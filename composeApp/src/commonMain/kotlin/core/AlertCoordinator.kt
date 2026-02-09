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
    
    private val recoverQueue: ArrayDeque<RestoreData> = ArrayDeque()
    
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
        recoverQueue.addLast(restoreData)
        if (_recoveryRequestAlert.value is RecoveryRequestAlertState.Hidden) {
            showNextRecoveryPrompt()
        }
    }
    
    override fun dismissRecoveryRequest() {
        _recoveryRequestAlert.value = RecoveryRequestAlertState.Hidden
        showNextRecoveryPrompt()
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
    
    override fun onRecoveryRequestProcessingComplete() {
        _recoveryRequestAlert.value = RecoveryRequestAlertState.Hidden
        showNextRecoveryPrompt()
    }
    
    private fun showNextRecoveryPrompt() {
        if (recoverQueue.isNotEmpty()) {
            val next = recoverQueue.removeFirst()
            _recoveryRequestAlert.value = RecoveryRequestAlertState.Visible(next)
        } else {
            _recoveryRequestAlert.value = RecoveryRequestAlertState.Hidden
        }
    }
    
    override fun showRecoverDeclinedNotification() {
        notificationCoordinator.showError(stringProvider.errorRecoverDeclined())
    }
}
