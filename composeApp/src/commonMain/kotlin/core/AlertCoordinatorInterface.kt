package core

import kotlinx.coroutines.flow.StateFlow
import models.appInternalModels.RestoreData

interface AlertCoordinatorInterface {
    val joinRequestAlert: StateFlow<JoinRequestAlertState>
    val recoveryRequestAlert: StateFlow<RecoveryRequestAlertState>
    
    fun showJoinRequest(deviceId: String)
    fun dismissJoinRequest()
    fun onJoinRequestDecision(isAccepted: Boolean)
    fun setJoinRequestHandler(handler: (Boolean) -> Unit)
    
    fun showRecoveryRequest(restoreData: RestoreData)
    fun dismissRecoveryRequest()
    fun onRecoveryRequestDecision(isAccepted: Boolean)
    fun setRecoveryRequestHandler(handler: (Boolean) -> Unit)
    fun onRecoveryRequestProcessingComplete()
}

sealed class JoinRequestAlertState {
    data object Hidden : JoinRequestAlertState()
    data class Visible(val deviceId: String) : JoinRequestAlertState()
    data class Processing(val deviceId: String) : JoinRequestAlertState()
}

sealed class RecoveryRequestAlertState {
    data object Hidden : RecoveryRequestAlertState()
    data class Visible(val restoreData: RestoreData) : RecoveryRequestAlertState()
    data class Processing(val restoreData: RestoreData) : RecoveryRequestAlertState()
}
