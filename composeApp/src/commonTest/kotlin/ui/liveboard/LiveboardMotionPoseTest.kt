package ui.liveboard

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.test.Test
import kotlin.test.assertEquals
import ui.design.generated.LiveboardDuration
import ui.design.generated.LiveboardMetric
import ui.design.generated.LiveboardTimelines
import ui.liveboard.visual.liveboardEasing
import ui.liveboard.visual.liveboardScript

class LiveboardMotionPoseTest {
    @Test
    fun testCanonicalExportedCubicBezierHasItsNonlinearComposeTransform() {
        val expected = CubicBezierEasing(.22f, .8f, .24f, 1f)
        val actual = liveboardEasing("cubic-bezier(.22,.8,.24,1)")

        for (fraction in listOf(.1f, .25f, .5f, .75f, .9f)) {
            assertEquals(expected.transform(fraction), actual.transform(fraction), .00001f,
                "Canonical exported curve at $fraction must retain its nonlinear transform")
        }
    }

    @Test
    fun testEveryActualExportedCubicTimelineUsesItsExactComposeControlPoints() {
        val expected = mapOf(
            "cubic-bezier(.22,.8,.24,1)" to CubicBezierEasing(.22f, .8f, .24f, 1f),
            "cubic-bezier(.5,0,.7,.2)" to CubicBezierEasing(.5f, 0f, .7f, .2f),
            "cubic-bezier(.4,0,.2,1)" to CubicBezierEasing(.4f, 0f, .2f, 1f),
        )
        val exported = LiveboardTimelines.all.filterValues { it.timing.easing.startsWith("cubic-bezier(") }
        assertEquals(setOf("inviteOpen", "inviteClose", "sheetOpen", "createOpen", "createHeight",
            "notificationHeight", "secretHeight", "cipherParticle", "cipherParticleHide"), exported.keys)
        assertEquals(expected.keys, exported.values.map { it.timing.easing }.toSet())

        for ((name, timeline) in exported) {
            val control = expected.getValue(timeline.timing.easing)
            val actual = liveboardEasing(timeline.timing.easing)
            for (fraction in listOf(.1f, .25f, .5f, .75f, .9f)) {
                assertEquals(control.transform(fraction), actual.transform(fraction), .00001f,
                    "$name exported curve at $fraction")
            }
        }
    }

    @Test
    fun testActualCipherParticleAndConcealScriptsSampleIndexedDelayAndMetricOwnedMotion() {
        val metrics = mapOf(LiveboardMetric.MASK_OPACITY to .3f,
            LiveboardMetric.PARTICLE_DX to 48f, LiveboardMetric.PARTICLE_DY to -24f)
        val control = CubicBezierEasing(.4f, 0f, .2f, 1f)
        val index = 3
        for (timeline in listOf(LiveboardTimelines.cipherParticle, LiveboardTimelines.cipherParticleHide)) {
            assertEquals(640, (timeline.timing.duration as LiveboardDuration.Fixed).ms)
            assertEquals(30, timeline.timing.delay.at(index))
        }

        for (fraction in listOf(-.1f, 0f, .125f, .5f, .875f, 1f, 1.1f)) {
            val elapsed = 30f + 640f * fraction
            val progress = control.transform(fraction.coerceIn(0f, 1f))
            val reveal = liveboardScript(LiveboardTimelines.cipherParticle, elapsed, metrics, index)
            val conceal = liveboardScript(LiveboardTimelines.cipherParticleHide, elapsed, metrics, index)
            assertEquals(48f * progress, reveal.translate.first, .00001f, "Reveal x at $fraction")
            assertEquals(-24f * progress, reveal.translate.second, .00001f, "Reveal y at $fraction")
            assertEquals(.3f + .55f * progress, reveal.opacity, .00001f, "Reveal opacity at $fraction")
            assertEquals(48f * (1f - progress), conceal.translate.first, .00001f, "Conceal x at $fraction")
            assertEquals(-24f * (1f - progress), conceal.translate.second, .00001f, "Conceal y at $fraction")
            assertEquals(.85f - .55f * progress, conceal.opacity, .00001f, "Conceal opacity at $fraction")
        }
    }
}
