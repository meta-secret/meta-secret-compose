package core.metaSecretCore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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
import models.apiModels.UserStatus
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
import models.apiModels.State

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val errorMapper: ErrorMapper,
    private val appStateCacheProvider: AppStateCacheProviderInterface,
    private val stringProvider: StringProviderInterface,
    private val socketClient: MetaSecretSocketClient = NoopMetaSecretSocketClient(),
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : MetaSecretSocketHandlerInterface {
    private val _socketActionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    override val socketActionType: StateFlow<SocketActionModel> = _socketActionType

    private val _socketActions = MutableSharedFlow<SocketActionModel>(
        replay = 0,
        extraBufferCapacity = 16
    )
    override val socketActions: SharedFlow<SocketActionModel> = _socketActions

    private val _pendingRecoveryRequests = MutableStateFlow<List<RestoreData>>(emptyList())
    override val pendingRecoveryRequests: StateFlow<List<RestoreData>> = _pendingRecoveryRequests

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()
    private val socketScope = CoroutineScope(Dispatchers.IO)
    private var refreshJob: Job? = null
    private var pendingRefresh = false
    private var isPaused = false
    private var isForeground = false
    private var processingSecretName: String? = null
    private var lastEmittedReadyToRecoverClaimId: String? = null
    private var lastObservedSuccessfulState: AppStateModel? = null
    private var joinResultRefreshInFlight = false
    private var joinResultAuthorizationErrorShown = false
    private var configuredJoinResultVault: String? = null

    init {
        logger.log(core.LogTag.SocketHandler.Message.Init, success = true)
        socketScope.launch {
            socketClient.events.collect { event ->
                when (event) {
                    is MetaSecretSocketEvent.Connected -> {
                        logger.e2eDiagnostic("recovery-socket connected")
                        refreshFromSocketReconnect()
                    }
                    is MetaSecretSocketEvent.StateInvalidated -> refreshFromInvalidation(event)
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
        socketScope.launch {
            socketClient.joinResultEvents.collect { event ->
                when (event) {
                    is MetaSecretJoinResultEvent.Resolved -> {
                        if (!actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE) || joinResultRefreshInFlight) {
                            return@collect
                        }
                        joinResultRefreshInFlight = true
                        logger.log(
                            core.LogTag.SocketHandler.Message.WaitingForJoinResponse,
                            "result=${event.outcome}",
                            success = true,
                        )
                        // SSE carries only the outcome. Fetch one canonical state from Core.
                        refreshAppState()
                    }
                    is MetaSecretJoinResultEvent.Error -> {
                        logger.log(
                            core.LogTag.SocketHandler.Message.SocketError,
                            event.message,
                            success = false,
                        )
                        if (event.terminal && !joinResultAuthorizationErrorShown) {
                            joinResultAuthorizationErrorShown = true
                            notificationCoordinator.showError(
                                "This pending join request cannot use live approval updates. Ask a vault member to resolve it, then create a new join request."
                            )
                        }
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
            if (SocketRequestModel.WAIT_FOR_JOIN_APPROVE in toExclude) {
                stopJoinResultSubscription()
            }
        }
        
        add?.let { toAdd ->
            if (SocketRequestModel.WAIT_FOR_JOIN_APPROVE in toAdd &&
                SocketRequestModel.WAIT_FOR_JOIN_APPROVE !in actionsToFollow
            ) {
                joinResultRefreshInFlight = false
                joinResultAuthorizationErrorShown = false
                configuredJoinResultVault = null
            }
            actionsToFollow.addAll(toAdd)
            // A fresh vault is created after the app's first launch refresh. At that
            // time there is no vault name yet, so no SSE subscription can be opened.
            // Starting to follow vault state or join requests must therefore refresh
            // immediately and subscribe to the newly created vault.
            val needsImmediateSync = toAdd.any {
                it == SocketRequestModel.SHOW_SECRET ||
                    it == SocketRequestModel.WAIT_FOR_JOIN_APPROVE ||
                    it == SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN ||
                    it == SocketRequestModel.GET_STATE
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
        socketScope.launch(uiDispatcher) {
            if (claimId == null || lastEmittedReadyToRecoverClaimId == claimId) {
                lastEmittedReadyToRecoverClaimId = null
            }
        }
    }

    override fun resetSocketActionType(expected: SocketActionModel?) {
        socketScope.launch(uiDispatcher) {
            if (expected == null || _socketActionType.value == expected) {
                _socketActionType.value = SocketActionModel.NONE
            }
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
        // A terminal authorization failure ends this SSE stream. Reconfigure on
        // foreground so a newly minted short-lived token can resume the request.
        configuredJoinResultVault = null
        refreshAppState()
    }

    override fun onAppBackground() {
        logger.log(core.LogTag.SocketHandler.Message.BackgroundSocketSuspend, success = true)
        isForeground = false
        refreshJob?.cancel()
        refreshJob = null
        pendingRefresh = false
        socketClient.disconnect()
        socketClient.disconnectJoinResult()
        configuredJoinResultVault = null
    }

    override fun refreshAppState() {
        scheduleRefresh()
    }

    private fun refreshFromSocketReconnect() {
        logger.log(core.LogTag.SocketHandler.Message.SocketReconnectRefresh, success = true)
        refreshAppState()
    }

    private fun refreshFromInvalidation(event: MetaSecretSocketEvent.StateInvalidated) {
        logger.e2eDiagnostic(
            "recovery-invalidation received scope=${event.scope ?: "unknown"} " +
                "claimId=${event.claimId ?: "none"} outcome=${event.decisionOutcome ?: "none"}"
        )
        logger.log(
            core.LogTag.SocketHandler.Message.StateInvalidated,
            "scope=${event.scope} claimId=${event.claimId}",
            success = true,
        )
        logger.e2eDiagnostic(
            "recovery-refresh-start source=invalidation scope=${event.scope ?: "unknown"} " +
                "claimId=${event.claimId ?: "none"}"
        )
        // Do not cancel an in-flight sync: cancellation can drop the refresh entirely.
        // scheduleRefresh marks a follow-up refresh when one is already running.
        scheduleRefresh()
    }

    private fun refreshFromSocketError() {
        if (!isForeground) return
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        if (refreshJob?.isActive == true) {
            pendingRefresh = true
            return
        }

        refreshJob = socketScope.launch {
            do {
                pendingRefresh = false
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

        val previousState = lastObservedSuccessfulState ?: appStateCacheProvider.appState.value
        val currentState = try {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            val parsedState = AppStateModel.fromJson(stateJson, logger, null)
            val vaultInfo = parsedState.getVaultFullInfo()
            logger.e2eDiagnostic(
                "recovery-appstate success=${parsedState.success} " +
                    "member=${vaultInfo is VaultFullInfo.Member}"
            )
            logger.diagnostic(
                "getAppState-result success=${parsedState.success} " +
                    "isMember=${vaultInfo is VaultFullInfo.Member} isOutsider=${vaultInfo is VaultFullInfo.Outsider} " +
                    "currentUserStatus=${parsedState.currentDeviceVaultStatus()} " +
                    "actionsToFollow=${actionsToFollow.joinToString()}"
            )
            appStateCacheProvider.updateCache(parsedState)
            if (parsedState.success) {
                lastObservedSuccessfulState = parsedState
            }
            logger.log(core.LogTag.SocketHandler.Message.AppStateRefreshCompleted, success = true)
            parsedState
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e2eDiagnostic("recovery-refresh-finished success=false reason=exception")
            logger.log(core.LogTag.SocketHandler.Message.ErrorGettingState, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            return
        }

        if (!currentState.success) {
            logger.e2eDiagnostic("recovery-refresh-finished success=false")
            return
        }

        logger.e2eDiagnostic("recovery-refresh-finished success=true")

        configureSocketSubscriptionSafely(currentState)
        configureJoinResultSubscriptionSafely(currentState)

        if (isCurrentDeviceRemovalTransition(previousState, currentState)) {
            logger.log(core.LogTag.SocketHandler.Message.CurrentDeviceRemoved, success = true)
            withContext(uiDispatcher) {
                _socketActions.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
            }
        }

        if (actionsToFollow.contains(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)) {
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
                    _socketActionType.value = SocketActionModel.ASK_TO_JOIN
                }
            } else if (_socketActionType.value == SocketActionModel.ASK_TO_JOIN) {
                logger.log(core.LogTag.SocketHandler.Message.JoinRequestCleared, success = true)
                withContext(uiDispatcher) {
                    _socketActionType.value = SocketActionModel.NONE
                }
            }
        }

        if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForJoinResponse, success = true)

            withContext(uiDispatcher) {
                when (currentState.getVaultFullInfo()) {
                    is VaultFullInfo.Member -> {
                        actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        stopJoinResultSubscription()
                        joinResultRefreshInFlight = false
                        joinResultAuthorizationErrorShown = false
                        _socketActionType.value = SocketActionModel.NONE
                        _socketActions.tryEmit(SocketActionModel.JOIN_REQUEST_ACCEPTED)
                    }
                    is VaultFullInfo.NotExists -> {
                        actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                        stopJoinResultSubscription()
                        joinResultRefreshInFlight = false
                        _socketActionType.value = SocketActionModel.NONE
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
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                joinResultRefreshInFlight = false
                                _socketActionType.value = SocketActionModel.NONE
                            }
                            UserDataOutsiderStatus.PENDING -> {
                                joinResultRefreshInFlight = false
                                _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                            }
                            UserDataOutsiderStatus.DECLINED -> {
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                joinResultRefreshInFlight = false
                                joinResultAuthorizationErrorShown = false
                                _socketActionType.value = SocketActionModel.NONE
                                _socketActions.tryEmit(SocketActionModel.JOIN_REQUEST_DECLINED)
                            }
                            null -> {
                                actionsToFollow.remove(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                                stopJoinResultSubscription()
                                joinResultRefreshInFlight = false
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
            withContext(uiDispatcher) {
                _socketActions.tryEmit(SocketActionModel.UPDATE_STATE)
            }
        }

        handleClaims(currentState)
    }

    private fun configureSocketSubscriptionSafely(currentState: AppStateModel) {
        try {
            configureSocketSubscription(currentState)
        } catch (e: Exception) {
            logger.log(
                core.LogTag.SocketHandler.Message.SocketError,
                "socket subscription failed: ${e.message}",
                success = false
            )
        }
    }

    private fun configureJoinResultSubscriptionSafely(currentState: AppStateModel) {
        val waiting = actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
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

        if (configuredJoinResultVault == vaultName) {
            return
        }

        socketClient.configureJoinResult(
            MetaSecretJoinResultSubscription(
                vaultName = vaultName,
                authorizationTokenProvider = { metaSecretCore.joinResultAuthToken() },
            ),
        )
        configuredJoinResultVault = vaultName
        if (isForeground) socketClient.connectJoinResult()
    }

    private fun stopJoinResultSubscription() {
        socketClient.configureJoinResult(null)
        socketClient.disconnectJoinResult()
        configuredJoinResultVault = null
    }

    private fun configureSocketSubscription(currentState: AppStateModel) {
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
                authorizationTokenProvider = { metaSecretCore.stateEventsAuthToken(vaultName) },
            )
        )
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
                logger.e2eDiagnostic("recovery-state membership=member recoverClaims=0 targeted=0 pendingApproval=0 done=0")
                logger.diagnostic(
                    "claims-refresh-empty device=$currentDeviceId " +
                        "vaultInfo=Member ssClaims=${vaultFullInfo.member.ssClaims != null} " +
                        "claimsCount=${claims?.size ?: 0}"
                )
                withContext(uiDispatcher) {
                    _pendingRecoveryRequests.value = emptyList()
                }
                logger.log(core.LogTag.SocketHandler.Message.NoClaimsFound, success = true)
                return
            }

            logger.log(core.LogTag.SocketHandler.Message.FoundClaims, "${claims.size}", success = true)
            checkRecoverRequest(claims, currentDeviceId, vaultFullInfo.member.member.vault.users)
            if (actionsToFollow.contains(SocketRequestModel.SHOW_SECRET)) {
                checkRecoverSentStatus()
            }
        } else {
            logger.e2eDiagnostic("recovery-state membership=non-member recoverClaims=0 targeted=0 pendingApproval=0 done=0")
            logger.diagnostic(
                "claims-refresh-no-member device=$currentDeviceId " +
                    "isOutsider=${vaultFullInfo is VaultFullInfo.Outsider} isNotExists=${vaultFullInfo is VaultFullInfo.NotExists}"
            )
            withContext(uiDispatcher) {
                _pendingRecoveryRequests.value = emptyList()
            }
        }
    }

    private suspend fun checkRecoverRequest(
        claims: Map<String, ClaimObject>,
        currentDeviceId: String,
        users: Map<String, models.apiModels.UserMembership>,
    ) {
        val recoverClaimsInVault = claims.values.filter {
            it.distributionType == DistributionType.RECOVER
        }
        val recoverClaimsForDevice = recoverClaimsInVault.filter {
            it.receivers.contains(currentDeviceId)
        }
        val doneClaims = recoverClaimsForDevice.filter { it.clientStatus == ClientStatus.DONE }
        val pendingClaims = recoverClaimsForDevice.filter { it.clientStatus == ClientStatus.NEED_APPROVE }
        logger.e2eDiagnostic(
            "recovery-claim-statuses " + recoverClaimsForDevice.joinToString(",") {
                "${it.distClaimId.id}:${it.clientStatus ?: "unknown"}"
            }.ifEmpty { "none" }
        )
        logger.e2eDiagnostic(
            "recovery-state membership=member recoverClaims=${recoverClaimsInVault.size} " +
                "targeted=${recoverClaimsForDevice.size} pendingApproval=${pendingClaims.size} done=${doneClaims.size}"
        )
        if (recoverClaimsForDevice.isNotEmpty()) {
            logger.e2eDiagnostic(
                "recovery claims for device=$currentDeviceId: " +
                    recoverClaimsForDevice.joinToString { claim ->
                        "claim=${claim.distClaimId.id},sender=${claim.sender},client=${claim.clientStatus}," +
                            "receivers=${claim.receivers.joinToString()}"
                    },
            )
        }
        if (recoverClaimsForDevice.isNotEmpty()) {
            logger.log(
                core.LogTag.SocketHandler.Message.ReceiverClaimStatuses,
                recoverClaimsForDevice.joinToString { "${it.distClaimId.id}=${it.clientStatus}" },
                success = true
            )
        }
        val pendingRecoveryRequests = pendingClaims
            .map {
                val rawSenderType = users[it.sender]?.member?.userData?.device?.deviceType
                    ?: users[it.sender]?.outsider?.userData?.device?.deviceType
                    ?: "Other"
                val senderType = when {
                    rawSenderType.equals("iphone", ignoreCase = true) ||
                        rawSenderType.equals("ios", ignoreCase = true) -> "iOS"
                    rawSenderType.equals("android", ignoreCase = true) -> "Android"
                    rawSenderType.equals("web", ignoreCase = true) -> "Web"
                    else -> rawSenderType
                }
                RestoreData(it.distClaimId.id, it.distClaimId.passId.name, it.sender, senderType)
            }
            .sortedBy { it.claimId }

        if (doneClaims.isNotEmpty()) {
            logger.diagnostic(
                "dismiss-signal-emitted reason=receiver-claim-DONE device=$currentDeviceId " +
                    "claims=" + doneClaims.joinToString { it.distClaimId.id }
            )
            logger.log(core.LogTag.SocketHandler.Message.DismissRecoveryRequest, success = true)
        }
        logger.diagnostic(
            "claims-refresh device=$currentDeviceId " +
                "recoverClaims=" + recoverClaimsForDevice.joinToString {
                    "${it.distClaimId.id}:${it.clientStatus}"
                } +
                " pending=" + pendingClaims.joinToString {
                    "${it.distClaimId.id}:${it.distClaimId.passId.name}"
                }
        )
        withContext(uiDispatcher) {
            if (doneClaims.isNotEmpty()) {
                lastEmittedReadyToRecoverClaimId = null
                doneClaims.forEach { doneClaim ->
                    _socketActions.tryEmit(
                        SocketActionModel.DISMISS_RECOVERY_REQUEST(doneClaim.distClaimId.id)
                    )
                }
            }
            _pendingRecoveryRequests.value = pendingRecoveryRequests
            logger.e2eDiagnostic(
                "recovery-pending-updated claimIds=" +
                    pendingRecoveryRequests.joinToString(",") { it.claimId }.ifEmpty { "none" }
            )

            val firstNeedApproveClaim = pendingClaims.firstOrNull()
            if (firstNeedApproveClaim != null) {
                val claimId = firstNeedApproveClaim.distClaimId.id
                if (claimId != lastEmittedReadyToRecoverClaimId) {
                    lastEmittedReadyToRecoverClaimId = claimId
                    val restoreData = RestoreData(
                        claimId,
                        firstNeedApproveClaim.distClaimId.passId.name,
                        firstNeedApproveClaim.sender,
                        pendingRecoveryRequests.firstOrNull { it.claimId == claimId }?.senderType ?: "Other",
                    )
                    logger.log(
                        core.LogTag.SocketHandler.Message.ReadyToRecover,
                        "claimId=$claimId secretId=${restoreData.secretId}",
                        success = true,
                    )
                    _socketActionType.value = SocketActionModel.READY_TO_RECOVER(restoreData)
                }
            } else {
                lastEmittedReadyToRecoverClaimId = null
                logger.log(core.LogTag.SocketHandler.Message.NothingToRecover, success = true)
            }
        }
        logger.log(
            core.LogTag.SocketHandler.Message.ReceiverClaimStatuses,
            "pending=${pendingRecoveryRequests.joinToString { it.claimId }}",
            success = true,
        )
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
            withContext(uiDispatcher) {
                when (claim?.clientStatus) {
                    ClientStatus.ACCEPTED -> {
                        val claimId = claim.claimId ?: return@withContext
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
            }
        } catch (e: CancellationException) {
            throw e
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
        // An invalidation may arrive while a secret action pauses refreshes. The
        // paused request is intentionally skipped, so retry once on resume while
        // the app is visible to avoid leaving pending requests stale.
        if (isForeground) {
            scheduleRefresh()
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
