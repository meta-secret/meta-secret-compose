package core

import kotlinx.coroutines.flow.StateFlow

interface AlertCoordinatorInterface {
    val joinRequestAlert: StateFlow<JoinRequestAlertState>
    
    fun showJoinRequest(deviceId: String)
    fun dismissJoinRequest()
    fun onJoinRequestDecision(isAccepted: Boolean)
    fun setJoinRequestHandler(handler: (Boolean) -> Unit)
}

sealed class JoinRequestAlertState {
    data object Hidden : JoinRequestAlertState()
    data class Visible(val deviceId: String) : JoinRequestAlertState()
    data class Processing(val deviceId: String) : JoinRequestAlertState()
}

