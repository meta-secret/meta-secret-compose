package ui.notifications

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.AppColors
import org.koin.compose.koinInject
import ui.theme.AppTextStyles

@Composable
fun WarningBubble(
    text: AnnotatedString,
    mainAction: () -> Unit?,
    closeAction: () -> Unit,
    isVisible: Boolean = true
) {
    val imageProvider: ImageProviderInterface = koinInject()
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.White5, RoundedCornerShape(10.dp))
                .height(85.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .height(60.dp)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painter = imageProvider.getPainter(AppImage.Warning),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
                var textLayoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
                Text(
                    text = text,
                    style = AppTextStyles.Paragraph().copy(color = AppColors.White75),
                    modifier = Modifier
                        .padding(end = 30.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset: Offset ->
                                textLayoutResult?.let { layoutResult ->
                                    val position = layoutResult.getOffsetForPosition(offset)
                                    text.getStringAnnotations("addText", position, position)
                                        .firstOrNull()
                                        ?.let {
                                            mainAction()
                                        }
                                }
                            }
                        },
                    onTextLayout = { layoutResult ->
                        textLayoutResult = layoutResult
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .padding(end = 12.dp)
            ) {
                Image(
                    painter = imageProvider.getPainter(AppImage.Close),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable {
                            closeAction()
                        }
                )
            }
        }
    }
}
