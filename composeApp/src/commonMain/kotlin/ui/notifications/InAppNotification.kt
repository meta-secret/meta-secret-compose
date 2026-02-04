package ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close_blue
import org.jetbrains.compose.resources.painterResource
import core.AppColors
import core.ScreenMetricsProviderInterface

@Composable
fun InAppNotification(
    screenMetricsProvider: ScreenMetricsProviderInterface,
    isSuccessful: Boolean,
    message: String,
    onDismiss: () -> Unit,
    visible: Boolean = true
) {
    val color = when (isSuccessful) {
        true -> AppColors.ActionMain
        false -> AppColors.RedError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 45.dp, end = 16.dp, bottom = 35.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (screenMetricsProvider.heightFactor() * 48).dp, max = 200.dp)
                        .background(color, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically

                    ) {
                        Column {
                            Text(
                                text = message,
                                color = Color.White,
                                modifier = Modifier
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier

                        ) {
                            Image(
                                painter = painterResource(Res.drawable.close_blue),
                                contentDescription = "Dismiss"
                            )
                        }
                    }
                }
            }
        }
    }
}
