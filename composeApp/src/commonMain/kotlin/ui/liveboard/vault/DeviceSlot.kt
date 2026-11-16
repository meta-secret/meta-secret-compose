package ui.liveboard.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import models.apiModels.DeviceUiCategory
import models.apiModels.liveboard.LiveboardDevice
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardArt
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport
import ui.design.generated.LiveboardScriptedGeometry
import ui.liveboard.visual.liveboardPose
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.visual.LiveboardGlyphHalo

data class DeviceSlotStatus(val text: String, val requester: Boolean, val outcome: String?)

data class DeviceSlotContent(
    val index: Int, val device: LiveboardDevice?, val preview: Boolean = false,
    val removal: Boolean = false, val status: DeviceSlotStatus? = null,
)

@Composable
fun DeviceSlot(content: DeviceSlotContent, onClick: () -> Unit, onAnchor: (androidx.compose.ui.geometry.Offset) ->
    Unit, environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    val viewport = LocalLiveboardViewport.current
    val device = content.device
    val width = when { viewport.expanded -> T.liveboardDimensionDeviceWideWidth; viewport.wide -> T
        .liveboardDimensionDeviceMediumWidth
        viewport.compact -> T.liveboardDimensionDeviceCompactWidth; else -> T.liveboardDimensionDeviceWidth }
    val height = when { viewport.expanded -> T.liveboardDimensionDeviceWideHeight; viewport.wide -> T
        .liveboardDimensionDeviceMediumHeight
        viewport.compact -> T.liveboardDimensionDeviceCompactHeight; else -> T.liveboardDimensionDeviceHeight }
    val inactive = device == null && !content.preview
    val glow = liveboardPose("recovery-device-glow", content.status?.requester == true && content.status.outcome ==
        null)
    val ink = when { content.status?.requester == true -> T.liveboardColorNeutralAction; inactive -> T
        .liveboardColorInactiveDevice; content.preview -> T.liveboardColorPreviewDevice; else -> T
        .liveboardColorDevice }
    val name = device?.name ?: appString(if (content.preview) AppString.lbIfConnected else AppString.lbNotConnected)
    val status = content.status?.text ?: when { device != null -> appString(AppString.lbConnected); content
        .preview -> appString(AppString.lbPreview); else -> "" }
    Column(modifier.testTag("lb-device-${device?.deviceId ?: content.index}")
        .background(if (content.preview) T.liveboardColorPreview.copy(alpha = .03f) else androidx.compose.ui
            .graphics.Color.Transparent, RoundedCornerShape(5.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = if (viewport.compact) 5.dp else 8.dp,
            vertical = if (viewport.wide) 17.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        Text(if (device?.current == true) appString(AppString.lbThisDevice
            ) else "${appString(AppString.device).uppercase()} ${(content.index + 1).toString().padStart(2, '0')}",
            style = LiveboardTypography.label(if (viewport.compact) T.liveboardFontSizeSlotHeadingCompact else T
                .liveboardFontSizeSlotHeading),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val art = when (device?.category) {
            DeviceUiCategory.Tablet -> "tablet"
            DeviceUiCategory.Desktop, DeviceUiCategory.Cli, DeviceUiCategory.Web -> "laptop"
            null -> "placeholder"
            else -> "phone"
        }
        LiveboardGlyphHalo(art, ((glow.opacity - GLYPH_GLOW_BASE) / GLYPH_GLOW_RANGE).coerceIn(0f, 1f).takeIf {
            content.status?.requester == true && content.status.outcome == null && !environment.reducedMotion } ?: 0f,
            environment, Modifier.padding(top = if (viewport.wide) 27.dp else 20.dp, bottom = if (viewport.wide) 24
                .dp else 17.dp)
            .width(width).height(height).alpha(if (content.removal) T.liveboardNumberRemovedOpacity else if (
                inactive) T.liveboardNumberInactiveOpacity else glow.opacity)
            .onGloballyPositioned { coordinates -> val rect = coordinates.boundsInRoot(); onAnchor(androidx.compose
                .ui.geometry.Offset(rect.center.x, rect.top + rect.height * LiveboardScriptedGeometry.requestPath
                .anchorFractionY)) }) {
                LiveboardArt(art, androidx.compose.ui.Modifier.fillMaxWidth().height(height), ink)
            }
        Text(name, style = LiveboardTypography.text(if (viewport.compact) T
            .liveboardFontSizeDeviceCompactName else T.liveboardFontSizeDeviceName,
            if (content.preview) T.liveboardColorPreview else if (inactive) T.liveboardColorMuted else T
                .liveboardColorDeviceName),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = if (viewport.compact) 29
                .dp else 36.dp).alpha(if (content.removal) REMOVING_LABEL_ALPHA else 1f))
        Text(status, style = LiveboardTypography.text(if (viewport.compact) T
            .liveboardFontSizeDeviceCompactState else T.liveboardFontSizeDeviceState,
            when { content.status?.outcome == "accepted" -> T.liveboardColorApproved; content.status?.outcome ==
                "declined" -> T.liveboardColorError;
                content.preview || content.removal -> T.liveboardColorPreview; else -> T.liveboardColorDeviceState }),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 5.dp).heightIn(min = 23.dp))
    }
}

private const val GLYPH_GLOW_BASE: Float = .7f
private const val GLYPH_GLOW_RANGE: Float = .3f
private const val REMOVING_LABEL_ALPHA: Float = .28f
