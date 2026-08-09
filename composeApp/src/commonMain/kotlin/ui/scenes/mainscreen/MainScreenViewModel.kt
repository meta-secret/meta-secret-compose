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
        logger.log(LogTag.MainVM.Message.FollowResponsibleToAcceptJoin, success = true)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )

        alertCoordinator.setRecoveryRequestHandler { isAccepted ->
            val currentState = alertCoordinator.recoveryRequestAlert.value
            val restoreData = when (currentState) {
                is core.RecoveryRequestAlertState.Visible -> currentState.restoreData
                is core.RecoveryRequestAlertState.Processing -> currentState.restoreData
                else -> {
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                    return@setRecoveryRequestHandler
                }
            }

            if (!isAccepted) {
                logger.log(LogTag.MainVM.Message.RecoverDeclined, "clicked claimId=${restoreData.claimId}", success = true)
                viewModelScope.launch(Dispatchers.IO) {
                    socketHandler.pauseRefreshes()
                    try {
                        logger.log(LogTag.MainVM.Message.DeclineRecoverCalled, "claimId = ${restoreData.claimId}", success = true)
                        metaSecretAppManager.declineRecover(restoreData.claimId)
                    } catch (t: Throwable) {
                        logger.log(LogTag.MainVM.Message.DeclineRecoverFailed, "claimId = ${restoreData.claimId}: $t", success = false)
                        socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    } finally {
                        socketHandler.resumeRefreshes()
                        socketHandler.refreshAppState()
                        withContext<Unit>(Dispatchers.Main) {
                            alertCoordinator.onRecoveryRequestProcessingComplete()
                        }
                    }
                }
                return@setRecoveryRequestHandler
            }

            logger.log(LogTag.MainVM.Message.RecoverAccepted, "clicked claimId=${restoreData.claimId}", success = true)
            biometricAuthenticator.authenticate(
                onSuccess = {
                    logger.log(LogTag.MainVM.Message.BiometricAuthSuccess, success = true)
                    viewModelScope.launch(Dispatchers.IO) {
                        socketHandler.pauseRefreshes()
                        try {
                            logger.log(LogTag.MainVM.Message.AcceptRecoverCalled, "claimId = ${restoreData.claimId}", success = true)
                            metaSecretAppManager.acceptRecover(restoreData.claimId)
                        } catch (t: Throwable) {
                            logger.log(LogTag.MainVM.Message.AcceptRecoverFailed, "claimId = ${restoreData.claimId}: $t", success = false)
                            socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                        } finally {
                            socketHandler.resumeRefreshes()
                            socketHandler.refreshAppState()
                            withContext<Unit>(Dispatchers.Main) {
                                alertCoordinator.onRecoveryRequestProcessingComplete()
                            }
                        }
                    }
                },
                onError = { error ->
                    logger.log(LogTag.MainVM.Message.BiometricAuthFailed, error, success = false)
                    socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                },
                onFallback = {
                    logger.log(LogTag.MainVM.Message.BiometricAuthFallback, success = false)
                    socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    alertCoordinator.onRecoveryRequestProcessingComplete()
                }
            )
        }

        alertCoordinator.setRecoveryRequestDismissHandler { restoreData ->
            socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
        }

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActions.collect { action ->
                if (action is SocketActionModel.DISMISS_RECOVERY_REQUEST) {
                    withContext(Dispatchers.Main) {
                        logger.log(LogTag.MainVM.Message.RecoveryAlertDismissed, success = true)
                        alertCoordinator.dismissRecoveryRequest()
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActionType.collect { actionType ->
                when (actionType) {
                    is SocketActionModel.READY_TO_RECOVER -> {
                        val restoreData = actionType.restoreData
                        logger.log(LogTag.MainVM.Message.ReadyToRecoverSignal, "$restoreData", success = true)

                        val secrets = metaSecretAppManager.getSecretsFromVault(true)
                        val existingSecretsIds = secrets?.map { it.name }?.toSet()
                        logger.log(LogTag.MainVM.Message.ReadyToRecoverExistingSecrets, "$existingSecretsIds", success = true)

                        val secretExists = existingSecretsIds?.contains(restoreData.secretId) == true
                        if (!secretExists) {
                            logger.log(LogTag.MainVM.Message.ReadyToRecoverNothing, success = true)
                            return@collect
                        }

                        withContext(Dispatchers.Main) {
                            logger.log(LogTag.MainVM.Message.RecoveryAlertShown, "claimId=${restoreData.claimId}", success = true)
                            alertCoordinator.showRecoveryRequest(restoreData)
                        }
                    }
                    is SocketActionModel.RECOVER_ACCEPTED -> {
                        _secretIdToShow.value = actionType.secretId
                        logger.log(LogTag.MainVM.Message.ReadyToShowSecret,
                            "claimId=${actionType.claimId}, secretId=${actionType.secretId}", success = true)
                    }
                    is SocketActionModel.RECOVER_DECLINED -> {
                        logger.log(LogTag.MainVM.Message.RecoverDeclined,
                            "secretId=${actionType.secretId}", success = true)
                        val claim = metaSecretAppManager.findClaim(actionType.secretId)
                        if (claim?.claimId != null) {
                            try {
                                metaSecretAppManager.sendDeclineCompletion(claim.claimId)
                            } catch (_: Throwable) { }
                        }
                        withContext(Dispatchers.Main) {
                            _secretIdToShow.value = null
                            alertCoordinator.showRecoverDeclinedNotification()
                        }
                    }
                    else -> { /* ignore */ }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            vaultStatsProvider.joinRequestsCount.collect { count ->
                withContext(Dispatchers.Main) {
                    _joinRequestsCount.value = count
                    if ((count ?: 0) > 0) {
                        _isWarningDismissedByUser.value = false
                        _isJoinBadgeDismissed.value = false
                        if (TabStateHolder.selectedTabIndex.value != 1) {
                            _isWarningShown.value = true
                        }
                    } else if (vaultStatsProvider.devicesCount.value >= 3) {
                        _isWarningShown.value = false
                        _isWarningDismissedByUser.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            var joinRequestWasVisible = false
            socketHandler.socketActionType.collect { action ->
                when (action) {
                    SocketActionModel.ASK_TO_JOIN -> joinRequestWasVisible = true
                    SocketActionModel.NONE -> if (joinRequestWasVisible) {
                        // The socket handler has refreshed the vault and confirmed that
                        // the request is no longer pending. Clear a stale warning before
                        // the stats collector receives the same snapshot.
                        joinRequestWasVisible = false
                        _joinRequestsCount.value = 0
                        _isJoinBadgeDismissed.value = false
                        _isWarningDismissedByUser.value = false
                        _isWarningShown.value = false
                    }
                    else -> Unit
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
