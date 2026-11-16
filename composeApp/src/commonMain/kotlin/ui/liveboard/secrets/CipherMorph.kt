package ui.liveboard.secrets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.clearAndSetSemantics
import ui.design.generated.LiveboardScriptedGeometry
import ui.design.generated.LiveboardTokens as T
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardMetric
import androidx.compose.animation.core.LinearEasing
import ui.liveboard.visual.liveboardScript
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import core.OrbitBackdropBlur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

data class CipherPresentation(val revealed: Boolean, val reduced: Boolean, val epoch: Any, val index: Int,
    val blur: OrbitBackdropBlur)

/** A reveal samples only its own authorized layer; its temporary raster is cleared immediately. */
@Composable
fun CipherMorph(presentation: CipherPresentation, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val spec = LiveboardScriptedGeometry.cipher
    val layer = key(presentation.revealed, presentation.epoch) { rememberGraphicsLayer() }
    val recording = remember(layer, presentation.revealed, presentation.epoch) {
        LiveboardValueRecording(layer, presentation.epoch) }
    val elapsed = remember(presentation.revealed, presentation.epoch) { Animatable(if (presentation.reduced) T
        .liveboardDurationValueReveal.toFloat() else 0f) }
    var seenDisclosure by remember(presentation.epoch) { mutableStateOf(false) }
    var points by remember(presentation.revealed, presentation.epoch) { mutableStateOf(emptyList<Offset>()) }
    var glyphSize by remember(presentation.revealed, presentation.epoch) { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(presentation)
    DisposableEffect(recording) { onDispose { recording.close(); points = emptyList() } }
    LaunchedEffect(presentation.revealed, presentation.epoch, presentation.reduced, recording) {
        if (!presentation.revealed) {
            if (seenDisclosure && !presentation.reduced) elapsed.animateTo(T.liveboardDurationCipherFade.toFloat(),
                tween(T.liveboardDurationCipherFade, easing = LinearEasing))
            seenDisclosure = false
            return@LaunchedEffect
        }
        seenDisclosure = true
        if (presentation.reduced) return@LaunchedEffect
        val capture = recording.capture {
            current.revealed && current.epoch === presentation.epoch
        } ?: return@LaunchedEffect
        val image = capture.image
        try {
            val pixels = image.toPixelMap()
            val sample = LiveboardScriptedGeometry.particleSampling
            val sampled = buildList {
                for (y in sample.originPx until image.height step sample.stepPx) for (x in sample
                    .originPx until image.width step sample.stepPx) {
                    if (pixels[x, y].alpha * ALPHA_BYTE_SCALE >= sample.alphaThreshold) add(Offset(x.toFloat(),
                        y.toFloat()))
                }
            }
            if (!current.revealed || current.epoch !== presentation.epoch || !recording.owns(capture.record))
                return@LaunchedEffect
            points = sampled
        } finally {
            runCatching { ImageCanvas(image).drawRect(Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()
                ), Paint().apply { blendMode = BlendMode.Clear }) }
        }
        elapsed.animateTo(T.liveboardDurationValueReveal.toFloat(), tween(T.liveboardDurationValueReveal, easing =
            LinearEasing))
        points = emptyList()
    }
    Box(modifier.fillMaxWidth().heightIn(min = T.liveboardDimensionSecretMaskHeight)) {
        val valuePose = liveboardScript(LiveboardTimelines.valueReveal, elapsed.value)
        val blurred = rememberLiveboardValueBlur(LiveboardValueBlurRequest(presentation.epoch, presentation
            .revealed, glyphSize,
            with(LocalDensity.current) { valuePose.blur.dp.toPx() }), presentation.blur, layer, recording)
        if (presentation.revealed) Box(Modifier.fillMaxWidth().onSizeChanged {
            glyphSize = it; recording.measured(it)
        }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            recording.recorded()
            if (valuePose.opacity > 0f) drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply {
                alpha = valuePose.opacity })
            if (valuePose.opacity > 0f) {
                if (valuePose.blur > 0f) blurred?.let { drawImage(it.image, topLeft = Offset(-it.padding.toFloat(),
                    -it.padding.toFloat())) }
                else drawLayer(layer)
                drawContext.canvas.restore()
            }
        }) { content() }
        if (!presentation.revealed || elapsed.value < T.liveboardDurationValueReveal) Canvas(Modifier
            .matchParentSize().clearAndSetSemantics { }) {
            val scale = size.width / spec.viewport.width
            val fade = if (presentation.revealed) liveboardScript(LiveboardTimelines.cipherFade, elapsed.value).opacity
                else if (seenDisclosure) liveboardScript(LiveboardTimelines.cipherConcealFade, elapsed.value)
                    .opacity else 1f
            repeat(spec.grid.rows) { row -> repeat(spec.grid.columns) { column ->
                val origin = Offset((spec.origin.x + column * spec.step.x) * scale, spec.origin.y + row * spec.step.y)
                val opacitySpec = spec.appearance.opacity
                val alpha = opacitySpec.base + ((row * opacitySpec.weights.row + column * opacitySpec.weights
                    .column + presentation.index * opacitySpec.weights.index) % opacitySpec.modulo) / opacitySpec
                    .divisor
                drawCircle(T.liveboardColorCipher.copy(alpha = (alpha * fade).coerceIn(0f, 1f)), spec.appearance
                    .radius * scale, origin)
            } }
            if (presentation.revealed && points.isNotEmpty()) {
                points.forEachIndexed { index, target ->
                    val slot = (index.toLong() * spec.grid.rows * spec.grid.columns / points.size).toInt()
                    val origin = Offset((spec.origin.x + slot % spec.grid.columns * spec.step.x) * scale, spec
                        .origin.y + slot / spec.grid.columns * spec.step.y)
                    val particle = liveboardScript(LiveboardTimelines.cipherParticle, elapsed.value,
                        mapOf(LiveboardMetric.MASK_OPACITY to fade, LiveboardMetric.PARTICLE_DX to target.x - origin
                            .x, LiveboardMetric.PARTICLE_DY to target.y - origin.y), index)
                    drawCircle(T.liveboardColorCipher.copy(alpha = (particle.opacity * fade).coerceIn(0f, 1f)), 1f,
                        origin + Offset(particle.translate.first, particle.translate.second))
                }
            }
        }
    }
}

private const val ALPHA_BYTE_SCALE: Int = 255
