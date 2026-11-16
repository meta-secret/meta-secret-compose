package core.metaSecretCore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.UserStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeReadCall
import core.metaSecretCore.liveboard.NativeReadScope
import core.metaSecretCore.liveboard.NativeRefreshLane
import core.metaSecretCore.liveboard.NativeRefreshRequest
import core.metaSecretCore.liveboard.NativeSocketPorts
import core.metaSecretCore.liveboard.NativeSocketPresentation
import core.metaSecretCore.liveboard.NativeSocketRuntime

/** Original Socket fields/flows, shared by direct delegates; construction has no effects. */
internal class NativeSocketObservationState(val readOwner: NativeCanonicalReadOwner) {
    val actionType = kotlinx.coroutines.flow.MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    val actionEvents = kotlinx.coroutines.flow.MutableSharedFlow<SocketActionModel>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val pendingRecoveryRequests = kotlinx.coroutines.flow.MutableStateFlow<List<RestoreData>>(emptyList())
    var actionsToFollow = mutableSetOf<SocketRequestModel>()
    var processingSecretName: String? = null
    var lastEmittedReadyToRecoverClaimId: String? = null
    var lastObservedSuccessfulState: AppStateModel? = null
    var joinResultRefreshInFlight = false
    var joinResultAuthorizationErrorShown = false
    var configuredJoinResultVault: String? = null
}

