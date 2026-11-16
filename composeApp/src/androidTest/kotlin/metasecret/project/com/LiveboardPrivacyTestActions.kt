package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import core.AppStateCacheProviderInterface
import org.koin.core.context.GlobalContext

internal fun ComposeTestRule.lbCaptureCopyBounds(id: String): Rect {
    lbWait("lb-copy-$id")
    onNodeWithTag("lb-copy-$id", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    val ownPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val root = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
    check(root.packageName?.toString() == ownPackage) { "Copy baseline must belong to the actual app window" }
    val candidates = lbAccessibilityNodes(root).filter {
        it.isVisibleToUser && it.isEnabled && (it.viewIdResourceName == "lb-copy-$id" || it.text?.toString() == lbLabel(core.AppString.lbCopy))
    }
    val actionable = candidates.mapNotNull { label ->
        val labelBounds = Rect().also { label.getBoundsInScreen(it) }
        check(!labelBounds.isEmpty) { "Visible Copy label has empty bounds" }
        var candidate: android.view.accessibility.AccessibilityNodeInfo? = label
        var found: android.view.accessibility.AccessibilityNodeInfo? = null
        for (depth in 0..8) {
            val node = candidate ?: break
            if (node.windowId != root.windowId || node.packageName?.toString() != ownPackage) break
            val actionBounds = Rect().also { node.getBoundsInScreen(it) }
            if (node.isVisibleToUser && node.isEnabled && !actionBounds.isEmpty && actionBounds.contains(labelBounds) &&
                (node.isClickable || node.actionList.any { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK })) {
                found = node
                break
            }
            candidate = node.parent
        }
        found
    }.distinct()
    check(actionable.size == 1) { "Copy baseline must resolve one bounded own-window actionable target" }
    return Rect().also { actionable.single().getBoundsInScreen(it); check(!it.isEmpty) }
}

internal fun ComposeTestRule.lbAssertHeldReadPrivacy(id: String, secretValue: String, oldCopyBounds: Rect) {
    val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
    waitUntil(180_000) { cache.synchronization.value.let { it.loading || it.activeRefreshes > 0 } }
    lbValueAbsent(id)
    if (lbHas("lb-primary-$id")) onNodeWithTag("lb-primary-$id", useUnmergedTree = true).assertIsNotEnabled()
    if (lbHas("lb-delete-$id")) onNodeWithTag("lb-delete-$id", useUnmergedTree = true).assertIsNotEnabled()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
    check(root.packageName?.toString() == instrumentation.targetContext.packageName)
    val nodes = lbAccessibilityNodes(root)
    check(root.isVisibleToUser && nodes.any { it.isVisibleToUser && (it.text?.isNotBlank() == true || it.contentDescription?.isNotBlank() == true) }) {
        "Privacy probe requires a meaningful actual foreground accessibility tree"
    }
    val foregroundBounds = Rect().also { root.getBoundsInScreen(it) }
    check(foregroundBounds.contains(oldCopyBounds.centerX(), oldCopyBounds.centerY()))
    check(nodes.none { it.viewIdResourceName == "lb-copy-$id" || it.viewIdResourceName == "lb-value-$id" })
    check(secretValue.isNotBlank() && nodes.none {
        it.text?.toString()?.contains(secretValue) == true || it.contentDescription?.toString()?.contains(secretValue) == true
    }) { "Actual foreground exposes plaintext (redacted)" }
    val copyLabel = lbLabel(core.AppString.lbCopy)
    check(nodes.none { it.text?.toString() == copyLabel || it.contentDescription?.toString() == copyLabel || it.viewIdResourceName == copyLabel }) {
        "Actual foreground exposes Copy"
    }
    val clipboard = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val sentinel = "test50-blocked-copy-sentinel"
    instrumentation.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("privacy-probe", sentinel)) }
    val time = SystemClock.uptimeMillis()
    val x = oldCopyBounds.exactCenterX(); val y = oldCopyBounds.exactCenterY()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
        try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    instrumentation.waitForIdleSync()
    var unchanged = false
    instrumentation.runOnMainSync {
        unchanged = clipboard.primaryClip?.getItemAt(0)?.coerceToText(instrumentation.targetContext)?.toString() == sentinel
    }
    check(unchanged) { "Blocked old Copy point changed the clipboard (redacted)" }
    lbValueAbsent(id)
    check(cache.synchronization.value.let { it.loading || it.activeRefreshes > 0 }) { "Canonical hold ended during the Copy probe" }
}
