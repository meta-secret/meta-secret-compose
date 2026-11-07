package ui.liveboard

import android.app.Activity
import android.graphics.Rect
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject

/** Passive metadata only; editor/IMM columns remain unobserved null, never getters. */
internal class LiveboardImeDiagnostics(private val currentActivity: () -> Activity) {
    private var originalHost: Activity? = null
    private var beforeTouch: JSONArray? = null
    private var afterTouch: JSONArray? = null
    private val lastTwo = ArrayDeque<JSONArray>()

    fun capture(phase: Int, ordinal: Int = 0, expectedHost: Activity? = null) {
        if (expectedHost != null) originalHost = expectedHost
        var row = JSONArray(listOf(ordinal, SystemClock.elapsedRealtime(), false) + List(14) { null })
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = currentActivity()
                val decor = activity.window.decorView
                val focus = activity.currentFocus
                row = JSONArray(listOf(ordinal, SystemClock.elapsedRealtime(), true,
                    originalHost?.let { activity === it }, activity.resources.configuration.orientation,
                    decor.width, decor.height, activity.hasWindowFocus(), decor.hasWindowFocus(), focus != null,
                    focus?.isFocused, focus?.hasWindowFocus(), null, null, null, null, null))
            }
        } catch (_: Throwable) { /* Unavailable metadata stays qualified; never changes the test action/error. */ }
        when (phase) {
            1 -> beforeTouch = row
            2 -> afterTouch = row
            3 -> { if (lastTwo.size == 2) lastTwo.removeFirst(); lastTwo.addLast(row) }
        }
    }

    /** One measured system DOWN/UP replaces one local Compose gesture, never an extra tap. */
    fun systemTap(target: SemanticsNodeInteraction, viewport: SemanticsNodeInteraction, expectedHost: Activity) {
        val nodes = listOf(target.assertIsDisplayed().assertIsEnabled().fetchSemanticsNode(),
            viewport.assertIsDisplayed().fetchSemanticsNode())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        check(root.packageName?.toString() == instrumentation.targetContext.packageName) { "Gesture needs own active window" }
        var point: Pair<Float, Float>? = null
        instrumentation.runOnMainSync {
            val activity = currentActivity()
            val decor = activity.window.decorView
            check(activity === expectedHost && decor.hasWindowFocus()) { "Gesture host/window changed" }
            val screen = IntArray(2).also { decor.getLocationOnScreen(it) }
            val window = IntArray(2).also { decor.getLocationInWindow(it) }
            val bounds = nodes.map { node ->
                val x = node.positionInWindow.x + screen[0] - window[0]
                val y = node.positionInWindow.y + screen[1] - window[1]
                RectF(x, y, x + node.size.width, y + node.size.height)
            }
            val visible = RectF(Rect().also { decor.getWindowVisibleDisplayFrame(it) })
            check((bounds + visible).all { it.width() > 0f && it.height() > 0f &&
                listOf(it.left, it.top, it.right, it.bottom).all { value -> value.isFinite() } }) { "Gesture bounds invalid" }
            check(visible.contains(bounds[0]) && bounds[1].contains(bounds[0])) { "Gesture target outside visible editor" }
            point = bounds[0].let { it.centerX() to it.centerY() }
        }
        val (x, y) = checkNotNull(point)
        capture(1, expectedHost = expectedHost)
        val downTime = SystemClock.uptimeMillis()
        var primary: Throwable? = null
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            var event: MotionEvent? = null
            try {
                val created = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event = created
                created.source = InputDevice.SOURCE_TOUCHSCREEN
                check(instrumentation.uiAutomation.injectInputEvent(created, true)) { "System pointer injection rejected" }
            } catch (error: Throwable) {
                val first = primary
                if (first == null) primary = error else if (first !== error) first.addSuppressed(error)
            } finally {
                try { event?.recycle() } catch (error: Throwable) {
                    val first = primary
                    if (first == null) primary = error else if (first !== error) first.addSuppressed(error)
                }
            }
        }
        primary?.let { throw it }
    }

    fun json(): JSONObject = JSONObject().put("schema", 1)
        .put("beforeTouch", beforeTouch ?: JSONObject.NULL).put("afterTouch", afterTouch ?: JSONObject.NULL)
        .put("lastTwo", JSONArray(lastTwo.toList()))
}
