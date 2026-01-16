package core

import kotlinx.coroutines.flow.StateFlow

interface NotificationCoordinatorInterface {
    val notificationState: StateFlow<NotificationState>
    
    fun showError(message: String)
    fun showSuccess(message: String)
    fun dismiss()
}

sealed class NotificationState {
    data object Hidden : NotificationState()
    data class Visible(val message: String, val isError: Boolean) : NotificationState()
}
