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
import core.BiometricAuthenticatorInterface
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.appInternalModels.RestoreData
import models.appInternalModels.RecoveredSecretTarget
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val dependencies: MainScreenDependencies,
) : CommonViewModel() {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount

    private val _isWarningShown = MutableStateFlow(false)
    val isWarningShown: StateFlow<Boolean> = _isWarningShown
    
    private val _isWarningDismissedByUser = MutableStateFlow(false)
    
    private val _recoveredSecretTarget = MutableStateFlow<RecoveredSecretTarget?>(null)
    val recoveredSecretTarget: StateFlow<RecoveredSecretTarget?> = _recoveredSecretTarget
    val pendingRecoveryRequests: StateFlow<List<RestoreData>> = socketHandler.pendingRecoveryRequests

    val devicesCount: StateFlow<Int> = dependencies.stats.devicesCount

    private val _isJoinBadgeDismissed = MutableStateFlow(false)
    private val _navigationEvent = MutableStateFlow<MainNavigationEvent>(MainNavigationEvent.Idle)
    val navigationEvent: StateFlow<MainNavigationEvent> = _navigationEvent
    val hasJoinRequestsBadge: StateFlow<Boolean> = dependencies.stats.joinRequestsCount
        .combine(_isJoinBadgeDismissed) { count, dismissed ->
            (count ?: 0) > 0 && !dismissed
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**
     * Resolve the request at click time instead of closing over a list from a
     * previous recomposition. The badge and the button can survive a claim
     * refresh with the same count while the claim id changes; resolving from
     * the current StateFlow keeps the alert bound to the current claim.
     */
    fun openRecoveryRequest(secretId: String) {
        val pendingRequests = pendingRecoveryRequests.value
        val stateBefore = recoveryAlertStateDescription()
        logger.e2eDiagnostic(
            "recovery-open-click secretId=$secretId " +
                "pendingCount=${pendingRequests.size} " +
                "pending=${pendingRequests.joinToString { "${it.claimId}:${it.secretId}" }} " +
                "alertBefore=$stateBefore",
        )

        val restoreData = pendingRequests
            .firstOrNull { it.secretId == secretId }
        if (restoreData == null) {
            logger.e2eDiagnostic(
                "recovery-open-no-match secretId=$secretId " +
                    "pendingCount=${pendingRequests.size} " +
                    "pending=${pendingRequests.joinToString { "${it.claimId}:${it.secretId}" }} " +
                    "alertState=$stateBefore",
            )
            return
        }
        openRecoveryRequest(restoreData)
    }

    private fun openRecoveryRequest(restoreData: RestoreData) {
        val stateBefore = recoveryAlertStateDescription()
        logger.e2eDiagnostic(
            "recovery-open-call claimId=${restoreData.claimId} " +
                "secretId=${restoreData.secretId} senderType=${restoreData.senderType} " +
                "alertBefore=$stateBefore",
        )
        dependencies.alerts.showRecoveryRequest(restoreData)
        logger.e2eDiagnostic(
            "recovery-open-result claimId=${restoreData.claimId} " +
                "alertAfter=${recoveryAlertStateDescription()}",
        )
    }

    private fun recoveryAlertStateDescription(): String = when (
        val state = dependencies.alerts.recoveryRequestAlert.value
    ) {
        is core.RecoveryRequestAlertState.Hidden -> "Hidden"
        is core.RecoveryRequestAlertState.Visible ->
            "Visible(${state.restoreData.claimId}:${state.restoreData.secretId})"
        is core.RecoveryRequestAlertState.Processing ->
            "Processing(${state.restoreData.claimId}:${state.restoreData.secretId})"
    }

    init {
        logger.log(LogTag.MainVM.Message.FollowResponsibleToAcceptJoin, success = true)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )

        dependencies.alerts.setRecoveryRequestHandler { isAccepted ->
            val currentState = dependencies.alerts.recoveryRequestAlert.value
            val restoreData = when (currentState) {
                is core.RecoveryRequestAlertState.Visible -> currentState.restoreData
                is core.RecoveryRequestAlertState.Processing -> currentState.restoreData
                else -> {
                    dependencies.alerts.onRecoveryRequestProcessingComplete()
                    return@setRecoveryRequestHandler
                }
            }

            if (!isAccepted) {
                logger.log(LogTag.MainVM.Message.RecoverDeclined, "clicked claimId=${restoreData.claimId}",
                    success = true)
                viewModelScope.launch(Dispatchers.IO) {
                    socketHandler.pauseRefreshes()
                    try {
                        logger.log(LogTag.MainVM.Message.DeclineRecoverCalled, "claimId = ${restoreData.claimId}",
                            success = true)
                        metaSecretAppManager.declineRecover(restoreData.claimId)
                    } catch (t: Throwable) {
                        logger.log(LogTag.MainVM.Message.DeclineRecoverFailed,
                            "claimId = ${restoreData.claimId}: $t", success = false)
                        socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    } finally {
                        socketHandler.resumeRefreshes()
                        socketHandler.refreshAppState()
                        withContext<Unit>(Dispatchers.Main) {
                            dependencies.alerts.onRecoveryRequestProcessingComplete()
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
                            logger.log(LogTag.MainVM.Message.AcceptRecoverCalled,
                                "claimId = ${restoreData.claimId}", success = true)
                            metaSecretAppManager.acceptRecover(restoreData.claimId)
                        } catch (t: Throwable) {
                            logger.log(LogTag.MainVM.Message.AcceptRecoverFailed,
                                "claimId = ${restoreData.claimId}: $t", success = false)
                            socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                        } finally {
                            socketHandler.resumeRefreshes()
                            socketHandler.refreshAppState()
                            withContext<Unit>(Dispatchers.Main) {
                                dependencies.alerts.onRecoveryRequestProcessingComplete()
                            }
                        }
                    }
                },
                onError = { error ->
                    logger.log(LogTag.MainVM.Message.BiometricAuthFailed, error, success = false)
                    socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    dependencies.alerts.onRecoveryRequestProcessingComplete()
                },
                onFallback = {
                    logger.log(LogTag.MainVM.Message.BiometricAuthFallback, success = false)
                    socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
                    dependencies.alerts.onRecoveryRequestProcessingComplete()
                }
            )
        }

        dependencies.alerts.setRecoveryRequestDismissHandler { restoreData ->
            socketHandler.resetReadyToRecoverDedup(restoreData.claimId)
        }

        // Recovery requests are deliberately surfaced as badges on the
        // affected secret, rather than as a FIFO queue of automatic dialogs.
        // Another receiver may complete a claim while this device has its
        // approval dialog open; refreshed state is authoritative, so close
        // that obsolete dialog.
        viewModelScope.launch {
            socketHandler.pendingRecoveryRequests.collect { pendingRequests ->
                val visibleClaimId = (dependencies.alerts.recoveryRequestAlert.value
                    as? core.RecoveryRequestAlertState.Visible)
                    ?.restoreData
                    ?.claimId
                dependencies.alerts.diagnostic(
                    "pending-collector pending=" +
                        pendingRequests.joinToString { "${it.claimId}:${it.secretId}" } +
                        " visibleClaim=${visibleClaimId ?: "none"} " +
                        "alert=${recoveryAlertStateDescription()}"
                )
                if (visibleClaimId == null) return@collect

                if (pendingRequests.none { it.claimId == visibleClaimId }) {
                    dependencies.alerts.diagnostic(
                        "dismiss-origin=pending-list-collector visibleClaim=$visibleClaimId " +
                            "reason=claim-not-in-pending pending=" + pendingRequests.joinToString { it.claimId }
                    )
                    logger.log(
                        LogTag.MainVM.Message.RecoveryAlertDismissed,
                        "claimId=$visibleClaimId is no longer pending",
                        success = true,
                    )
                    dependencies.alerts.dismissRecoveryRequest()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActions.collect { action ->
                when (action) {
                    is SocketActionModel.DISMISS_RECOVERY_REQUEST -> {
                        withContext(Dispatchers.Main) {
                            val alertState = dependencies.alerts.recoveryRequestAlert.value
                            val shouldDismiss = shouldDismissRecoveryRequest(alertState, action.claimId)
                            dependencies.alerts.diagnostic(
                                "dismiss-origin=socket-action-DISMISS_RECOVERY_REQUEST " +
                                    "completedClaim=${action.claimId} " +
                                    "visible=${recoveryAlertStateDescription()} matched=$shouldDismiss"
                            )
                            if (shouldDismiss) {
                                logger.log(
                                    LogTag.MainVM.Message.RecoveryAlertDismissed,
                                    "claimId=${action.claimId}",
                                    success = true,
                                )
                                dependencies.alerts.dismissRecoveryRequest()
                            }
                        }
                    }

                    SocketActionModel.CURRENT_DEVICE_REMOVED -> resetRemovedDeviceSession()
                    else -> Unit
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActionType.collect { actionType ->
                when (actionType) {
                    is SocketActionModel.RECOVER_ACCEPTED -> {
                        _recoveredSecretTarget.value = RecoveredSecretTarget(
                            claimId = actionType.claimId,
                            secretId = actionType.secretId,
                        )
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
                            _recoveredSecretTarget.value = null
                            dependencies.alerts.showRecoverDeclinedNotification()
                        }
                    }
                    else -> { /* ignore */ }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            dependencies.stats.joinRequestsCount.collect { count ->
                withContext(Dispatchers.Main) {
                    _joinRequestsCount.value = count
                    if ((count ?: 0) > 0) {
                        _isWarningDismissedByUser.value = false
                        _isJoinBadgeDismissed.value = false
                        if (TabStateHolder.selectedTabIndex.value != 1) {
                            _isWarningShown.value = true
                        }
                    } else if (dependencies.stats.devicesCount.value >= WARNING_DEVICE_THRESHOLD) {
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
        _recoveredSecretTarget.value = null
    }

    fun consumeNavigationEvent() {
        _navigationEvent.value = MainNavigationEvent.Idle
        dependencies.consumeRemoval()
    }

    private suspend fun resetRemovedDeviceSession() {
        val origin = dependencies.beginRemoval() ?: return
        var receipt: core.metaSecretCore.liveboard.NativeKeyResult<Boolean>? = null
        logger.log(LogTag.MainVM.Message.CurrentDeviceRemovalReset, "started", success = true)
        try {
            if (!dependencies.sameGeneration(origin)) return
            socketHandler.onAppBackground()
            receipt = dependencies.clearRemoval(origin) ?: return
            val result = receipt
            if (!dependencies.isCurrent(result)) return
            dependencies.dismiss(result)
            if (dependencies.isCurrent(result)) _recoveredSecretTarget.value = null
        } catch (t: Throwable) {
            logger.log(LogTag.MainVM.Message.CurrentDeviceRemovalReset, "local cleanup failed: ${t.message}",
                success = false)
        } finally {
            val result = receipt
            val cleared = result?.value == true
            logger.log(LogTag.MainVM.Message.CurrentDeviceRemovalReset, "completed localDataCleared=$cleared",
                success = cleared)
            try {
                withContext(Dispatchers.Main) {
                    if (result != null && dependencies.isCurrent(result)) _navigationEvent.value =
                        MainNavigationEvent.NavigateToSignIn
                }
            } finally { dependencies.finishRemoval(origin, result) }
        }
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

internal fun shouldDismissRecoveryRequest(
    alertState: core.RecoveryRequestAlertState,
    completedClaimId: String,
): Boolean {
    val activeClaimId = when (alertState) {
        is core.RecoveryRequestAlertState.Visible -> alertState.restoreData.claimId
        is core.RecoveryRequestAlertState.Processing -> alertState.restoreData.claimId
        is core.RecoveryRequestAlertState.Hidden -> null
    }
    return activeClaimId == completedClaimId
}

sealed class MainViewEvents : CommonViewModelEventsInterface {
    data class SetTabIndex(val index: Int) : MainViewEvents()
    data class ShowWarning(val isToShow: Boolean) : MainViewEvents()
}

sealed class MainNavigationEvent {
    data object Idle : MainNavigationEvent()
    data object NavigateToSignIn : MainNavigationEvent()
}

private const val WARNING_DEVICE_THRESHOLD: Int = 3
