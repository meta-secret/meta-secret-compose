package ui.liveboard.visual

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class LiveboardInteractionKind { Tile, Control, Plus }
data class LiveboardSurfaceInteraction(
    val onClick: (() -> Unit)? = null, val enabled: Boolean = true, val selected: Boolean = false,
    val moves: Boolean = true, val kind: LiveboardInteractionKind = LiveboardInteractionKind.Tile,
)
class LiveboardInteractionState(
    val source: MutableInteractionSource, val pressed: Boolean, val hovered: Boolean,
    val focused: Boolean, val options: LiveboardSurfaceInteraction,
)

@Composable
fun rememberLiveboardInteraction(options: LiveboardSurfaceInteraction): LiveboardInteractionState {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    return LiveboardInteractionState(source, pressed && options.enabled, hovered && options.enabled, focused &&
        options.enabled, options)
}

/** Click owns one existing callback. A non-clickable parent only observes child presses, consuming none. */
@Composable
fun Modifier.liveboardInteract(state: LiveboardInteractionState): Modifier {
    val options = state.options
    val amount = when {
        !options.moves -> 0f
        state.pressed -> if (options.kind == LiveboardInteractionKind.Tile && !state.hovered
            ) PRESSED_TILE_LIFT else if (options.kind == LiveboardInteractionKind.Control) 2f else PRESSED_FALLBACK_LIFT
        state.hovered -> if (options.kind == LiveboardInteractionKind.Tile) -HOVER_TILE_LIFT else -HOVER_CONTROL_LIFT
        else -> 0f
    }
    val lift by animateDpAsState(amount.dp, tween(if (LocalLiveboardReducedMotion.current
        ) 0 else LIFT_DURATION_MILLIS, easing = liveboardEasing("ease")))
    val input = Modifier.hoverable(state.source, options.enabled).then(if (options.onClick != null) {
        Modifier.clickable(state.source, indication = null, enabled = options.enabled, role = Role.Button, onClick =
            options.onClick)
    } else Modifier.pointerInput(state.source, options.enabled) {
        if (options.enabled) awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val press = PressInteraction.Press(down.position)
            state.source.tryEmit(press)
            try {
                var held = true
                while (held) {
                    val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                    held = change?.pressed == true
                }
                state.source.tryEmit(PressInteraction.Release(press))
            } finally { state.source.tryEmit(PressInteraction.Cancel(press)) }
        }
    })
    return then(input).graphicsLayer { translationY = lift.toPx() }
}

private const val PRESSED_TILE_LIFT: Float = 4f
private const val PRESSED_FALLBACK_LIFT: Float = 3f
private const val HOVER_TILE_LIFT: Float = 3f
private const val HOVER_CONTROL_LIFT: Float = 2f
private const val LIFT_DURATION_MILLIS: Int = 220
