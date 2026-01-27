package core.metaSecretCore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val appManager: MetaSecretAppManagerInterface,
    private val logger: core.DebugLoggerInterface,
    private val ffiSynchronizer: FfiSynchronizerInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val errorMapper: ErrorMapper
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
    private val restartMutex = kotlinx.coroutines.sync.Mutex()
    
    private val processedRecoverClaimIds = mutableSetOf<String>()

    init {
        logger.log(core.LogTag.SocketHandler.Message.Init, success = true)
        startFollowing()
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        logger.log(core.LogTag.SocketHandler.Message.UpdateActionsToFollow, success = true)

        exclude?.let { toExclude ->
            actionsToFollow.removeAll(toExclude.toSet())
        }
        
        add?.let { toAdd ->
            actionsToFollow.addAll(toAdd)
        }

        logger.log(core.LogTag.SocketHandler.Message.ActualActionsToFollow, "$actionsToFollow", success = true)
    }

    private fun startFollowing() {
        logger.log(core.LogTag.SocketHandler.Message.TimerStarted, success = true)
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
        
        if (!ffiSynchronizer.isAppManagerInitialized) {
            logger.log(core.LogTag.SocketHandler.Message.NoSubscriptions, "AppManager not initialized", success = true)
            return
        }

        logger.log(core.LogTag.SocketHandler.Message.FireTimer, success = true)
        
        val currentState = try {
            ffiSynchronizer.withFfiLock {
                val stateJson = withContext(Dispatchers.IO) {
                    metaSecretCore.getAppState()
                }
                AppStateModel.fromJson(stateJson, logger, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(core.LogTag.SocketHandler.Message.ErrorGettingState, "${e.message}", success = false)
            fun Throwable.isTimeout(): Boolean {
                val msg = message?.lowercase() ?: ""
                if (msg.contains("timed out") || msg.contains("timeout")) return true
                return cause?.isTimeout() == true
            }
            val isTimeout = e.isTimeout()
            if (isTimeout) {
                return
            }
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
                _socketActionType.value = SocketActionModel.ASK_TO_JOIN
            }
        }

        if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForJoinResponse, success = true)

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

        if (actionsToFollow.contains(SocketRequestModel.GET_STATE)) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForStateResponse, success = true)
            _socketActions.tryEmit(SocketActionModel.UPDATE_STATE)
        }

        coroutineScope {
            if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_RECOVER_REQUEST)) {
                logger.log(core.LogTag.SocketHandler.Message.WaitingForRecover, success = true)
                launch {
                    try {
                        checkRecoverRequest(currentState)
                    } catch (e: Exception) {
                        logger.log(core.LogTag.SocketHandler.Message.ErrorCheckingRecoverRequest, "${e.message}", success = false)
                        val appError = errorMapper.mapExceptionToAppError(e)
                        val userMessage = errorMapper.getUserFriendlyMessage(appError)
                        notificationCoordinator.showError(userMessage)
                    }
                }
            }

            if (actionsToFollow.contains(SocketRequestModel.SHOW_SECRET)) {
                logger.log(core.LogTag.SocketHandler.Message.WaitingForShowSecret, success = true)
                launch {
                    try {
                        checkRecoverSentStatus(currentState)
                    } catch (e: Exception) {
                        logger.log(core.LogTag.SocketHandler.Message.ErrorCheckingRecoverSentStatus, "${e.message}", success = false)
                        val appError = errorMapper.mapExceptionToAppError(e)
                        val userMessage = errorMapper.getUserFriendlyMessage(appError)
                        notificationCoordinator.showError(userMessage)
                    }
                }
                
                launch {
                    try {
                        checkRecoverDeclinedStatus(currentState)
                    } catch (e: Exception) {
                        logger.log(core.LogTag.SocketHandler.Message.ErrorCheckingRecoverDeclinedStatus, "${e.message}", success = false)
                        val appError = errorMapper.mapExceptionToAppError(e)
                        val userMessage = errorMapper.getUserFriendlyMessage(appError)
                        notificationCoordinator.showError(userMessage)
                    }
                }
            }
        }
    }

    private suspend fun checkRecoverRequest(currentState: AppStateModel) {
        withContext(Dispatchers.Default) {
            logger.log(core.LogTag.SocketHandler.Message.WaitingForRecoverRequest, success = true)
            
            val currentDeviceId = currentState.getCurrentDeviceId()
            val vaultFullInfo = currentState.getVaultFullInfo()
            
            if (currentDeviceId != null && vaultFullInfo is VaultFullInfo.Member) {
                val ssClaims = vaultFullInfo.member.ssClaims
                val claims = ssClaims?.claims
                
                if (claims != null && claims.isNotEmpty()) {
                    logger.log(core.LogTag.SocketHandler.Message.FoundClaims, "${claims.size}", success = true)

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
                    }
                } else {
                    logger.log(core.LogTag.SocketHandler.Message.NoClaimsFound, success = true)
                }
            }
        }
    }

    private suspend fun checkRecoverSentStatus(currentState: AppStateModel) {
        withContext(Dispatchers.Default) {
            logger.log(core.LogTag.SocketHandler.Message.CheckingRecoverSentStatus, success = true)
            
            val currentDeviceId = currentState.getCurrentDeviceId()
            val vaultFullInfo = currentState.getVaultFullInfo()
            
            if (currentDeviceId != null && vaultFullInfo is VaultFullInfo.Member) {
                val ssClaims = vaultFullInfo.member.ssClaims
                val claims = ssClaims?.claims
                
                if (claims != null && claims.isNotEmpty()) {
                    logger.log(core.LogTag.SocketHandler.Message.CheckingRecoverSentStatusClaims, "${claims.values}", success = true)
                    
                    val firstSentClaim = claims.values.firstOrNull { claim ->
                        val isRecoverType = claim.distributionType == DistributionType.RECOVER
                        val isSenderForThisDevice = claim.sender == currentDeviceId
                        val isAlreadyProcessed = processedRecoverClaimIds.contains(claim.id)
                        val isSent = claim.receivers.any { receiverId ->
                            claim.status.statuses[receiverId] == ClaimStatus.SENT
                        }
                        logger.log(core.LogTag.SocketHandler.Message.CheckingRecoverSentStatusDetails, 
                            "claimId: ${claim.id}, isRecoverType: $isRecoverType, isSenderForThisDevice: $isSenderForThisDevice, isSent: $isSent, isAlreadyProcessed: $isAlreadyProcessed", success = true)
                        isRecoverType && isSenderForThisDevice && isSent && !isAlreadyProcessed
                    }

                    if (firstSentClaim != null) {
                        val claimId = firstSentClaim.id
                        val secretId = firstSentClaim.distClaimId.passId.name
                        
                        processedRecoverClaimIds.add(claimId)
                        
                        logger.log(core.LogTag.SocketHandler.Message.RecoverSentForSecretId,
                            "claimId=$claimId, secretId=$secretId", success = true)
                        _socketActionType.value = SocketActionModel.RECOVER_SENT(claimId, secretId)
                    }
                }
            }
        }
    }
    
    private suspend fun checkRecoverDeclinedStatus(currentState: AppStateModel) {
        withContext(Dispatchers.Default) {
            logger.log(core.LogTag.SocketHandler.Message.CheckingRecoverDeclinedStatus, success = true)
            
            val currentDeviceId = currentState.getCurrentDeviceId()
            val vaultFullInfo = currentState.getVaultFullInfo()
            
            if (currentDeviceId != null && vaultFullInfo is VaultFullInfo.Member) {
                val ssClaims = vaultFullInfo.member.ssClaims
                val claims = ssClaims?.claims
                
                if (claims != null && claims.isNotEmpty()) {
                    val firstDeclinedClaim = claims.values.firstOrNull { claim ->
                        val isRecoverType = claim.distributionType == DistributionType.RECOVER
                        val isSenderForThisDevice = claim.sender == currentDeviceId
                        val isAlreadyProcessed = processedRecoverClaimIds.contains(claim.id)
                        val isDeclined = claim.receivers.any { receiverId ->
                            claim.status.statuses[receiverId] == ClaimStatus.DECLINED
                        }
                        isRecoverType && isSenderForThisDevice && isDeclined && !isAlreadyProcessed
                    }

                    if (firstDeclinedClaim != null) {
                        val claimId = firstDeclinedClaim.id
                        val secretId = firstDeclinedClaim.distClaimId.passId.name
                        
                        processedRecoverClaimIds.add(claimId)
                        
                        logger.log(core.LogTag.SocketHandler.Message.RecoverDeclinedForSecretId,
                            "claimId=$claimId, secretId=$secretId", success = true)
                        _socketActionType.value = SocketActionModel.RECOVER_DECLINED(secretId)
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        logger.log(core.LogTag.SocketHandler.Message.TimerStopped, success = true)
        timerJob?.cancel()
        timerJob = null
    }

    override fun pausePolling() {
        logger.log(core.LogTag.SocketHandler.Message.PollingPaused, success = true)
        isPaused = true
    }

    override fun resumePolling() {
        logger.log(core.LogTag.SocketHandler.Message.PollingResumed, success = true)
        isPaused = false
    }

    override fun restartTimer() {
        if (!restartMutex.tryLock()) {
            logger.log(core.LogTag.SocketHandler.Message.TimerRestartSkipped, success = true)
            return
        }
        try {
            stopTimer()
            startFollowing()
        } finally {
            restartMutex.unlock()
        }
    }
    
    override fun clearProcessedRecoverClaims() {
        processedRecoverClaimIds.clear()
    }

    fun dispose() {
        stopTimer()
        timerScope.cancel()
    }
}
