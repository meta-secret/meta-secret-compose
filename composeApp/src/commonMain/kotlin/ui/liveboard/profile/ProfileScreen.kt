package ui.liveboard.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import core.AppString
import core.appString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.liveboard_brand
import org.jetbrains.compose.resources.painterResource
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.LiveboardRootReset
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardControlSize
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport
import ui.liveboard.visual.LiveboardBackdropRequest
import ui.liveboard.visual.rememberLiveboardBackdrop
import ui.liveboard.visual.liveboardProfileDialogStyle
import ui.liveboard.visual.LiveboardStyleBindings
import ui.liveboard.visual.LiveboardIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    var confirm by remember(state.root.content?.session) { mutableStateOf(false) }
    val running = state.root.chrome.reset == LiveboardRootReset.Running
    val wide = LocalLiveboardViewport.current.width > T.liveboardDimensionProfileColumnsOne.value
    val layer = key(environment.captureEpoch) { rememberGraphicsLayer() }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var dialogOrigin by remember { mutableStateOf(Offset.Unspecified) }
    val backdrop = rememberLiveboardBackdrop(LiveboardBackdropRequest(environment.captureEpoch, 8.dp, confirm,
        bounds), environment.blur, layer)
    val viewport = LocalLiveboardViewport.current
    val compact = !wide
    Column(modifier.fillMaxWidth().testTag("lb-profile").onSizeChanged { bounds = it }.onGloballyPositioned {
        origin = it.positionOnScreen() }
        .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) },
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            LiveboardControl(appString(AppString.orbitBack), "back", actions.navigation.back,
                LiveboardControlOptions(tag = "lb-profile-back", size = LiveboardControlSize(44.dp, 21.dp, caption =
                    false)))
            Column(Modifier.weight(1f)) {
                Text(appString(AppString.lbYourVault), style = LiveboardTypography.label(T.liveboardFontSizeEyebrow
                    ), modifier = Modifier.padding(bottom = 10.dp))
                LiveboardDots(appString(AppString.profile), height = if (compact) 23.dp else 28.dp)
            }
            Image(painterResource(Res.drawable.liveboard_brand), appString(AppString.orbitBrand), Modifier.size(if (
                compact) 26.dp else 33.dp, if (compact) 30.dp else 38.dp))
        }
        ProfileIdentity(state)
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProfileAbout(environment, Modifier.weight(1f))
            ProfileReset({ confirm = true }, Modifier.weight(1f))
        } else {
            ProfileAbout(environment)
            ProfileReset({ confirm = true })
        }
        if (state.root.chrome.reset == LiveboardRootReset.Failed) Text(appString(AppString.orbitResetFailed),
            style = LiveboardTypography.text(color = T.liveboardColorError), modifier = Modifier.testTag(
                "lb-profile-reset-failed"))
    }
    if (confirm) Dialog(onDismissRequest = { if (!running) confirm = false }, properties = DialogProperties(
        usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(T.liveboardColorBackground).onGloballyPositioned {
            dialogOrigin = it.positionOnScreen() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val location = origin - dialogOrigin
                if (origin != Offset.Unspecified && dialogOrigin != Offset.Unspecified && location.x.isFinite() &&
                    location.y.isFinite()) {
                    backdrop?.let { drawImage(it, topLeft = location) }
                }
            }
            Box(Modifier.matchParentSize().background(LiveboardStyleBindings.color(
                ".profile-reset-dialog::backdrop", "background", viewport, T.liveboardColorCreateBackdrop)))
            LiveboardSurface(Modifier.widthIn(max = minOf(330.dp, maxWidth - 32.dp)).fillMaxWidth().heightIn(max =
                maxHeight - 32.dp).testTag("lb-reset-confirmation"), liveboardProfileDialogStyle(viewport)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 25.dp, top = 26.dp, end = 25
                    .dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LiveboardIcon("reset", Modifier.size(31.dp), T.liveboardColorError, moving = false)
                    Text(appString(AppString.lbReset), style = LiveboardTypography.heading(21.sp), modifier =
                        Modifier.padding(top = 18.dp, bottom = 13.dp))
                    Text(appString(AppString.lbResetConfirm), style = LiveboardTypography.text(12.sp, T
                        .liveboardColorMuted).copy(lineHeight = 21.6.sp), textAlign = androidx.compose.ui.text.style
                        .TextAlign.Center)
                    Row(Modifier.fillMaxWidth().padding(top = 25.dp), horizontalArrangement = Arrangement.spacedBy(
                        10.dp)) {
                        ResetButton(appString(AppString.cancel), !running, { confirm = false }, Modifier.weight(1f)
                            .testTag("lb-reset-cancel"))
                        ResetButton(appString(AppString.lbReset), !running, actions.vault.resetProfile, Modifier
                            .weight(1f).testTag("lb-reset-submit"), destructive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    destructive: Boolean = false) {
    val viewport = LocalLiveboardViewport.current
    val selector = if (destructive) "#profile-reset-confirm" else ".profile-reset-actions button"
    val baseBorder = LiveboardStyleBindings.color(".profile-reset-actions button", "border", viewport, T
        .liveboardColorControlBorder)
    Box(modifier.heightIn(min = 40.dp).border(1.dp, LiveboardStyleBindings.color(selector, "border-color", viewport,
        baseBorder), RoundedCornerShape(10.dp))
        .background(LiveboardStyleBindings.color(selector, "background", viewport, androidx.compose.ui.graphics
            .Color.Transparent), RoundedCornerShape(10.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp, horizontal = 4
            .dp), contentAlignment = Alignment.Center) {
        Text(label, style = LiveboardTypography.text(12.sp, LiveboardStyleBindings.color(selector, "color",
            viewport, T.liveboardColorDevice)))
    }
}
