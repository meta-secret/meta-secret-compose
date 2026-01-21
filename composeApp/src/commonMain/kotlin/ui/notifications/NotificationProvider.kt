package ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import core.NotificationCoordinatorInterface
import core.NotificationState
import core.ScreenMetricsProviderInterface
import org.koin.compose.koinInject

@Composable
fun NotificationProvider(
    notificationCoordinator: NotificationCoordinatorInterface,
    screenMetricsProvider: ScreenMetricsProviderInterface
) {
    val notificationState by notificationCoordinator.notificationState.collectAsState()
    
    val (isVisible, message, isError) = when (val state = notificationState) {
        is NotificationState.Visible -> Triple(true, state.message, state.isError)
        is NotificationState.Hidden -> Triple(false, "", false)
    }
    
    InAppNotification(
        screenMetricsProvider = screenMetricsProvider,
        isSuccessful = !isError,
        message = message,
        onDismiss = { notificationCoordinator.dismiss() },
        visible = isVisible
    )
}
