package ui.liveboard.visual

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import ui.design.generated.LiveboardMotion
import ui.design.generated.LiveboardTokens as T
import ui.design.generated.LiveboardTimeline
import ui.design.generated.LiveboardScalar
import ui.design.generated.LiveboardMetric
import ui.design.generated.LiveboardDuration
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

private val LocalMotionTime = staticCompositionLocalOf { 0f }
val LocalLiveboardReducedMotion = staticCompositionLocalOf { true }

/** One frame clock drives every exported icon part without per-icon polling or timers. */
@Composable
fun LiveboardMotionClock(reduced: Boolean, content: @Composable () -> Unit) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        elapsed = 0f
        if (!reduced) {
            var origin = 0L
            while (true) withInfiniteAnimationFrameNanos { frame ->
                if (origin == 0L) origin = frame
                elapsed = (frame - origin) / NANOS_PER_MILLI_FLOAT
            }
        }
    }
    CompositionLocalProvider(LocalMotionTime provides elapsed, LocalLiveboardReducedMotion provides reduced,
        content = content)
}

data class LiveboardPose(
    val translate: Pair<Float, Float> = 0f to 0f,
    val scale: Pair<Float, Float> = 1f to 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val trace: Pair<Float, List<Float>> = 0f to emptyList(),
)

private val durations = mapOf(
    "recovery-device-glow" to T.liveboardDurationRequesterGlow,
    "device-current-turn" to T.liveboardDurationCurrentTurn, "device-current-trace" to T.liveboardDurationCurrentTurn,
    "device-bin-tip" to T.liveboardDurationDeviceDelete, "device-bin-mark" to T.liveboardDurationDeviceDelete,
    "device-clock-tick" to T.liveboardDurationDeviceClock, "notification-wash" to T.liveboardDurationNoticeWash,
    "notification-appear" to T.liveboardDurationNoticeAppear, "notification-check" to T.liveboardDurationNoticeGlyph,
    "notification-cross" to T.liveboardDurationNoticeGlyph, "notification-loading" to T.liveboardDurationNoticeLoader,
    "secret-bin-lid" to T.liveboardDurationSecretDelete, "secret-bin-cross" to T.liveboardDurationSecretDelete,
    "secret-key-turn" to T.liveboardDurationSecretKey, "secret-key-slash" to T.liveboardDurationSecretKey,
    "secret-clock-turn" to T.liveboardDurationSecretClock, "secret-copy-expand" to T.liveboardDurationCopy,
    "create-cancel-step" to T.liveboardDurationCancel, "create-plus-breathe" to T.liveboardDurationCreate,
    "create-loader-wave" to T.liveboardDurationCreateLoader,
)

@Composable
fun liveboardCycle(duration: Int, index: Int = 0, startKey: Any? = null): Float {
    val time = LocalMotionTime.current
    val origin = remember(startKey) { if (startKey == null) 0f else time }
    return if (LocalLiveboardReducedMotion.current) 0f else ((time - origin + index * LOOP_INDEX_STAGGER) % duration
        ) / duration
}

@Composable
fun liveboardPose(animation: String?, enabled: Boolean = true, index: Int = 0, delayMs: Int = 0, startKey: Any? =
    null): LiveboardPose {
    if (animation == null || !enabled || LocalLiveboardReducedMotion.current) return LiveboardPose()
    val frames = LiveboardMotion.frames[animation].orEmpty()
    if (frames.isEmpty()) return LiveboardPose()
    val duration = durations[animation] ?: return LiveboardPose()
    val now = LocalMotionTime.current
    val origin = remember(animation, enabled, index, startKey) { now }
    val delayed = (now - origin + index * LOOP_INDEX_STAGGER - delayMs).coerceAtLeast(0f)
    val cycle = delayed / duration
    val fraction = if (animation == "notification-loading" && cycle.toInt() % 2 == 1) 1f - cycle % 1f else cycle % 1f
    val before = frames.lastOrNull { it.offset <= fraction }
    val after = frames.firstOrNull { it.offset > fraction } ?: frames.last()
    val start = before?.offset ?: 0f
    var progress = if (after.offset == start) 1f else ((fraction - start) / (after.offset - start)).coerceIn(0f, 1f)
    if (animation !in setOf("secret-clock-turn", "device-clock-tick", "create-plus-breathe")) {
        progress = (if (animation in setOf("notification-check", "notification-cross", "notification-appear"))
            CubicBezierEasing(EASE_CONTROL_X, EASE_CONTROL_Y, EASE_CONTROL_X, 1f) else CubicBezierEasing(
                EASE_IN_CONTROL_X, 0f, EASE_OUT_CONTROL_X, 1f)).transform(progress)
    }
    return interpolate(pose(before?.properties.orEmpty()), pose(after.properties), progress)
}

