package ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.AlertCoordinatorInterface
import core.AppColors
import core.JoinRequestAlertState
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.wanna_join
import org.jetbrains.compose.resources.stringResource
import ui.dialogs.YesNoDialog

@Composable
fun AlertProvider(
    alertCoordinator: AlertCoordinatorInterface
) {
    val joinRequestState by alertCoordinator.joinRequestAlert.collectAsState()
    
    val isJoinRequestVisible = joinRequestState is JoinRequestAlertState.Visible || 
                               joinRequestState is JoinRequestAlertState.Processing
    
    val canDismiss = joinRequestState is JoinRequestAlertState.Visible
    
    BgFade(
        isVisible = isJoinRequestVisible,
        onDismiss = {
            if (canDismiss) {
                alertCoordinator.dismissJoinRequest()
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
            title = stringResource(Res.string.wanna_join),
            onDismiss = { isAccepted ->
                if (isAccepted != null) {
                    alertCoordinator.onJoinRequestDecision(isAccepted)
                } else {
                    alertCoordinator.dismissJoinRequest()
                }
            },
            isVisible = isJoinRequestVisible && joinRequestState !is JoinRequestAlertState.Processing
        )
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

