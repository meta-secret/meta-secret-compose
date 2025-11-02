package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertCoordinator : AlertCoordinatorInterface {
    private val _joinRequestAlert = MutableStateFlow<JoinRequestAlertState>(JoinRequestAlertState.Hidden)
    override val joinRequestAlert: StateFlow<JoinRequestAlertState> = _joinRequestAlert.asStateFlow()
    
    private var joinRequestHandler: ((Boolean) -> Unit)? = null
    
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
    
    fun onJoinRequestProcessingComplete() {
        _joinRequestAlert.value = JoinRequestAlertState.Hidden
    }
}