/** Original refresh consumption/subscriptions; the facade passes its SAME lane per call. */
internal class NativeSocketStateConsumer(
    ports: NativeSocketPorts,
    presentation: NativeSocketPresentation,
    private val runtime: NativeSocketRuntime,
    private val state: NativeSocketObservationState,
    private val claims: NativeSocketClaimObserver,
) {
    private val metaSecretCore = ports.core
    private val logger = ports.logger
    private val notificationCoordinator = ports.notification
    private val errorMapper = ports.errorMapper
    private val appStateCacheProvider = presentation.cache
    private val socketClient = runtime.client
    private val uiDispatcher = runtime.uiDispatcher
    private val readOwner = state.readOwner
    private fun mayConsumeActualRead(call: NativeReadCall, lane: NativeRefreshLane): Boolean =
        readOwner.mayConsumeActualRead(call) && lane.mayConsume(call)

    suspend fun searchRequest(request: NativeRefreshRequest, lane: NativeRefreshLane): Boolean {
        if (lane.isPaused || !request.isCurrent()) {
            logger.log(core.LogTag.SocketHandler.Message.RefreshSkippedWhilePaused, success = true)
            return false
        }

        if (state.actionsToFollow.isEmpty()) {
            logger.log(core.LogTag.SocketHandler.Message.NoSubscriptions, success = true)
        }

        val previousState = state.lastObservedSuccessfulState ?: appStateCacheProvider.appState.value
        val readCall = readOwner.readAttempt(request.expectedScope, request.turn)
        if (!readOwner.mayConsumeActualRead(readCall) || !request.recordActualRead(readCall)) return false
        val currentState = try {
            val parsedState = when (readCall) {
                is NativeReadCall.Value -> readCall.frame.state ?: return false
                is NativeReadCall.Failure -> throw readCall.cause
            }
            val vaultInfo = parsedState.getVaultFullInfo()
            logger.e2eDiagnostic(
                "recovery-appstate success=${parsedState.success} " +
                    "member=${vaultInfo is VaultFullInfo.Member}"
            )
            logger.diagnostic(
                "getAppState-result success=${parsedState.success} " +
                    "isMember=${vaultInfo is VaultFullInfo.Member} isOutsider=${vaultInfo is VaultFullInfo.Outsider} " +
                    "currentUserStatus=${parsedState.currentDeviceVaultStatus()} " +
                    "actionsToFollow=${state.actionsToFollow.joinToString()}"
            )
            if (parsedState.success) {
                state.lastObservedSuccessfulState = parsedState
            }
            logger.log(core.LogTag.SocketHandler.Message.AppStateRefreshCompleted, success = true)
            parsedState
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(uiDispatcher) {
                if (!mayConsumeActualRead(readCall, lane)) return@withContext
                appStateCacheProvider.updateCache(AppStateModel(success = false))
                logger.e2eDiagnostic("recovery-refresh-finished success=false reason=exception")
                logger.log(core.LogTag.SocketHandler.Message.ErrorGettingState, "${e.message}", success = false)
                val appError = errorMapper.mapExceptionToAppError(e)
                val userMessage = errorMapper.getUserFriendlyMessage(appError)
                if (mayConsumeActualRead(readCall, lane)) notificationCoordinator.showError(userMessage)
            }
            return false
        }

        if (!mayConsumeActualRead(readCall, lane)) return false
        if (!currentState.success) {
            logger.e2eDiagnostic("recovery-refresh-finished success=false")
            return false
        }

        logger.e2eDiagnostic("recovery-refresh-finished success=true")

        val readScope = readCall.frame.scope ?: return false
        configureSocketSubscriptionSafely(currentState, readScope, lane)
        if (!mayConsumeActualRead(readCall, lane)) return false
        configureJoinResultSubscriptionSafely(currentState, readScope, lane)
        if (!mayConsumeActualRead(readCall, lane)) return false

        if (isCurrentDeviceRemovalTransition(previousState, currentState)) {
            logger.log(core.LogTag.SocketHandler.Message.CurrentDeviceRemoved, success = true)
            withContext(uiDispatcher) {
                if (!mayConsumeActualRead(readCall, lane)) return@withContext
                state.actionEvents.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
            }
        }

        if (state.actionsToFollow.contains(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)) {
            val memberDeviceIds = currentState.getVaultSummary()
                ?.users
                ?.values
                ?.filter { it.status == UserStatus.MEMBER }
                ?.map { it.deviceId }
                ?.toSet()
                .orEmpty()
            val hasJoinRequests = currentState.getVaultEvents()
                ?.getJoinRequests()
                ?.any { request -> request.candidate.device.deviceId !in memberDeviceIds } == true

            logger.log(
                core.LogTag.SocketHandler.Message.JoinRequestState,
                "hasJoinRequests=$hasJoinRequests memberCount=${memberDeviceIds.size}",
                success = true
            )

            if (hasJoinRequests) {
                logger.log(core.LogTag.SocketHandler.Message.NeedShowAskToJoin, success = true)
                withContext(uiDispatcher) {
                    if (!mayConsumeActualRead(readCall, lane)) return@withContext
                    state.actionType.value = SocketActionModel.ASK_TO_JOIN
                }
            } else if (state.actionType.value == SocketActionModel.ASK_TO_JOIN) {
                logger.log(core.LogTag.SocketHandler.Message.JoinRequestCleared, success = true)
                withContext(uiDispatcher) {
                    if (!mayConsumeActualRead(readCall, lane)) return@withContext
                    state.actionType.value = SocketActionModel.NONE
                }
            }
        }

        if (state.actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForJoinResponse, success = true)

            withContext(uiDispatcher) {
                if (!mayConsumeActualRead(readCall, lane)) return@withContext
                when (currentState.getVaultFullInfo()) {
                    is VaultFullInfo.Member -> {
                        state.actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        stopJoinResultSubscription()
                        state.joinResultRefreshInFlight = false
                        state.joinResultAuthorizationErrorShown = false
                        state.actionType.value = SocketActionModel.NONE
                        state.actionEvents.tryEmit(SocketActionModel.JOIN_REQUEST_ACCEPTED)
                    }
                    is VaultFullInfo.NotExists -> {
                        state.actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        stopJoinResultSubscription()
                        state.joinResultRefreshInFlight = false
                        state.actionType.value = SocketActionModel.NONE
                    }
                    is VaultFullInfo.Outsider -> {
                        val outsiderStatus = currentState.getOutsiderStatus()
                        logger.log(
                            core.LogTag.SocketHandler.Message.WaitingForJoinResponse,
                            "outsiderStatus=$outsiderStatus",
                            success = true
                        )
                        when (outsiderStatus) {
                            UserDataOutsiderStatus.NON_MEMBER -> {
                                state.actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                state.joinResultRefreshInFlight = false
                                state.actionType.value = SocketActionModel.NONE
                            }
                            UserDataOutsiderStatus.PENDING -> {
                                state.joinResultRefreshInFlight = false
                                state.actionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                            }
                            UserDataOutsiderStatus.DECLINED -> {
                                state.actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                state.joinResultRefreshInFlight = false
                                state.joinResultAuthorizationErrorShown = false
                                state.actionType.value = SocketActionModel.NONE
                                state.actionEvents.tryEmit(SocketActionModel.JOIN_REQUEST_DECLINED)
                            }
                            null -> {
                                state.actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                state.joinResultRefreshInFlight = false
                                state.actionType.value = SocketActionModel.NONE
                            }
                        }
                    }
                    null -> state.actionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                }
            }
        }

        if (state.actionsToFollow.contains(SocketRequestModel.GET_STATE)) {
            logger.log(core.LogTag.SocketHandler.Message.SocketEmmitOnStateResponse, success = true)
            withContext(uiDispatcher) {
                if (!mayConsumeActualRead(readCall, lane)) return@withContext
                state.actionEvents.tryEmit(SocketActionModel.UPDATE_STATE)
            }
        }

        claims.handleClaims(currentState, readCall, lane)
        return mayConsumeActualRead(readCall, lane)
    }

    private fun configureSocketSubscriptionSafely(currentState: AppStateModel, scope: NativeReadScope,
        lane: NativeRefreshLane) {
        try {
            configureSocketSubscription(currentState, scope, lane)
        } catch (e: Exception) {
            logger.log(
                core.LogTag.SocketHandler.Message.SocketError,
                "socket subscription failed: ${e.message}",
                success = false
            )
        }
    }

    private fun configureJoinResultSubscriptionSafely(currentState: AppStateModel, scope: NativeReadScope,
        lane: NativeRefreshLane) {
        val waiting = state.actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
        val pending = currentState.getVaultFullInfo() is VaultFullInfo.Outsider &&
            currentState.getOutsiderStatus() == UserDataOutsiderStatus.PENDING
        if (!waiting || !pending) {
            socketClient.configureJoinResult(null)
            return
        }

        val vaultName = currentState.getCurrentVaultName()
        if (vaultName.isNullOrBlank()) {
            logger.log(
                core.LogTag.SocketHandler.Message.SocketError,
                "pending JOIN has no Vault name for result subscription",
                success = false,
            )
            return
        }

        if (state.configuredJoinResultVault == vaultName) {
            return
        }

        socketClient.configureJoinResult(
            MetaSecretJoinResultSubscription(
                vaultName = vaultName,
                authorizationTokenProvider = { runtime.authorization(scope) { metaSecretCore.joinResultAuthToken() } },
            ),
        )
        state.configuredJoinResultVault = vaultName
        if (lane.isForeground) socketClient.connectJoinResult()
    }

    fun stopJoinResultSubscription() {
        socketClient.configureJoinResult(null)
        socketClient.disconnectJoinResult()
        state.configuredJoinResultVault = null
    }

    private fun configureSocketSubscription(currentState: AppStateModel, scope: NativeReadScope,
        lane: NativeRefreshLane) {
        if (currentState.getVaultFullInfo() !is VaultFullInfo.Member) {
            socketClient.configure(null)
            return
        }
        val deviceId = currentState.getCurrentDeviceId()
        val vaultName = currentState.getCurrentVaultName()
        if (deviceId == null || vaultName == null) {
            socketClient.configure(null)
            return
        }
        socketClient.configure(
            MetaSecretSocketSubscription(
                vaultName = vaultName,
                deviceId = deviceId,
                authorizationTokenProvider = { runtime.authorization(scope) { metaSecretCore.stateEventsAuthToken(
                    vaultName) } },
            )
        )
        if (lane.isForeground) {
            socketClient.connect()
        }
    }

}

