package core.metaSecretCore

import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.ClaimStatus
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
    private val stringProvider: StringProviderInterface
): MetaSecretSocketHandlerInterface {
    private val _socketActionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    override val socketActionType: StateFlow<SocketActionModel> = _socketActionType

    private val _socketActions = MutableSharedFlow<SocketActionModel>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val socketActions: SharedFlow<SocketActionModel> = _socketActions

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()
    private var timerJob: Job? = null
    private val timerScope = CoroutineScope(Dispatchers.IO)
    private var isPaused = false
    private var processingSecretName: String? = null

    init {
        logger.log(core.LogTag.SocketHandler.Message.Init, success = true)
        startFollowing()
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
        }
        logger.log(core.LogTag.SocketHandler.Message.ActualActionsToFollow, "$actionsToFollow", success = true)
    }

    override fun setProcessingSecretName(secretName: String) {
        processingSecretName = secretName
    }

    private fun startFollowing() {
        stopTimer()
        timerJob = timerScope.launch {
            while (isActive) {
                searchRequest()
                delay(5000)
            }
        }
    }

    private suspend fun searchRequest() {
        if (isPaused) {
            logger.log(core.LogTag.SocketHandler.Message.PollingSkippedWhilePaused, success = true)
            return
        }

        if (actionsToFollow.isEmpty()) {
            logger.log(core.LogTag.SocketHandler.Message.NoSubscriptions, success = true)
            return
        }

        logger.log(core.LogTag.SocketHandler.Message.FireTimer, success = true)
        
        val currentState = try {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState() // MAIN POINT OF GETTING STATE, WHILE WE USE TIMER
            }
            val parsedState = AppStateModel.fromJson(stateJson, logger, null)
            appStateCacheProvider.updateCache(parsedState)
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
                    is VaultFullInfo.Member -> _socketActionType.value = SocketActionModel.JOIN_REQUEST_ACCEPTED
                    is VaultFullInfo.NotExists -> _socketActionType.value = SocketActionModel.NONE
                    is VaultFullInfo.Outsider -> {
                        when (currentState.getOutsiderStatus()) {
                            UserDataOutsiderStatus.NON_MEMBER -> { _socketActionType.value = SocketActionModel.NONE }
                            UserDataOutsiderStatus.PENDING -> { _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING }
                            UserDataOutsiderStatus.DECLINED -> { _socketActionType.value = SocketActionModel.JOIN_REQUEST_DECLINED }
                            null -> _socketActionType.value = SocketActionModel.NONE
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
            if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_RECOVER_REQUEST)) {
                checkRecoverRequest(claims, currentDeviceId)
            }
            if (actionsToFollow.contains(SocketRequestModel.SHOW_SECRET)) {
                checkRecoverSentStatus()
            }
        }
    }

    private fun checkRecoverRequest(claims: Map<String, ClaimObject>, currentDeviceId: String) {
        val firstPendingClaim = claims.values.firstOrNull { claim ->
            val isRecoverType = claim.distributionType == DistributionType.RECOVER
            val isReceiverForThisDevice = claim.receivers.contains(currentDeviceId)
            val receiverStatus = claim.status.statuses[currentDeviceId]
            val isPending = receiverStatus == ClaimStatus.PENDING
            isRecoverType && isReceiverForThisDevice && isPending
        }

        if (firstPendingClaim != null) {
            val restoreData = RestoreData(
                firstPendingClaim.distClaimId.id,
                firstPendingClaim.distClaimId.passId.name
            )
            logger.log(core.LogTag.SocketHandler.Message.ReadyToRecover, "$restoreData", success = true)
            _socketActionType.value = SocketActionModel.READY_TO_RECOVER(restoreData = restoreData)
        } else {
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
            when (existingClaim.claim?.status) {
                ClaimStatus.SENT ->  {
                    val claimId = existingClaim.claim?.claimId ?: return
                    if (existingClaim.claim?.claimId != null) {
                        _socketActionType.value = SocketActionModel.RECOVER_SENT(claimId, secretName)
                        processingSecretName = null
                    }
                }
                else -> {
                    println("#6")
                }
            }
        } catch (t: Throwable) {
            logger.log(LogTag.ShowSecretVM.Message.PresentingFailed, "${t.message}", success = false)
        }
    }

    private fun stopTimer() {
        logger.log(LogTag.SocketHandler.Message.TimerStopped, success = true)
        timerJob?.cancel()
        timerJob = null
    }

    override fun pausePolling() {
        logger.log(LogTag.SocketHandler.Message.PollingPaused, success = true)
        isPaused = true
    }

    override fun resumePolling() {
        logger.log(LogTag.SocketHandler.Message.PollingResumed, success = true)
        isPaused = false
    }
}
