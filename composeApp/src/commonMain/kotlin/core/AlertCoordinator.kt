package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import models.appInternalModels.RestoreData

class AlertCoordinator(
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val stringProvider: StringProviderInterface
) : AlertCoordinatorInterface {
    private val _joinRequestAlert = MutableStateFlow<JoinRequestAlertState>(JoinRequestAlertState.Hidden)
    override val joinRequestAlert: StateFlow<JoinRequestAlertState> = _joinRequestAlert.asStateFlow()
    
    private val _recoveryRequestAlert = MutableStateFlow<RecoveryRequestAlertState>(RecoveryRequestAlertState.Hidden)
    override val recoveryRequestAlert: StateFlow<RecoveryRequestAlertState> = _recoveryRequestAlert.asStateFlow()
    
    private var joinRequestHandler: ((Boolean) -> Unit)? = null
    private var recoveryRequestHandler: ((Boolean) -> Unit)? = null
    private var recoveryRequestDismissHandler: ((RestoreData) -> Unit)? = null
    
    override fun showJoinRequest(deviceId: String) {
        _joinRequestAlert.value = JoinRequestAlertState.Visible(deviceId)
    }
    
    override fun dismissJoinRequest() {
        _joinRequestAlert.value = JoinRequestAlertState.Hidden
    }
    
    override fun onJoinRequestDecision(isAccepted: Boolean) {
        val currentState = _joinRequestAlert.value
        if (currentState is JoinRequestAlertState.Visible) {
            _joinRequestAlert.value = JoinRequestAlertState.Processing(currentState.deviceId)
            joinRequestHandler?.invoke(isAccepted)
        }
    }
    
    override fun setJoinRequestHandler(handler: (Boolean) -> Unit) {
        joinRequestHandler = handler
    }
    
    override fun showRecoveryRequest(restoreData: RestoreData) {
        val current = _recoveryRequestAlert.value
        val currentClaimId = when (current) {
            is RecoveryRequestAlertState.Visible -> current.restoreData.claimId
            is RecoveryRequestAlertState.Processing -> current.restoreData.claimId
            else -> null
        }
        if (currentClaimId == restoreData.claimId) {
            return
        }
        if (current is RecoveryRequestAlertState.Hidden) {
            _recoveryRequestAlert.value = RecoveryRequestAlertState.Visible(restoreData)
        }
    }
    
    override fun dismissRecoveryRequest() {
        val current = _recoveryRequestAlert.value
        val restoreData = when (current) {
            is RecoveryRequestAlertState.Visible -> current.restoreData
            is RecoveryRequestAlertState.Processing -> current.restoreData
            else -> null
        }
        if (restoreData != null) {
            recoveryRequestDismissHandler?.invoke(restoreData)
        }
        _recoveryRequestAlert.value = RecoveryRequestAlertState.Hidden
    }

    override fun onRecoveryRequestDecision(isAccepted: Boolean) {
        val currentState = _recoveryRequestAlert.value
        if (currentState is RecoveryRequestAlertState.Visible) {
            _recoveryRequestAlert.value = RecoveryRequestAlertState.Processing(currentState.restoreData)
            recoveryRequestHandler?.invoke(isAccepted)
        }
    }
    
    override fun setRecoveryRequestHandler(handler: (Boolean) -> Unit) {
        recoveryRequestHandler = handler
    }

    override fun setRecoveryRequestDismissHandler(handler: (RestoreData) -> Unit) {
        recoveryRequestDismissHandler = handler
    }
    
    override fun onRecoveryRequestProcessingComplete() {
        _recoveryRequestAlert.value = RecoveryRequestAlertState.Hidden
    }
    
    override fun showRecoverDeclinedNotification() {
        notificationCoordinator.showError(stringProvider.errorRecoverDeclined())
    }
}
