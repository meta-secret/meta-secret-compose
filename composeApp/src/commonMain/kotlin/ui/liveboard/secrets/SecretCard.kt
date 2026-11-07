package ui.liveboard.secrets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import ui.design.generated.LiveboardTimelines
import ui.liveboard.visual.liveboardScript
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import core.metaSecretCore.liveboard.LiveboardActionTarget
import models.apiModels.liveboard.LiveboardSecret
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardControlSize
import ui.liveboard.visual.LiveboardControlPalette
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LiveboardSurfaceInteraction

@Composable
fun SecretCard(secret: LiveboardSecret, state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, index: Int) {
    val id = secret.identity.passId
    val disclosure = state.sensitive?.revealed?.get(id)
    val value = disclosure?.value
    val deleting = secret.deletionRequestId != null || state.activity.actions.any { it.target ==
        LiveboardActionTarget.Delete(id) }
    val recoveredReady = secret.primary.action in setOf("showLocal", "showRecovered") && secret.primary.readiness ==
        "ready"
    // Native producer truth can remain Unknown after return. Only current Core ready permits Show;
    // the callback remains the values command, so this never replays that retained Recover producer.
    val recovering = !recoveredReady && (secret.primary.action == "waiting" ||
        state.activity.actions.any { it.target == LiveboardActionTarget.Recover(id) })
    val ready = state.inbox?.ready == true
    val enabled = ready && secret.primary.readiness == "ready" && !deleting && !recovering
    val isRecovery = secret.primary.action == "recover"
    val size = LiveboardControlSize(T.liveboardDimensionSecretControl, T.liveboardDimensionSecretIcon,
        LiveboardControlPalette.Secret)
    LiveboardSurface(Modifier.fillMaxWidth().testTag("lb-secret-$id").animateContentSize(tween(if (environment
        .reducedMotion) 0 else T.liveboardDurationSecretHeight,
        easing = androidx.compose.animation.core.CubicBezierEasing(HEIGHT_EASING_X1, HEIGHT_EASING_Y1,
            HEIGHT_EASING_X2, 1f))), interaction = LiveboardSurfaceInteraction(moves = false)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment =
                Alignment.CenterVertically) {
                LiveboardDots((index + 1).toString().padStart(2, '0'), height = 10.dp, color = T
                    .liveboardColorStorageRule)
                LiveboardIcon("lock", Modifier.size(16.dp), T.liveboardColorStorageRule, moving = false)
            }
            Text(secret.identity.name, style = LiveboardTypography.heading(T.liveboardFontSizeSecretSheetName),
                modifier = Modifier.padding(top = 18.dp).testTag("lb-secret-name-$id"))
            key(id, environment.captureEpoch) {
                CipherMorph(CipherPresentation(value != null && !deleting, environment.reducedMotion, environment
                    .captureEpoch, index, environment.blur), Modifier.padding(top = T
                    .liveboardDimensionSecretValueMarginTop)) {
                    if (value != null && !deleting) {
                        SecretValue(secret.identity, value)
                        val ticket = disclosure?.showObservation
                        val emit = environment.showObservationEmitter
                        if (ticket != null && emit != null) SideEffect { emit(ticket) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(T
                .liveboardDimensionSecretActionsGap)) {
                LiveboardControl(appString(when { value != null -> AppString.lbHide; isRecovery -> AppString
                    .recover; recovering -> AppString.lbPending; else -> AppString.lbShow }),
                    if (value != null) "hide" else "show", {
                        when { value != null -> actions.secrets.values.hide(id); isRecovery -> actions.secrets
                            .commands.recover(id); else -> actions.secrets.values.show(id) }
                    }, LiveboardControlOptions((value != null && !deleting) || (enabled && secret.primary.action in
                        setOf("showLocal", "showRecovered", "recover")), pending = value == null && recovering, tag =
                        "lb-primary-$id", size = size))
                LiveboardControl(appString(AppString.lbDelete), "delete", { actions.secrets.commands.delete(id) },
                    LiveboardControlOptions(ready && secret.canDelete && !deleting && !recovering, destructive =
                        true, pending = deleting, tag = "lb-delete-$id", size = size))
                if (value != null && !deleting) CopyControl(id, ready, actions, environment)
            }
        }
    }
}

@Composable
private fun CopyControl(id: String, ready: Boolean, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment) {
    val elapsed = remember(environment.captureEpoch) { Animatable(if (environment.reducedMotion
        ) REVEAL_DURATION_FLOAT else 0f) }
    LaunchedEffect(environment.captureEpoch, environment.reducedMotion) {
        if (!environment.reducedMotion) elapsed.animateTo(REVEAL_DURATION_FLOAT, tween(REVEAL_DURATION_MILLIS,
            easing = LinearEasing))
    }
    val pose = liveboardScript(LiveboardTimelines.copyReveal, elapsed.value)
    LiveboardControl(appString(AppString.lbCopy), "copy", { actions.secrets.values.copy(id) },
        LiveboardControlOptions(ready && pose.opacity > 0f, tag = "lb-copy-$id", size = LiveboardControlSize(T
            .liveboardDimensionSecretControl, T.liveboardDimensionSecretIcon, LiveboardControlPalette.Secret)),
        Modifier.graphicsLayer { alpha = pose.opacity; scaleX = pose.scale; scaleY = pose.scale; translationX = pose
            .translate.first.dp.toPx() })
}

private const val HEIGHT_EASING_X1: Float = .22f
private const val HEIGHT_EASING_Y1: Float = .8f
private const val HEIGHT_EASING_X2: Float = .24f
private const val REVEAL_DURATION_FLOAT: Float = 780f
private const val REVEAL_DURATION_MILLIS: Int = 780
