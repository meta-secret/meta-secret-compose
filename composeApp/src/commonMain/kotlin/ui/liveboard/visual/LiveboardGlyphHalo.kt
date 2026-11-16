package ui.liveboard.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ui.design.generated.LiveboardGeometry
import ui.design.generated.LiveboardMotion
import ui.liveboard.LiveboardRenderEnvironment
import kotlin.math.ceil

private data class HaloFrame(val epoch: Any, val size: IntSize, val strength: Float)

/** Only exported device dots are rasterized: this cannot capture any text or a foreground layer. */
@Composable
fun LiveboardGlyphHalo(name: String, strength: Float, environment: LiveboardRenderEnvironment, modifier: Modifier =
    Modifier, content: @Composable BoxScope.() -> Unit) {
    val art = LiveboardGeometry.art[name] ?: return
    val exported = remember { LiveboardMotion.frames["recovery-device-glow"].orEmpty().firstNotNullOfOrNull { it
        .properties["filter"] } }
    val filter = remember(exported) { Regex("drop-shadow\\(0 0 ([\\d.]+)px (#[0-9a-fA-F]+)\\)").find(exported
        .orEmpty()) }
    val maximum = filter?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    val ink = filter?.groupValues?.get(2)?.let { liveboardColor(it) } ?: androidx.compose.ui.graphics.Color.Transparent
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(LocalDensity.current) { ceil((maximum * HALO_PADDING_MULTIPLIER + 1f).dp.toPx()).toInt() }
    val radius = with(LocalDensity.current) { maximum.dp.toPx() * strength }
    var output by remember(environment.captureEpoch, name, size) { mutableStateOf<ImageBitmap?>(null) }
    val request = HaloFrame(environment.captureEpoch, size, strength)
    val current by rememberUpdatedState(request)
    DisposableEffect(environment.captureEpoch, name, size) { onDispose { output?.eraseHalo(); output = null } }
    LaunchedEffect(environment.captureEpoch, name, size, strength) {
        if (strength <= 0f || size == IntSize.Zero) { output?.eraseHalo(); output = null; return@LaunchedEffect }
        val source = ImageBitmap(size.width + padding * 2, size.height + padding * 2)
        val canvas = ImageCanvas(source)
        val scale = minOf(size.width / art.viewBox[2], size.height / art.viewBox[VIEW_BOX_HEIGHT_INDEX])
        val origin = Offset(padding + (size.width - art.viewBox[2] * scale) / 2, padding + (size.height - art
            .viewBox[VIEW_BOX_HEIGHT_INDEX] * scale) / 2)
        val paint = Paint().apply { color = ink.copy(alpha = ink.alpha * strength) }
        art.dots.forEach { dot -> canvas.drawCircle(origin + Offset(dot.x - art.viewBox[0], dot.y - art.viewBox[1]
            ) * scale, dot.radius * scale, paint) }
        val blurred = try { environment.blur.blur(source, radius) } catch (cancelled: CancellationException) {
            throw cancelled
        } finally { source.eraseHalo() }
        if (current.epoch === request.epoch && current.size == request.size && current.strength == request.strength) {
            output?.eraseHalo(); output = blurred
        } else blurred.eraseHalo()
    }
    Box(modifier.onSizeChanged { size = it }) {
        Canvas(Modifier.matchParentSize()) { output?.let { drawImage(it, topLeft = Offset(-padding.toFloat(),
            -padding.toFloat())) } }
        content()
    }
}

private fun ImageBitmap.eraseHalo() {
    ImageCanvas(this).drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { blendMode =
        BlendMode.Clear })
}

private const val HALO_PADDING_MULTIPLIER: Float = 3f
private const val VIEW_BOX_HEIGHT_INDEX: Int = 3
