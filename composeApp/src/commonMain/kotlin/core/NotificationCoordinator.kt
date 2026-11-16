package core

import core.metaSecretCore.liveboard.LiveboardPrivacyOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NotificationCoordinator(private val privacy: LiveboardPrivacyOwner) : NotificationCoordinatorInterface {
    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Hidden)
    override val notificationState: StateFlow<NotificationState> = _notificationState.asStateFlow()
    
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoDismissJob: Job? = null
    
    override fun showError(message: String) = runBlocking(Dispatchers.Main.immediate) {
        val event = privacy.beforeError(message)
        if (!privacy.mayDisplay(event)) return@runBlocking
        autoDismissJob?.cancel()
        _notificationState.value = NotificationState.Visible(message, isError = true)
        scheduleAutoDismiss()
    }
    
    override fun showSuccess(message: String) {
        autoDismissJob?.cancel()
        _notificationState.value = NotificationState.Visible(message, isError = false)
        scheduleAutoDismiss()
    }
    
    override fun dismiss() {
        autoDismissJob?.cancel()
        _notificationState.value = NotificationState.Hidden
    }
    
    private fun scheduleAutoDismiss() {
        autoDismissJob = coordinatorScope.launch {
            delay(5000)
            _notificationState.value = NotificationState.Hidden
        }
    }
}
