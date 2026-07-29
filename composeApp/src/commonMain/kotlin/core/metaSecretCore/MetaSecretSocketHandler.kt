package core.metaSecretCore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.ClientStatus
import models.apiModels.DistributionType
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import core.AppStateCacheProviderInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.StringProviderInterface
import core.errors.ErrorMapper
import models.apiModels.ClaimObject
import models.apiModels.SearchClaimModel
import models.apiModels.SsClaims

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val errorMapper: ErrorMapper,
    private val appStateCacheProvider: AppStateCacheProviderInterface,
    private val stringProvider: StringProviderInterface,
    private val socketClient: MetaSecretSocketClient = NoopMetaSecretSocketClient()
): MetaSecretSocketHandlerInterface {
    private val _socketActionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    override val socketActionType: StateFlow<SocketActionModel> = _socketActionType

    private val _socketActions = MutableSharedFlow<SocketActionModel>(
        replay = 0,
        extraBufferCapacity = 16
    )
    override val socketActions: SharedFlow<SocketActionModel> = _socketActions

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()
    private val socketScope = CoroutineScope(Dispatchers.IO)
    private var refreshJob: Job? = null
    private var pendingRefresh = false
    private var isPaused = false
    private var isForeground = false
    private var processingSecretName: String? = null
    private var lastEmittedReadyToRecoverClaimId: String? = null
    private val socketErrorRefreshDelayMs = 1_000L

    init {
        logger.log(core.LogTag.SocketHandler.Message.Init, success = true)
        socketScope.launch {
            socketClient.events.collect { event ->
                when (event) {
                    is MetaSecretSocketEvent.Connected -> refreshFromSocketReconnect()
                    is MetaSecretSocketEvent.StateInvalidated -> refreshFromInvalidation(event.claimId)
                    is MetaSecretSocketEvent.Disconnected -> Unit
                    is MetaSecretSocketEvent.Error -> {
                        logger.log(
                            core.LogTag.SocketHandler.Message.SocketError,
                            event.message,
                            success = false
                        )
                        refreshFromSocketError()
                    }
                }
            }
        }
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        exclude?.let { toExclude ->
            actionsToFollow.removeAll(toExclude.toSet())
        }
        
        add?.let { toAdd ->
            actionsToFollow.addAll(toAdd)
            val needsImmediateSync = toAdd.any {
                it == SocketRequestModel.SHOW_SECRET ||
                    it == SocketRequestModel.WAIT_FOR_JOIN_APPROVE
            }
            if (needsImmediateSync) {
                refreshAppState()
            }
        }
        logger.log(core.LogTag.SocketHandler.Message.ActualActionsToFollow, "$actionsToFollow", success = true)
    }

    override fun setProcessingSecretName(secretName: String) {
        processingSecretName = secretName
    }

    override fun resetReadyToRecoverDedup(claimId: String?) {
        if (claimId == null || lastEmittedReadyToRecoverClaimId == claimId) {
            lastEmittedReadyToRecoverClaimId = null
        }
    }

    override fun resetSocketActionType(expected: SocketActionModel?) {
        if (expected == null || _socketActionType.value == expected) {
            _socketActionType.value = SocketActionModel.NONE
        }
    }

    override fun onAppLaunch() {
        logger.log(core.LogTag.SocketHandler.Message.AppLaunchRefresh, success = true)
        isForeground = true
        refreshAppState()
    }

    override fun onAppForeground() {
        logger.log(core.LogTag.SocketHandler.Message.ForegroundRefresh, success = true)
        isForeground = true
        refreshAppState()
    }

    override fun onAppBackground() {
        logger.log(core.LogTag.SocketHandler.Message.BackgroundSocketSuspend, success = true)
        isForeground = false
        refreshJob?.cancel()
        refreshJob = null
        pendingRefresh = false
        socketClient.disconnect()
    }

    override fun refreshAppState() {
        scheduleRefresh()
    }

    private fun refreshFromSocketReconnect() {
        logger.log(core.LogTag.SocketHandler.Message.SocketReconnectRefresh, success = true)
        refreshAppState()
    }

    private fun refreshFromInvalidation(claimId: String?) {
        logger.log(core.LogTag.SocketHandler.Message.StateInvalidated, "claimId=$claimId", success = true)
        scheduleRefresh(delayMs = 150)
    }

    private fun refreshFromSocketError() {
        if (!isForeground) return
        scheduleRefresh(delayMs = socketErrorRefreshDelayMs)
    }

    private fun scheduleRefresh(delayMs: Long = 0) {
        if (refreshJob?.isActive == true) {
            pendingRefresh = true
            return
        }

        refreshJob = socketScope.launch {
            var nextDelayMs = delayMs
            do {
                pendingRefresh = false
                if (nextDelayMs > 0) {
                    delay(nextDelayMs)
                    nextDelayMs = 0
                }
                searchRequest()
            } while (pendingRefresh)
            refreshJob = null
        }
    }

    private suspend fun searchRequest() {
        if (isPaused) {
            logger.log(core.LogTag.SocketHandler.Message.RefreshSkippedWhilePaused, success = true)
            return
        }

        if (actionsToFollow.isEmpty()) {
            logger.log(core.LogTag.SocketHandler.Message.NoSubscriptions, success = true)
        }

        val currentState = try {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            val parsedState = AppStateModel.fromJson(stateJson, logger, null)
            appStateCacheProvider.updateCache(parsedState)
            configureSocketSubscription(parsedState)
            logger.log(core.LogTag.SocketHandler.Message.AppStateRefreshCompleted, success = true)
            parsedState
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(core.LogTag.SocketHandler.Message.ErrorGettingState, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            return
        }

        if (!currentState.success) {
            return
        }

        if (actionsToFollow.contains(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)) {
            val hasJoinRequests = currentState.getVaultEvents()?.hasJoinRequests() == true

            if (hasJoinRequests) {
                logger.log(core.LogTag.SocketHandler.Message.NeedShowAskToJoin, success = true)
                withContext(Dispatchers.Main) {
                    _socketActionType.value = SocketActionModel.ASK_TO_JOIN
                }
            }
        }

        if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForJoinResponse, success = true)

            withContext(Dispatchers.Main) {
                when (currentState.getVaultFullInfo()) {
                    is VaultFullInfo.Member -> {
                        actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        _socketActionType.value = SocketActionModel.NONE
                        _socketActions.tryEmit(SocketActionModel.JOIN_REQUEST_ACCEPTED)
                    }
                    is VaultFullInfo.NotExists -> {
                        actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        _socketActionType.value = SocketActionModel.NONE
                    }
                    is VaultFullInfo.Outsider -> {
                        when (currentState.getOutsiderStatus()) {
                            UserDataOutsiderStatus.NON_MEMBER -> {
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                _socketActionType.value = SocketActionModel.NONE
                            }
                            UserDataOutsiderStatus.PENDING -> { _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING }
                            UserDataOutsiderStatus.DECLINED -> {
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                _socketActionType.value = SocketActionModel.NONE
                                _socketActions.tryEmit(SocketActionModel.JOIN_REQUEST_DECLINED)
                            }
                            null -> {
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                _socketActionType.value = SocketActionModel.NONE
                            }
                        }
                    }
                    null -> _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                }
            }
        }

        if (actionsToFollow.contains(SocketRequestModel.GET_STATE)) {
            logger.log(core.LogTag.SocketHandler.Message.SocketEmmitOnStateResponse, success = true)
            withContext(Dispatchers.Main) {
                _socketActions.tryEmit(SocketActionModel.UPDATE_STATE)
            }
        }

        handleClaims(currentState)
    }

    private fun configureSocketSubscription(currentState: AppStateModel) {
        val deviceId = currentState.getCurrentDeviceId()
        val vaultName = currentState.getCurrentVaultName()
        if (deviceId == null || vaultName == null) {
            socketClient.configure(null)
            return
        }
        socketClient.configure(MetaSecretSocketSubscription(vaultName = vaultName, deviceId = deviceId))
        if (isForeground) {
            socketClient.connect()
        }
    }

    private suspend fun handleClaims(currentState: AppStateModel) {
        val currentDeviceId = currentState.getCurrentDeviceId() ?: return
        val vaultFullInfo = currentState.getVaultFullInfo()

        if (vaultFullInfo is VaultFullInfo.Member) {
            val claims = vaultFullInfo.member.ssClaims?.claims
            if (claims.isNullOrEmpty()) {
                logger.log(core.LogTag.SocketHandler.Message.NoClaimsFound, success = true)
                return
            }

            logger.log(core.LogTag.SocketHandler.Message.FoundClaims, "${claims.size}", success = true)
            checkRecoverRequest(claims, currentDeviceId)
            if (actionsToFollow.contains(SocketRequestModel.SHOW_SECRET)) {
                checkRecoverSentStatus()
            }
        }
    }

    private fun checkRecoverRequest(claims: Map<String, ClaimObject>, currentDeviceId: String) {
        val recoverClaimsForDevice = claims.values.filter {
            it.distributionType == DistributionType.RECOVER && it.receivers.contains(currentDeviceId)
        }
        if (recoverClaimsForDevice.isNotEmpty()) {
            logger.log(
                core.LogTag.SocketHandler.Message.ReceiverClaimStatuses,
                recoverClaimsForDevice.joinToString { "${it.distClaimId.id}=${it.clientStatus}" },
                success = true
            )
        }
        val hasDoneClaim = claims.values.any { claim ->
            claim.distributionType == DistributionType.RECOVER &&
                claim.receivers.contains(currentDeviceId) &&
                claim.clientStatus == ClientStatus.DONE
        }
        if (hasDoneClaim) {
            lastEmittedReadyToRecoverClaimId = null
            logger.log(core.LogTag.SocketHandler.Message.DismissRecoveryRequest, success = true)
            _socketActions.tryEmit(SocketActionModel.DISMISS_RECOVERY_REQUEST)
        }
        val firstNeedApproveClaim = claims.values.firstOrNull { claim ->
            val isRecoverType = claim.distributionType == DistributionType.RECOVER
            val isReceiverForThisDevice = claim.receivers.contains(currentDeviceId)
            val needsApproval = claim.clientStatus == ClientStatus.NEED_APPROVE
            isRecoverType && isReceiverForThisDevice && needsApproval
        }
        if (firstNeedApproveClaim != null) {
            val claimId = firstNeedApproveClaim.distClaimId.id
            if (claimId != lastEmittedReadyToRecoverClaimId) {
                lastEmittedReadyToRecoverClaimId = claimId
                val restoreData = RestoreData(claimId, firstNeedApproveClaim.distClaimId.passId.name)
                logger.log(core.LogTag.SocketHandler.Message.ReadyToRecover, "claimId=$claimId secretId=${restoreData.secretId}", success = true)
                _socketActionType.value = SocketActionModel.READY_TO_RECOVER(restoreData = restoreData)
            }
        } else {
            lastEmittedReadyToRecoverClaimId = null
            logger.log(core.LogTag.SocketHandler.Message.NothingToRecover, success = true)
        }
    }

    private suspend fun checkRecoverSentStatus() {
        try {
            val secretName = processingSecretName ?: return
            val searchResult = withContext(Dispatchers.IO) {
                metaSecretCore.findClaim(secretName)
            }
            val existingClaim = SearchClaimModel.fromJson(searchResult)
            val claim = existingClaim.claim
            val clientStatusStr = claim?.clientStatus?.name ?: "null"
            val statusesStr = existingClaim.message?.claim?.status?.statuses?.entries
                ?.joinToString { "${it.key}=${it.value.name}" } ?: "{}"
            logger.log(
                core.LogTag.SocketHandler.Message.RecoverSentStatusClaimStatus,
                "clientStatus=$clientStatusStr statuses=$statusesStr",
                success = true
            )
            when (claim?.clientStatus) {
                ClientStatus.ACCEPTED -> {
                    val claimId = claim.claimId ?: return
                    logger.log(
                        core.LogTag.SocketHandler.Message.RecoverSentForSecretId,
                        "secretId=$secretName claimId=$claimId",
                        success = true
                    )
                    _socketActionType.value = SocketActionModel.NONE
                    _socketActionType.value = SocketActionModel.RECOVER_ACCEPTED(claimId, secretName)
                    processingSecretName = null
                }
                ClientStatus.DECLINED -> {
                    logger.log(
                        core.LogTag.SocketHandler.Message.RecoverDeclinedForSecretId,
                        "secretId=$secretName",
                        success = true
                    )
                    _socketActionType.value = SocketActionModel.NONE
                    _socketActionType.value = SocketActionModel.RECOVER_DECLINED(secretName)
                    processingSecretName = null
                }
                else -> { }
            }
        } catch (t: Throwable) {
            logger.log(LogTag.ShowSecretVM.Message.PresentingFailed, "${t.message}", success = false)
        }
    }

    override fun pauseRefreshes() {
        logger.log(LogTag.SocketHandler.Message.RefreshesPaused, success = true)
        isPaused = true
    }

    override fun resumeRefreshes() {
        logger.log(LogTag.SocketHandler.Message.RefreshesResumed, success = true)
        isPaused = false
    }
}