private fun pose(properties: Map<String, String>): LiveboardPose {
    val transform = properties["transform"].orEmpty()
    fun number(pattern: String, fallback: Float) = Regex(pattern).find(transform)?.groupValues?.get(1)
        ?.toFloatOrNull() ?: fallback
    val x = number("translateX\\(([-.\\d]+)", number("translate\\(([-.\\d]+)", 0f))
    val y = number("translateY\\(([-.\\d]+)", Regex("translate\\([-.\\d]+px?,?\\s*([-.\\d]+)").find(transform)
        ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f)
    val scale = number("scale\\(([-.\\d]+)", 1f)
    val rotationY = number("rotateY\\(([-.\\d]+)", 0f)
    val sx = scale * kotlin.math.cos(rotationY * kotlin.math.PI.toFloat() / HALF_TURN_DEGREES)
    val sy = number("scaleY\\(([-.\\d]+)", scale)
    val dash = properties["stroke-dasharray"]?.split(' ')?.mapNotNull { it.toFloatOrNull() }.orEmpty()
    return LiveboardPose(x to y, sx to sy, number("rotate\\(([-.\\d]+)", 0f),
        properties["opacity"]?.toFloatOrNull() ?: 1f, (properties["stroke-dashoffset"]?.toFloatOrNull() ?: 0f) to dash)
}

private fun interpolate(a: LiveboardPose, b: LiveboardPose, t: Float): LiveboardPose {
    fun mix(x: Float, y: Float) = x + (y - x) * t
    return LiveboardPose(mix(a.translate.first, b.translate.first) to mix(a.translate.second, b.translate.second),
        mix(a.scale.first, b.scale.first) to mix(a.scale.second, b.scale.second), mix(a.rotation, b.rotation),
        mix(a.opacity, b.opacity), mix(a.trace.first, b.trace.first) to if (t < TRACE_SWITCH_FRACTION) a.trace
            .second else b.trace.second)
}

data class LiveboardScriptPose(
    val opacity: Float = 1f, val translate: Pair<Float, Float> = 0f to 0f,
    val scale: Float = 1f, val blur: Float = 0f, val bounds: List<Float?> = listOf(null, null, null),
)

fun liveboardEasing(value: String): Easing = when (value) {
    "linear" -> LinearEasing
    "ease" -> CubicBezierEasing(EASE_CONTROL_X, EASE_CONTROL_Y, EASE_CONTROL_X, 1f)
    "ease-in" -> CubicBezierEasing(EASE_IN_CONTROL_X, 0f, 1f, 1f)
    "ease-out" -> CubicBezierEasing(0f, 0f, EASE_OUT_CONTROL_X, 1f)
    "ease-in-out" -> CubicBezierEasing(EASE_IN_CONTROL_X, 0f, EASE_OUT_CONTROL_X, 1f)
    else -> Regex("[+-]?(?:\\d*\\.\\d+|\\d+)").findAll(value).map { it.value.toFloat() }.toList().let {
        if (it.size == CUBIC_CONTROL_COUNT) CubicBezierEasing(it[0], it[1], it[2], it[LAST_CUBIC_CONTROL_INDEX]
            ) else LinearEasing
    }
}

/** Samples the typed WAAPI export, including delays, global easing and metric-owned bounds. */
fun liveboardScript(timeline: LiveboardTimeline, elapsed: Float, metrics: Map<LiveboardMetric, Float> = emptyMap(),
    index: Int = 0): LiveboardScriptPose {
    val duration = when (val timing = timeline.timing.duration) { is LiveboardDuration.Fixed -> timing.ms;
        is LiveboardDuration.ByPeer -> timing.at(index) }
    val raw = ((elapsed - timeline.timing.delay.at(index)) / duration).coerceIn(0f, 1f)
    val progress = liveboardEasing(timeline.timing.easing).transform(raw)
    val frames = timeline.frames
    if (frames.isEmpty()) return LiveboardScriptPose()
    val a = frames.lastOrNull { it.offset <= progress } ?: frames.first()
    val b = frames.firstOrNull { it.offset > progress } ?: frames.last()
    val t = if (a.offset == b.offset) 1f else ((progress - a.offset) / (b.offset - a.offset)).coerceIn(0f, 1f)
    fun scalar(value: LiveboardScalar?, default: Float) = when (value) {
        is LiveboardScalar.Fixed -> value.value
        is LiveboardScalar.Metric -> metrics[value.metric] ?: default
        null -> default
    }
    fun mix(x: Float, y: Float) = x + (y - x) * t
    fun metricPair(first: LiveboardScalar?, second: LiveboardScalar?, default: Float) = mix(scalar(first, default),
        scalar(second, default))
    return LiveboardScriptPose(metricPair(a.opacity, b.opacity, 1f),
        metricPair(a.transform?.x, b.transform?.x, 0f) to metricPair(a.transform?.y, b.transform?.y, 0f),
        mix(a.transform?.scale ?: 1f, b.transform?.scale ?: 1f), metricPair(a.blur, b.blur, 0f),
        listOf(a.bounds?.width to b.bounds?.width, a.bounds?.height to b.bounds?.height, a.bounds?.radius to b
            .bounds?.radius).map { (x, y) ->
            if (x == null && y == null) null else metricPair(x, y, 0f)
        })
}

private const val NANOS_PER_MILLI_FLOAT: Float = 1_000_000f
private const val LOOP_INDEX_STAGGER: Float = 210f
private const val EASE_CONTROL_X: Float = .25f
private const val EASE_CONTROL_Y: Float = .1f
private const val EASE_IN_CONTROL_X: Float = .42f
private const val EASE_OUT_CONTROL_X: Float = .58f
private const val HALF_TURN_DEGREES: Float = 180f
private const val TRACE_SWITCH_FRACTION: Float = .5f
private const val CUBIC_CONTROL_COUNT: Int = 4
private const val LAST_CUBIC_CONTROL_INDEX: Int = 3
