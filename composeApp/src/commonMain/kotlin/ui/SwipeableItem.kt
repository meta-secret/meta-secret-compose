package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import core.ScreenMetricsProviderInterface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeableItem(
    itemsCount: Int = -1,
    buttonText: String,
    isRevealed: Boolean,
    screenMetricsProvider: ScreenMetricsProviderInterface,
    action: (Boolean) -> Unit,
    onExpanded: () -> Unit = {},
    onCollapsed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var deleteButtonSize by remember {
        mutableFloatStateOf(0f)
    }
    val offset = remember {
        Animatable(initialValue = 0f)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = isRevealed, deleteButtonSize) {
        if (isRevealed) {
            offset.animateTo(deleteButtonSize)
        } else {
            offset.animateTo(0f)
        }
    }
    Box {
        Row(modifier = Modifier.onSizeChanged {
            deleteButtonSize = -it.width.toFloat()
        }.align(Alignment.CenterEnd).padding(end = 16.dp)) {
            RemoveButton(
                screenMetricsProvider,
                {action(true)},
                buttonText
            )
        }
        if (itemsCount != 1) {  // Single device swipe behaviour
            Box(modifier = Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(deleteButtonSize) {
                    detectHorizontalDragGestures(onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            val newOffset =
                                (offset.value + dragAmount).coerceIn(deleteButtonSize, 0f)
                            offset.snapTo(newOffset)
                        }
                    }, onDragEnd = {
                        when {
                            offset.value <= deleteButtonSize / 2f -> {
                                scope.launch {
                                    offset.animateTo(deleteButtonSize)
                                    onExpanded()
                                }
                            }

                            else -> {
                                scope.launch {
                                    offset.animateTo(0f)
                                    onCollapsed()
                                }
                            }
                        }
                    })
                }) {
                content()
            }
        } else {
            content()
        }
    }
}