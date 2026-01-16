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
    
    when (val state = notificationState) {
        is NotificationState.Visible -> {
            InAppNotification(
                screenMetricsProvider = screenMetricsProvider,
                isSuccessful = !state.isError,
                message = state.message,
                onDismiss = { notificationCoordinator.dismiss() },
                visible = true
            )
        }
        is NotificationState.Hidden -> {}
    }
}
