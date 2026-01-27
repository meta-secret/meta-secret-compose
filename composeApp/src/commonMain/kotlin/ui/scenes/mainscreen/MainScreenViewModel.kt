package ui.scenes.mainscreen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.BackupCoordinatorInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.LogTag
import core.ScreenMetricsProviderInterface
import core.VaultStatsProviderInterface
import core.BiometricAuthenticatorInterface
import core.AlertCoordinatorInterface
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.appInternalModels.ClaimModel
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val backupCoordinatorInterface: BackupCoordinatorInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val alertCoordinator: AlertCoordinatorInterface,
) : CommonViewModel() {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount

    private val _isWarningShown = MutableStateFlow(false)
    val isWarningShown: StateFlow<Boolean> = _isWarningShown
    
    private val _isWarningDismissedByUser = MutableStateFlow(false)
    
    private val _secretIdToShow = MutableStateFlow<String?>(null)
    val secretIdToShow: StateFlow<String?> = _secretIdToShow

    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount

    private val _isJoinBadgeDismissed = MutableStateFlow(false)
    val hasJoinRequestsBadge: StateFlow<Boolean> = vaultStatsProvider.joinRequestsCount
        .combine(_isJoinBadgeDismissed) { count, dismissed ->
            (count ?: 0) > 0 && !dismissed
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        checkBackup()
        logger.log(LogTag.MainVM.Message.FollowResponsibleToAcceptJoin, success = true)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN, SocketRequestModel.WAIT_FOR_RECOVER_REQUEST,
                SocketRequestModel.SHOW_SECRET),
            exclude = null
        )

        alertCoordinator.setRecoveryRequestHandler { isAccepted ->
            if (!isAccepted) {
                alertCoordinator.onRecoveryRequestProcessingComplete()
                return@setRecoveryRequestHandler
            }

            val currentState = alertCoordinator.recoveryRequestAlert.value
            val restoreData = when (currentState) {
                is core.RecoveryRequestAlertState.Visible -> currentState.restoreData
                is core.RecoveryRequestAlertState.Processing -> currentState.restoreData
                else -> {
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                    return@setRecoveryRequestHandler
                }
            }

            logger.log(LogTag.MainVM.Message.RecoverAccepted, success = true)
            biometricAuthenticator.authenticate(
                onSuccess = {
                    logger.log(LogTag.MainVM.Message.BiometricAuthSuccess, success = true)
                    viewModelScope.launch(Dispatchers.IO) {
                        socketHandler.pausePolling()
                        try {
                            logger.log(LogTag.MainVM.Message.AcceptRecoverCalled, "claimId = ${restoreData.claimId}", success = true)
                            metaSecretAppManager.acceptRecover(ClaimModel(restoreData.claimId))
                        } catch (t: Throwable) {
                            logger.log(LogTag.MainVM.Message.AcceptRecoverFailed, "claimId = ${restoreData.claimId}: $t", success = false)
                        } finally {
                            socketHandler.resumePolling()
                            withContext<Unit>(Dispatchers.Main) {
                                alertCoordinator.onRecoveryRequestProcessingComplete()
                            }
                        }
                    }
                },
                onError = { error ->
                    logger.log(LogTag.MainVM.Message.BiometricAuthFailed, error, success = false)
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                },
                onFallback = {
                    logger.log(LogTag.MainVM.Message.BiometricAuthFallback, success = false)
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActionType.collect { actionType ->
                when (actionType) {
                    is SocketActionModel.READY_TO_RECOVER -> {
                        val restoreData = actionType.restoreData
                        logger.log(LogTag.MainVM.Message.ReadyToRecoverSignal, "$restoreData", success = true)

                        val secrets = metaSecretAppManager.getSecretsFromVault()
                        val existingSecretsIds = secrets?.map { it.name }?.toSet()
                        logger.log(LogTag.MainVM.Message.ReadyToRecoverExistingSecrets, "$existingSecretsIds", success = true)

                        val newRequests = restoreData.filter { restoreData ->
                            existingSecretsIds?.contains(restoreData.secretId) == true
                        }
                        logger.log(LogTag.MainVM.Message.ReadyToRecoverNewRequests, "$newRequests", success = true)

                        if (newRequests.isEmpty()) {
                            logger.log(LogTag.MainVM.Message.ReadyToRecoverNothing, success = true)
                            return@collect
                        }

                        withContext(Dispatchers.Main) {
                            newRequests.forEach { restoreData ->
                                alertCoordinator.showRecoveryRequest(restoreData)
                            }
                        }
                    }
                    is SocketActionModel.RECOVER_SENT -> {
                        _secretIdToShow.value = actionType.secretId
                        logger.log(LogTag.MainVM.Message.ReadyToShowSecret,
                            "claimId=${actionType.claimId}, secretId=${actionType.secretId}", success = true)
                    }
                    else -> { /* ignore */ }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            vaultStatsProvider.joinRequestsCount.collect { count ->
                withContext(Dispatchers.Main) {
                    _joinRequestsCount.value = count
                    if (count != null && count > 0) {
                        _isWarningDismissedByUser.value = false
                    }
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is MainViewEvents) {
            when (event) {
                is MainViewEvents.SetTabIndex -> setTabIndex(event.index)
                is MainViewEvents.ShowWarning -> changeWarningVisibilityTo(event.isToShow)
            }
        }
    }

    private fun checkBackup() {
        backupCoordinatorInterface.ensureBackupDestinationSelected()
    }

    fun clearSecretIdToShow() {
        logger.log(LogTag.MainVM.Message.ClearingSecretId, success = true)
        _secretIdToShow.value = null
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
        if (index == 1) {
            val currentJoinRequests = _joinRequestsCount.value ?: 0
            if (currentJoinRequests > 0) {
                _isWarningDismissedByUser.value = true
                _isWarningShown.value = false
                _isJoinBadgeDismissed.value = true
            }
        }
    }

    private fun changeWarningVisibilityTo(state: Boolean) {
        if (state) {
            if (!_isWarningDismissedByUser.value) {
                _isWarningShown.value = true
            }
        } else {
            _isWarningDismissedByUser.value = true
            _isWarningShown.value = false
        }
    }
}

sealed class MainViewEvents : CommonViewModelEventsInterface {
    data class SetTabIndex(val index: Int) : MainViewEvents()
    data class ShowWarning(val isToShow: Boolean) : MainViewEvents()
}
