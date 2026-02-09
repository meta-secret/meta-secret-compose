package ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import core.AlertCoordinatorInterface
import core.AppColors
import core.JoinRequestAlertState
import core.RecoveryRequestAlertState
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.wanna_join
import kotlinproject.composeapp.generated.resources.wanna_recover
import org.jetbrains.compose.resources.stringResource

enum class AlertType {
    JoinRequest,
    RecoveryRequest
}

@Composable
fun AlertProvider(
    alertCoordinator: AlertCoordinatorInterface
) {
    val joinRequestState by alertCoordinator.joinRequestAlert.collectAsState()
    val recoveryRequestState by alertCoordinator.recoveryRequestAlert.collectAsState()
    
    val alertType = determineAlertType(joinRequestState, recoveryRequestState)
    val isAlertVisible = alertType != null
    
    val canDismiss = when {
        joinRequestState is JoinRequestAlertState.Visible -> true
        recoveryRequestState is RecoveryRequestAlertState.Visible -> true
        else -> false
    }
    
    val title = when (alertType) {
        AlertType.JoinRequest -> stringResource(Res.string.wanna_join)
        AlertType.RecoveryRequest -> {
            val restoreData = (recoveryRequestState as? RecoveryRequestAlertState.Visible)?.restoreData
                ?: (recoveryRequestState as? RecoveryRequestAlertState.Processing)?.restoreData
            if (restoreData != null) {
                "${stringResource(Res.string.wanna_recover)} \"${restoreData.secretId}\"?"
            } else {
                stringResource(Res.string.wanna_recover)
            }
        }
        null -> ""
    }
    
    val isProcessing = when {
        joinRequestState is JoinRequestAlertState.Processing -> true
        recoveryRequestState is RecoveryRequestAlertState.Processing -> true
        else -> false
    }
    
    BgFade(
        isVisible = isAlertVisible,
        onDismiss = {
            if (canDismiss) {
                when (alertType) {
                    AlertType.JoinRequest -> alertCoordinator.dismissJoinRequest()
                    AlertType.RecoveryRequest -> alertCoordinator.dismissRecoveryRequest()
                    null -> {}
                }
            }
        }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        YesNoDialog(
            title = title,
            onDismiss = { isAccepted ->
                if (isAccepted != null) {
                    when (alertType) {
                        AlertType.JoinRequest -> alertCoordinator.onJoinRequestDecision(isAccepted)
                        AlertType.RecoveryRequest -> alertCoordinator.onRecoveryRequestDecision(isAccepted)
                        null -> {}
                    }
                } else {
                    when (alertType) {
                        AlertType.JoinRequest -> alertCoordinator.dismissJoinRequest()
                        AlertType.RecoveryRequest -> alertCoordinator.dismissRecoveryRequest()
                        null -> {}
                    }
                }
            },
            isVisible = isAlertVisible && !isProcessing
        )
    }

    if (isProcessing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = AppColors.ActionMain,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun determineAlertType(
    joinState: JoinRequestAlertState,
    recoveryState: RecoveryRequestAlertState
): AlertType? {
    val isJoinVisible = joinState is JoinRequestAlertState.Visible || 
                       joinState is JoinRequestAlertState.Processing
    val isRecoveryVisible = recoveryState is RecoveryRequestAlertState.Visible || 
                           recoveryState is RecoveryRequestAlertState.Processing
    
    return when {
        isJoinVisible -> AlertType.JoinRequest
        isRecoveryVisible -> AlertType.RecoveryRequest
        else -> null
    }
}

@Composable
private fun BgFade(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(350)),
        exit = fadeOut(animationSpec = tween(350)),
        label = "AlertBackgroundFade"
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = AppColors.Black60)
                .clickable { onDismiss() }
        )
    }
}
