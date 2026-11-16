package metasecret.project.com

import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import core.AppString

private const val RECOVER_UI_BUDGET_MS = 180_000L
private const val MAX_RECOVER_NODES = 2048
private const val MAX_RECOVER_DEPTH = 128
private const val NANOS_PER_MILLISECOND = 1_000_000L

/** Accessibility action only: owning-root current UI, never a physical hit-test or Core receipt. */
internal fun ComposeTestRule.lbRequestRecoverySemantics(name: String): String {
    val started = System.nanoTime()
    fun remaining(): Long = (RECOVER_UI_BUDGET_MS -
        (System.nanoTime() - started) / NANOS_PER_MILLISECOND).also {
        check(it > 0) { "Recover UI deadline expired" }
    }
    val label = lbLabel(AppString.recover)
    val id = lbWaitPrimary(name, label, remaining())
    remaining()
    lbValueAbsent(id)
    runCatching { onNodeWithTag("lb-primary-$id", useUnmergedTree = true).performScrollTo() }
    remaining()
    // This anchor is not a cached target/action. The current subtree is traversed on UI below.
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    remaining()
    lbAwaitRecoverDispatch(root, "lb-primary-$id", label, ::remaining)
    remaining()
    // Presentation-only wait; actual canonical claims/strict terminal barriers remain in the orchestrator.
    waitUntil(remaining()) {
        runCatching {
            onNodeWithTag("lb-primary-$id", useUnmergedTree = true).assertIsNotEnabled()
            onNodeWithTag("lb-delete-$id", useUnmergedTree = true).assertIsNotEnabled()
        }.isSuccess
    }
    remaining()
    return id
}

internal class RecoverBeforeDispatchNotReady : IllegalStateException("Recover UI not ready")

/** Readiness may be observed again only before this call has begun its single UI action. */
internal fun ComposeTestRule.lbAwaitRecoverDispatch(
    root: SemanticsNode,
    tag: String,
    label: String,
    remaining: () -> Long,
) {
    var invocationStarted = false
    val budget = remaining()
    check(budget > 0)
    waitUntil(budget) {
        check(!invocationStarted) { "Recover action cannot be repeated" }
        var dispatched = false
        runOnUiThread {
            check(remaining() > 0)
            try {
                lbDispatchRecoverSemantics(root, tag, label, remaining) { invocationStarted = true }
                dispatched = true
            } catch (failure: RecoverBeforeDispatchNotReady) {
                if (invocationStarted) throw failure
            }
            check(remaining() > 0)
        }
        dispatched
    }
    check(invocationStarted)
}

/** Preserve the original trailing-lambda remaining argument without a competing default callback. */
internal fun lbDispatchRecoverSemantics(
    root: SemanticsNode,
    tag: String,
    label: String,
    remaining: () -> Long,
) = lbDispatchRecoverSemantics(root, tag, label, remaining, {})

/** Called only on the UI thread: complete current owning-root traversal precedes any action. */
internal fun lbDispatchRecoverSemantics(
    root: SemanticsNode,
    tag: String,
    label: String,
    remaining: () -> Long,
    invocationStarted: () -> Unit,
) {
    check(Looper.myLooper() === Looper.getMainLooper()) { "Recover dispatch requires UI thread" }
    check(remaining() > 0)
    check(tag.isNotEmpty() && label.isNotEmpty())
    lbRequireRecoverRoot(root)
    val current = lbCurrentRecoverNode(root, tag)
    check(current.layoutInfo.isAttached && current.layoutInfo.isPlaced)
    if (lbNodeTexts(current).joinToString(" ") != label) throw RecoverBeforeDispatchNotReady()
    if (current.config.getOrNull(SemanticsProperties.Disabled) != null) throw RecoverBeforeDispatchNotReady()
    lbRequireRecoverAncestry(root, current)
    check(lbRecoverBounds(current.boundsInRoot, root.boundsInRoot)) { "Recover is outside owning root" }
    check(lbRecoverBounds(current.boundsInWindow, root.boundsInWindow)) { "Recover is outside owning window" }
    val action = checkNotNull(current.config.getOrNull(SemanticsActions.OnClick)?.action) {
        "Current Recover action absent"
    }
    lbRequireRecoverRoot(root)
    check(current.layoutInfo.isAttached && current.layoutInfo.isPlaced)
    check(remaining() > 0)
    invocationStarted()
    check(action()) { "Current Recover action was not handled" }
    check(remaining() > 0)
}

private fun lbRequireRecoverRoot(root: SemanticsNode) {
    check(root.parent == null && root.layoutInfo.isAttached && root.layoutInfo.isPlaced) {
        "Recover owning root retired"
    }
    check(lbRecoverBounds(root.boundsInWindow, root.boundsInWindow)) { "Recover root bounds unavailable" }
}

private fun lbCurrentRecoverNode(root: SemanticsNode, tag: String): SemanticsNode {
    val pending = ArrayDeque<Pair<SemanticsNode, Int>>()
    val visited = mutableSetOf<Int>()
    val matching = mutableListOf<SemanticsNode>()
    pending.add(root to 0)
    while (pending.isNotEmpty()) {
        val (node, depth) = pending.removeLast()
        check(depth <= MAX_RECOVER_DEPTH && visited.add(node.id) && visited.size <= MAX_RECOVER_NODES) {
            "Recover tree is ambiguous or exceeds bounds"
        }
        if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) matching.add(node)
        val children = node.children
        check(children.size <= MAX_RECOVER_NODES - visited.size - pending.size)
        children.forEach { pending.add(it to depth + 1) }
    }
    check(matching.size == 1) { "Recover target absent or ambiguous" }
    return matching.single()
}

private fun lbRequireRecoverAncestry(root: SemanticsNode, current: SemanticsNode) {
    var ancestor = current
    var depth = 0
    while (ancestor.parent != null) {
        check(depth++ < MAX_RECOVER_DEPTH) { "Recover ancestry exceeds bounds" }
        ancestor = checkNotNull(ancestor.parent)
    }
    check(ancestor.layoutInfo === root.layoutInfo) { "Recover target changed owning root" }
}

private fun lbRecoverBounds(control: Rect, root: Rect): Boolean {
    val values = listOf(control.left, control.top, control.right, control.bottom,
        root.left, root.top, root.right, root.bottom)
    if (values.any { !it.isFinite() } || control.width <= 0 || control.height <= 0 ||
        root.width <= 0 || root.height <= 0) return false
    val clipped = control.intersect(root)
    return clipped.width > 0 && clipped.height > 0
}