internal fun isCurrentDeviceRemovalTransition(
    previousState: AppStateModel?,
    currentState: AppStateModel,
): Boolean {
    if (!currentState.success) return false
    if (previousState?.getVaultFullInfo() !is VaultFullInfo.Member) return false

    val previousDeviceId = previousState.getCurrentDeviceId() ?: return false
    val localDeviceId = (currentState.getCurrentAppState() as? State.Local)
        ?.local
        ?.deviceId
    if (localDeviceId != null) {
        return previousDeviceId == localDeviceId
    }

    val isDeclined = currentState.getOutsiderStatus() == UserDataOutsiderStatus.DECLINED ||
        currentState.currentDeviceVaultStatus() == UserStatus.DECLINED
    if (!isDeclined) return false

    val currentDeviceId = currentState.getCurrentDeviceId() ?: return false
    val previousVaultName = previousState.getCurrentVaultName() ?: return false
    val currentVaultName = currentState.getCurrentVaultName() ?: return false

    return previousDeviceId == currentDeviceId && previousVaultName == currentVaultName
}

private fun AppStateModel.currentDeviceVaultStatus(): UserStatus? {
    val currentDeviceId = getCurrentDeviceId() ?: return null
    return getVaultSummary()?.users?.get(currentDeviceId)?.status
}
