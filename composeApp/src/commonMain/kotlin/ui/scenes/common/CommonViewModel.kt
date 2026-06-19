package ui.scenes.common

import androidx.lifecycle.ViewModel
import core.NotificationCoordinatorInterface
import core.DebugLoggerInterface
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class CommonViewModel : ViewModel(), KoinComponent {
    protected val logger: DebugLoggerInterface by inject()
    protected val notificationCoordinator: NotificationCoordinatorInterface by inject()

    protected fun showNotification(message: String, isError: Boolean) {
        if (isError) {
            notificationCoordinator.showError(message)
        } else {
            notificationCoordinator.showSuccess(message)
        }
    }
    
    abstract fun handle(event: CommonViewModelEventsInterface)
}

interface CommonViewModelEventsInterface {}
