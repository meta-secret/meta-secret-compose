package ui.scenes.registration

import androidx.compose.animation.core.CubicBezierEasing

internal data class OrbitFusionOpacity(val side: Float, val label: Float, val back: Float, val dots: Float)
internal data class OrbitFusionPose(val travel: Float, val opacity: OrbitFusionOpacity, val scaleX: Float,
    val scaleY: Float)
private val sideEase = CubicBezierEasing(.45f, 0f, .2f, 1f)

/** Frozen05.9 reference keyframes, sampled from linear 1400ms presentation time. */
internal fun orbitFusionPose(progress: Float): OrbitFusionPose {
    val p = progress.coerceIn(0f, 1f)
    val travel = when {
        p <= .08f -> 0f
        p >= .44f -> 1f
        else -> sideEase.transform((p - .08f) / .36f)
    }
    val side = when {
        p <= .44f -> 1f
        p >= .48f -> 0f
        else -> 1 - sideEase.transform((p - .44f) / .04f)
    }
    return OrbitFusionPose(travel, OrbitFusionOpacity(side,
        orbitKeyframeValue(p, listOf(0f to 1f, .18f to 0f, 1f to 0f)),
        orbitKeyframeValue(p, listOf(0f to 1f, .18f to 0f, .8f to 0f, .96f to 1f, 1f to 1f)),
        orbitKeyframeValue(p, listOf(0f to 0f, .4f to 0f, .47f to 1f, .72f to 1f, .84f to 0f, 1f to 0f))),
        orbitKeyframeValue(p, listOf(0f to 1f, .4f to 1f, .49f to .84f, .61f to 1.08f, .73f to .98f, .85f to 1f,
            1f to 1f)),
        orbitKeyframeValue(p, listOf(0f to 1f, .4f to 1f, .49f to 1.25f, .61f to .94f, .73f to 1.04f, .85f to 1f,
            1f to 1f)))
}

internal fun orbitKeyframeValue(progress: Float, frames: List<Pair<Float, Float>>): Float {
    val p = progress.coerceIn(0f, 1f)
    val next = frames.indexOfFirst { it.first >= p }.coerceAtLeast(1)
    val (start, a) = frames[next - 1]
    val (end, b) = frames[next]
    return a + (b - a) * ((p - start) / (end - start)).coerceIn(0f, 1f)
}
