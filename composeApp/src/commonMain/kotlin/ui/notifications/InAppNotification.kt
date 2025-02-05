package ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close_blue
import org.jetbrains.compose.resources.painterResource
import sharedData.AppColors
import sharedData.actualHeightFactor

@Composable
fun InAppNotification(
    message: String,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 35.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((actualHeightFactor() * 48).dp)
                    .background(AppColors.ActionMain, RoundedCornerShape(10.dp))
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically

                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        modifier = Modifier
                    )
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
