package metasecret.project.com

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule

internal data class LiveboardInboxRow(
    val nodeId: Int,
    val tag: String,
    val texts: List<String>,
    val pinnedError: Boolean,
)
internal data class LiveboardInboxObservation(val rows: List<LiveboardInboxRow>, val scannedNodes: Int)
internal data class LiveboardInboxProjection(
    val incoming: LiveboardTestIncoming?, val count: Int, val pinnedError: Boolean,
)

/** Compose 1.10.2 eagerly evaluates this matcher inside its one UI-thread node collection.
 * getRoots precedes that callback: this does not assert atomic global root registration.
 * The cap bounds our processing, not Compose's preceding all-node allocation. */
internal fun ComposeTestRule.lbCaptureInbox(): LiveboardInboxObservation {
    val errorLabel = lbLabel(core.AppString.lbError)
    val rows = mutableListOf<LiveboardInboxRow>()
    val seen = mutableSetOf<Int>()
    var scanned = 0
    var work = 0
    fun consume(node: SemanticsNode) {
        check(Looper.myLooper() === Looper.getMainLooper()) { "Inbox capture requires the UI thread" }
        check(work < 8192) { "Inbox capture processing limit" }
        work++
        check(node.layoutInfo.isAttached) { "Detached inbox semantics" }
    }
    fun texts(root: SemanticsNode): List<String> {
        val result = mutableListOf<String>()
        val stack = mutableListOf(root)
        val visited = mutableSetOf<Int>()
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.lastIndex)
            consume(node)
            check(visited.add(node.id)) { "Repeated inbox text node" }
            val rawTexts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val available = 8192 - work - stack.size
            check(available >= 0 && rawTexts.size <= available) { "Inbox text processing limit" }
            work += rawTexts.size
            result.addAll(rawTexts.map { it.text })
            val children = node.children
            check(children.size <= 8192 - work - stack.size) { "Inbox text frontier limit" }
            for (index in children.indices.reversed()) stack.add(children[index])
        }
        return result.toList()
    }
    val capture = SemanticsMatcher("Capture immutable inbox primitives") { node ->
        consume(node)
        check(seen.add(node.id)) { "Repeated inbox semantics node" }
        scanned++
        val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        check(descriptions.size <= 8192 - work) { "Inbox description processing limit" }
        work += descriptions.size
        val pinnedError = errorLabel in descriptions
        if (tag.startsWith("lb-inbox-head-") || tag in listOf("lb-request-sender", "lb-request-subject", "lb-inbox-count") || pinnedError) {
            val copiedTexts = if (tag in listOf("lb-request-sender", "lb-request-subject", "lb-inbox-count")) texts(node) else emptyList()
            rows.add(LiveboardInboxRow(node.id, tag, copiedTexts, pinnedError))
        }
        false
    }
    check(onAllNodes(capture, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    check(scanned > 0) { "Inbox capture did not observe semantics" }
    return LiveboardInboxObservation(rows.toList(), scanned)
}

/** No live node or framework query is read while projecting one captured observation. */
internal fun lbProjectInbox(observation: LiveboardInboxObservation): LiveboardInboxProjection {
    check(observation.scannedNodes in 1..8192)
    val rows = observation.rows
    check(rows.size <= observation.scannedNodes && rows.map { it.nodeId }.distinct().size == rows.size)
    val counters = rows.filter { it.tag == "lb-inbox-count" }
    check(counters.size <= 1) { "Ambiguous visible notification counter" }
    val count = counters.singleOrNull()?.let {
        check(it.texts.size == 1) { "Malformed visible notification counter" }
        checkNotNull(it.texts.single().toIntOrNull()) { "Malformed visible notification counter" }
            .also { value -> check(value > 0) }
    } ?: 0
    val heads = rows.filter { it.tag.startsWith("lb-inbox-head-") }
    val senders = rows.filter { it.tag == "lb-request-sender" }
    val subjects = rows.filter { it.tag == "lb-request-subject" }
    check(heads.size <= 1 && senders.size <= 1 && subjects.size <= 1) { "Ambiguous inbox fields" }
    val incoming = heads.singleOrNull()?.let { head ->
        val suffix = head.tag.removePrefix("lb-inbox-head-")
        val kind = listOf("Join", "Recover", "DeleteSecret", "RemoveDevice")
            .singleOrNull { suffix.startsWith("$it-") } ?: error("Unknown rendered request kind")
        val requestId = suffix.removePrefix("$kind-")
        check(requestId.isNotBlank() && requestId.none { it == '\r' || it == '\n' })
        val sender = senders.single()
        check(sender.texts.size == 1 && sender.texts.single().isNotBlank() && count > 0)
        val subject = subjects.singleOrNull()?.let { check(it.texts.size == 1); it.texts.single() }
        LiveboardTestIncoming(kind, requestId, sender.texts.single(), subject, count)
    }
    // A receipt may hide the pending head while the remaining queue still has a positive badge.
    return LiveboardInboxProjection(incoming, count, rows.any { it.pinnedError })
}
