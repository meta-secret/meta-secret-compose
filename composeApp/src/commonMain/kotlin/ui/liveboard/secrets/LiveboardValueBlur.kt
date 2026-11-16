package ui.liveboard.secrets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import core.OrbitBackdropBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.ceil

data class LiveboardValueBlurRequest(val epoch: Any, val shown: Boolean, val size: IntSize, val radiusPx: Float)
class LiveboardValueBlurFrame(val image: ImageBitmap, val padding: Int, val radiusPx: Float, val epoch: Any,
    val size: IntSize)
private class ValueBlurLifetime { var active = true }

internal class LiveboardValueRecord(val owner: LiveboardValueRecording, val size: IntSize)
internal class LiveboardValueCapture(val record: LiveboardValueRecord, val image: ImageBitmap)

/** Layout/frame publication is not recording: only the completed draw publishes a receipt. */
class LiveboardValueRecording internal constructor(private val layer: GraphicsLayer, private val epoch: Any) {
    private var active by mutableStateOf(true)
    private var expectedSize by mutableStateOf(IntSize.Zero)
    private var recorded by mutableStateOf<LiveboardValueRecord?>(null)

    internal fun matches(epoch: Any, layer: GraphicsLayer): Boolean = this.epoch === epoch && this.layer === layer

    internal fun measured(size: IntSize) {
        if (active && expectedSize != size) { expectedSize = size; recorded = null }
    }

    internal fun recorded() {
        if (!active || layer.isReleased) { recorded = null; return }
        if (expectedSize.width <= 0 || expectedSize.height <= 0 || layer.size != expectedSize) {
            recorded = null; return
        }
        if (recorded?.size != expectedSize) recorded = LiveboardValueRecord(this, expectedSize)
    }

    internal fun current(size: IntSize? = null): LiveboardValueRecord? = recorded?.takeIf {
        active && it.owner === this && it.size == expectedSize && (size == null || it.size == size) &&
            it.size.width > 0 && it.size.height > 0 && !layer.isReleased && layer.size == it.size
    }

    internal fun owns(record: LiveboardValueRecord): Boolean = current(record.size) === record

    internal suspend fun capture(size: IntSize? = null, isCurrent: () -> Boolean): LiveboardValueCapture? =
        snapshotFlow {
            val running = active && isCurrent()
            running to if (running) current(size) else null
        }.map { (running, record) ->
            if (!running) return@map false to null
            if (record == null || !owns(record) || !isCurrent()) return@map (active && isCurrent()) to null
            val image = layer.toImageBitmap()
            var retained = false
            try {
                if (owns(record) && isCurrent()) {
                    retained = true
                    true to LiveboardValueCapture(record, image)
                } else (active && isCurrent()) to null
            } finally { if (!retained) image.eraseValuePixels() }
        }.first { !it.first || it.second != null }.second

    internal fun close() { active = false; recorded = null; expectedSize = IntSize.Zero }
}

/** Actual authorized glyph pixels only; CPU/Skia works where native RenderEffect is unsupported. */
@Composable
fun rememberLiveboardValueBlur(request: LiveboardValueBlurRequest, blur: OrbitBackdropBlur, layer: GraphicsLayer,
    recording: LiveboardValueRecording
    ): LiveboardValueBlurFrame? {
    var output by remember(request.epoch, request.shown, request.size, layer) {
        mutableStateOf<LiveboardValueBlurFrame?>(null) }
    val lifetime = remember(request.epoch, request.shown, request.size, layer) { ValueBlurLifetime() }
    val latestLifetime by rememberUpdatedState(lifetime)
    val current by rememberUpdatedState(request)
    DisposableEffect(lifetime) { onDispose { lifetime.active = false; output?.image?.eraseValuePixels(); output =
        null } }
    LaunchedEffect(request.epoch, request.shown, request.size, layer, recording) {
        if (!request.shown || request.size.width <= 0 || request.size.height <= 0) return@LaunchedEffect
        if (!recording.matches(request.epoch, layer)) return@LaunchedEffect
        // One blur runs to completion; only the newest waiting radius is kept. Radius changes
        // cannot repeatedly cancel useful work or blank an already completed, owned glyph frame.
        snapshotFlow { current.radiusPx to recording.current(request.size) }.conflate().collect { (radius, record) ->
            if (!lifetime.active || latestLifetime !== lifetime || !current.shown || current.epoch !== request
                .epoch || current.size != request.size || radius <= 0f || record == null) {
                output?.image?.eraseValuePixels(); output = null; return@collect
            }
            val capture = recording.capture(request.size) {
                lifetime.active && latestLifetime === lifetime && current.shown && current.epoch === request
                    .epoch && current.size == request.size && current.radiusPx > 0f
            } ?: return@collect
            val captured = capture.image
            val padding = ceil(radius * BLUR_PADDING_RADIUS_MULTIPLIER).toInt().coerceAtLeast(1)
            val source = try { blur.paddedCopy(captured, padding) } finally {
                // A platform-owned immutable screenshot is never retained beyond this copy.
                captured.eraseValuePixels()
            }
            val result = try { blur.blur(source, radius) } catch (cancelled: CancellationException) {
                throw cancelled
            } finally { source.eraseValuePixels() }
            if (lifetime.active && latestLifetime === lifetime && current.shown && current.epoch === request
                .epoch && current.size == request.size && current.radiusPx > 0f && recording.owns(capture.record)) {
                output?.image?.eraseValuePixels()
                output = LiveboardValueBlurFrame(result, padding, radius, request.epoch, request.size)
            } else result.eraseValuePixels()
        }
    }
    return output?.takeIf { lifetime.active && request.shown && it.epoch === request.epoch && it.size == request
        .size && request.radiusPx > 0f && recording.current(request.size) != null }
}

private fun ImageBitmap.eraseValuePixels() {
    // Hardware screenshots may be immutable; callers keep only their owned mutable copy/output.
    runCatching { Canvas(this).drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { blendMode =
        BlendMode.Clear }) }
}

private const val BLUR_PADDING_RADIUS_MULTIPLIER: Float = 3f
