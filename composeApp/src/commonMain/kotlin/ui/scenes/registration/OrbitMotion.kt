package ui.scenes.registration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.MotionDurationScale
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import ui.design.generated.OrbitTokens as T

internal val LocalOrbitInteractive = staticCompositionLocalOf { true }

/** Keeps the ruler outside both rotating layers, with a common right-side pivot. */
@Composable
internal fun OrbitMotion(
    state: RegistrationUiState,
    pivot: Float,
    content: @Composable (RegistrationUiState, Boolean) -> Unit,
) {
    var incoming by remember { mutableStateOf(state) }
    var outgoing by remember { mutableStateOf<RegistrationUiState?>(null) }
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val reduced = scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    LaunchedEffect(state.scene) {
        if (incoming.scene != state.scene) {
            outgoing = incoming
            incoming = state
            progress.snapTo(0f)
            progress.animateTo(1f, tween(if (reduced) 0 else T.orbitDurationScene, easing = FastOutSlowInEasing))
            outgoing = null
        }
    }
    val interactive = progress.value == 1f && incoming.scene == state.scene && outgoing == null
    Box(Modifier.fillMaxSize().testTag("orbit-phase-${state.phase.name.lowercase()}")) {
      Box(Modifier.fillMaxSize().testTag(if (interactive) "orbit-transition-ready" else "orbit-transition-running")) {
        Box(Modifier.fillMaxSize().then(if (interactive) Modifier else Modifier.clearAndSetSemantics {}).graphicsLayer {
            transformOrigin = TransformOrigin(pivot, .5f)
            rotationZ = orbitKeyframeValue(progress.value, listOf(0f to -40f, .4f to -24f, .7f to -12f, 1f to 0f)) * state.direction
            alpha = orbitKeyframeValue(progress.value, listOf(0f to 0f, .4f to .1f, .7f to .6f, 1f to 1f))
        }) {
            CompositionLocalProvider(LocalOrbitInteractive provides interactive) {
                content(if (incoming.scene == state.scene) state else incoming, interactive)
            }
        }
        outgoing?.let { old ->
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}.graphicsLayer {
                transformOrigin = TransformOrigin(pivot, .5f)
                rotationZ = orbitKeyframeValue(progress.value, listOf(0f to 0f, .5f to 20f, .8f to 32f, 1f to 40f)) * state.direction
                alpha = orbitKeyframeValue(progress.value, listOf(0f to 1f, .5f to .25f, .8f to 0f, 1f to 0f))
            }) { CompositionLocalProvider(LocalOrbitInteractive provides false) { content(old, false) } }
        }
      }
    }
}
