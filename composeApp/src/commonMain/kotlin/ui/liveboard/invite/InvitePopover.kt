package ui.liveboard.invite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.liveboard_brand
import org.jetbrains.compose.resources.painterResource
import qrgenerator.qrkitpainter.QrKitCodeShape
import qrgenerator.qrkitpainter.QrKitErrorCorrection
import qrgenerator.qrkitpainter.QrKitOptions
import qrgenerator.qrkitpainter.QrKitShapes
import qrgenerator.qrkitpainter.QrMatrix
import qrgenerator.qrkitpainter.QrPainter
import ui.design.generated.LiveboardInviteFit
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardSurfaceStyle
import ui.liveboard.visual.LiveboardTypography

data class InvitePresentation(val fit: LiveboardInviteFit, val contentAlpha: Float, val surfaceBlur: ImageBitmap?,
    val surfaceOffset: Offset, val active: Boolean = true)

@Composable
fun InvitePopover(vaultName: String, presentation: InvitePresentation, onClose: () -> Unit, modifier: Modifier =
    Modifier) {
    if (vaultName.isBlank()) return
    val code = remember(vaultName) { runCatching {
        var modules: Int? = null
        val defaultShape = QrKitCodeShape.Default
        val observer = object : QrKitCodeShape by defaultShape {
            override fun QrMatrix.transform(): QrMatrix =
                with(defaultShape) { this@transform.transform() }.also { modules = it.dimension }
        }
        val painter = QrPainter(vaultName, QrKitOptions(shapes = QrKitShapes(codeShape = observer),
            errorCorrection = QrKitErrorCorrection.High))
        InviteCode(painter, requireNotNull(modules))
    }.getOrNull() }
    LiveboardSurface(modifier.testTag("lb-invite-popover"), LiveboardSurfaceStyle(LiveboardMaterials.invite, T
        .liveboardDimensionInviteRadius, T.liveboardColorVaultBorder)) {
        presentation.surfaceBlur?.let { image -> Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(T
            .liveboardDimensionInviteRadius))) {
            drawImage(image, topLeft = -presentation.surfaceOffset)
            drawRect(ui.liveboard.visual.liveboardGradient(LiveboardMaterials.invite, size))
        } }
        Box(Modifier.fillMaxSize().drawWithContent { drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(
            Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { alpha = presentation.contentAlpha });
            drawContent(); drawContext.canvas.restore() }) {
            LiveboardDots(appString(AppString.lbInvite), Modifier.align(Alignment.TopCenter).padding(top = 24.dp,
                start = 46.dp, end = 46.dp), height = 13.dp)
            if (code != null) {
                val size = presentation.fit.codeSize.dp
                val modules = code.modules
                val quiet = size * (QUIET_ZONE_MODULES / (modules + TOTAL_QUIET_ZONE_MODULES))
                Box(Modifier.size(size).align(Alignment.Center).background(T.liveboardColorForeground,
                    RoundedCornerShape(12.dp)).testTag("lb-invite-qr"), contentAlignment = Alignment.Center) {
                    Image(code.painter, appString(AppString.orbitQr), Modifier.fillMaxSize().padding(quiet))
                    Box(Modifier.size(size * (FINDER_MODULES / (modules + TOTAL_QUIET_ZONE_MODULES))).background(T
                        .liveboardColorForeground), contentAlignment = Alignment.Center) {
                        Image(painterResource(Res.drawable.liveboard_brand), appString(AppString.orbitBrand),
                            Modifier.size(size * (BRAND_MODULES / (modules + TOTAL_QUIET_ZONE_MODULES))))
                    }
                }
            } else Text(appString(AppString.errorInternal), style = LiveboardTypography.text(color = T
                .liveboardColorError), modifier = Modifier.align(Alignment.Center))
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp,
                bottom = 21.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(appString(AppString.lbScanQr), style = LiveboardTypography.text(T.liveboardFontSizeVaultName),
                    textAlign = TextAlign.Center)
                Text(vaultName, style = LiveboardTypography.text(T.liveboardFontSizeFootCompact, T
                    .liveboardColorMuted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                    .testTag("lb-invite-name"))
            }
        }
        Box(Modifier.size(T.liveboardDimensionControl).align(Alignment.TopEnd).testTag("lb-invite-close").clickable(
            enabled = presentation.active, onClick = onClose), contentAlignment = Alignment.Center) {
            LiveboardIcon("close", Modifier.size(20.dp), moving = false)
        }
    }
}

private data class InviteCode(val painter: QrPainter, val modules: Int)

private const val QUIET_ZONE_MODULES: Float = 4f
private const val TOTAL_QUIET_ZONE_MODULES: Float = 8f
private const val FINDER_MODULES: Float = 7f
private const val BRAND_MODULES: Float = 5.4f
