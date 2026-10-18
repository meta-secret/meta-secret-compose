package ui.scenes.registration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import kotlinproject.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import ui.design.generated.OrbitTokens as T
import ui.theme.OrbitTextStyles

internal enum class OrbitGlyph { Apple, Google, Create, Join, Enter, Back, Ok, Qr, Request, Fusion, Approved }
internal data class OrbitActionAppearance(val glyphOpacity: Float = 1f, val labelOpacity: Float = 1f, val interactive: Boolean = true)
internal data class OrbitAction(val glyph: OrbitGlyph, val label: AppString, val enabled: Boolean = true,
    val labelVisible: Boolean = true, val appearance: OrbitActionAppearance = OrbitActionAppearance())

@Composable
internal fun OrbitActionButton(action: OrbitAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "action pulse").animateFloat(.8f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 2000; .8f at 0; 1.18f at 400; 1f at 1400; .8f at 2000 }), label = "create pulse")
    val interactive = LocalOrbitInteractive.current
    val color = T.orbitColorControl
    val opacity = if (!action.enabled) T.orbitNumberDisabledOpacity else if (action.glyph in setOf(OrbitGlyph.Apple, OrbitGlyph.Google)) T.orbitNumberProviderOpacity else 1f
    Column(modifier.size(width = T.orbitDimensionControlTarget, height = 72.dp)
        .graphicsLayer { alpha = opacity }.testTag("orbit-${action.glyph.name.lowercase()}")
        .clickable(enabled = action.enabled && interactive && action.appearance.interactive, onClickLabel = appString(action.label), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(T.orbitDimensionControlLabelGap)) {
        Box(Modifier.size(T.orbitDimensionControlCircle).border(1.dp, T.orbitColorControlBorder, CircleShape), contentAlignment = Alignment.Center) {
            if (action.glyph == OrbitGlyph.Google) {
                Text("G", style = OrbitTextStyles.heading(20f), color = color)
            } else OrbitAnimatedArt(action, Modifier
                    .size(if (action.glyph == OrbitGlyph.Create) T.orbitDimensionCreateGlyph else T.orbitDimensionGlyph)
                    .graphicsLayer { alpha = action.appearance.glyphOpacity; if (action.glyph == OrbitGlyph.Create && action.enabled) { scaleX = pulse.value; scaleY = pulse.value } })
        }
        if (action.labelVisible) Text(appString(action.label), style = OrbitTextStyles.label(),
            modifier = Modifier.graphicsLayer { alpha = action.appearance.labelOpacity })
    }
}

@Composable
internal fun OrbitActionRow(actions: List<OrbitAction>, onClick: (OrbitGlyph) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(T.orbitDimensionControlGap)) {
        actions.forEach { action -> OrbitActionButton(action, { onClick(action.glyph) }) }
    }
}

@Composable
internal fun OrbitJoinActions(state: RegistrationUiState, onClick: (OrbitGlyph) -> Unit, modifier: Modifier = Modifier) {
    val waiting = state.phase in setOf(RegistrationPhase.Requesting, RegistrationPhase.Pending, RegistrationPhase.Syncing)
    val merge = remember { Animatable(0f) }
    LaunchedEffect(waiting) {
        if (waiting) merge.animateTo(1f, tween(T.orbitDurationJoinFusion, easing = LinearEasing)) else merge.snapTo(0f)
    }
    val pose = orbitFusionPose(merge.value)
    val outer = OrbitActionAppearance(labelOpacity = pose.opacity.label, interactive = !waiting)
    val terminal = state.phase in setOf(RegistrationPhase.Declined, RegistrationPhase.Error)
    Box(modifier.size(180.dp, 72.dp)) {
        if (pose.opacity.side > 0f && !terminal) {
            OrbitActionButton(OrbitAction(OrbitGlyph.Qr, AppString.orbitQr, appearance = outer),
                { onClick(OrbitGlyph.Qr) }, Modifier.graphicsLayer {
                    translationX = (66.dp * pose.travel).toPx(); alpha = pose.opacity.side
                })
            OrbitActionButton(OrbitAction(OrbitGlyph.Request, AppString.orbitRequest, appearance = outer),
                { onClick(OrbitGlyph.Request) }, Modifier.align(Alignment.TopEnd).graphicsLayer {
                    translationX = -(66.dp * pose.travel).toPx(); alpha = pose.opacity.side
                })
        }
        Box(Modifier.align(Alignment.TopCenter).graphicsLayer { scaleX = pose.scaleX; scaleY = pose.scaleY }) {
            val approved = state.phase == RegistrationPhase.Syncing
            OrbitActionButton(OrbitAction(if (approved) OrbitGlyph.Approved else OrbitGlyph.Back,
                if (approved) AppString.orbitApproved else AppString.orbitBack, appearance = OrbitActionAppearance(pose.opacity.back, pose.opacity.back,
                    !waiting || (state.phase == RegistrationPhase.Pending && merge.value >= .96f))),
                { onClick(OrbitGlyph.Back) })
            if (pose.opacity.dots > 0f) Box(Modifier.size(T.orbitDimensionControlTarget, T.orbitDimensionControlCircle),
                contentAlignment = Alignment.Center) {
                OrbitAnimatedArt(OrbitAction(OrbitGlyph.Fusion, AppString.orbitBack),
                    Modifier.size(T.orbitDimensionGlyph).graphicsLayer { alpha = pose.opacity.dots }.clearAndSetSemantics {})
            }
        }
    }
}

internal fun OrbitGlyph.resource(): DrawableResource = when (this) {
    OrbitGlyph.Apple -> Res.drawable.orbit_apple
    OrbitGlyph.Google, OrbitGlyph.Enter -> Res.drawable.orbit_enter
    OrbitGlyph.Create -> Res.drawable.orbit_create
    OrbitGlyph.Join -> Res.drawable.orbit_join
    OrbitGlyph.Back -> Res.drawable.orbit_back
    OrbitGlyph.Ok -> Res.drawable.orbit_ok
    OrbitGlyph.Qr -> Res.drawable.orbit_qr
    OrbitGlyph.Request -> Res.drawable.orbit_request
    OrbitGlyph.Fusion -> Res.drawable.orbit_fusion
    OrbitGlyph.Approved -> Res.drawable.orbit_approved
}

@Composable
internal fun OrbitWithdrawingActions(modifier: Modifier = Modifier) {
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) { fade.animateTo(0f, tween(T.orbitDurationButtonsFade)) }
    OrbitActionRow(listOf(OrbitAction(OrbitGlyph.Create, AppString.orbitCreate),
        OrbitAction(OrbitGlyph.Join, AppString.orbitJoin), OrbitAction(OrbitGlyph.Enter, AppString.orbitEnter)), {},
        modifier.graphicsLayer { alpha = fade.value }.clearAndSetSemantics {})
}
