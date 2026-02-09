package ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import core.NotificationCoordinatorInterface
import core.NotificationState
import core.ScreenMetricsProviderInterface
import kotlinx.coroutines.delay

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

    var showDialog by remember { mutableStateOf(false) }
    var animateIn by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    var lastIsError by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            lastMessage = message
            lastIsError = isError
            animateIn = false
            showDialog = true
            delay(50)
            animateIn = true
        } else if (showDialog) {
            animateIn = false
            delay(350)
            showDialog = false
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { notificationCoordinator.dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            InAppNotification(
                screenMetricsProvider = screenMetricsProvider,
                isSuccessful = !lastIsError,
                message = lastMessage,
                onDismiss = { notificationCoordinator.dismiss() },
                visible = animateIn
            )
        }
    }
}
