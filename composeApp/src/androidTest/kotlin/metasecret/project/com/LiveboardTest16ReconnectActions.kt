package metasecret.project.com

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Test16 only: one manual error acknowledgement/new action, one original deadline. */
internal fun ComposeTestRule.lbTest16ReconnectApprove(head: LiveboardTestIncoming) {
    check(head.kind == "Recover")
    val deadline = System.nanoTime() + 180_000_000_000L
    fun remaining(): Long = ((deadline - System.nanoTime()) / 1_000_000).also {
        check(it > 0) { "Test16 reconnect deadline expired" }
    }
    fun observe(): LiveboardInboxProjection {
        remaining()
        val value = lbProjectInbox(lbCaptureInbox())
        remaining()
        value.incoming?.takeIf { it.requestId == head.requestId }?.let {
            check(it == head) { "Test16 original request metadata changed" }
        }
        return value
    }
    fun terminal(value: LiveboardInboxProjection): Boolean = !value.pinnedError &&
        value.incoming != head && value.count == head.count - 1
    var dismissed = false
    var first = observe()
    waitUntil(remaining()) {
        first = observe()
        first.pinnedError || terminal(first)
    }
    if (first.pinnedError) {
        check(lbExactlyOne("lb-inbox")) { "Test16 error tile is ambiguous" }
        val errorControl = onNodeWithTag("lb-inbox", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
        remaining()
        errorControl.performClick()
        remaining()
        dismissed = true
        waitUntil(remaining()) { !observe().pinnedError }
    }
    var ended = false
    waitUntil(remaining()) {
        val current = observe()
        check(!current.pinnedError) { "Test16 new error cannot authorize another acknowledgement" }
        ended = terminal(current)
        if (ended) true else if (!dismissed || current.incoming != head) false else {
            lbBindCurrentIncoming(head)
            remaining()
            runCatching {
                check(lbExactlyOne("lb-approve-${head.requestId}"))
                onNodeWithTag("lb-approve-${head.requestId}", useUnmergedTree = true)
                    .assertIsDisplayed().assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }
    if (!ended) {
        val current = observe()
        if (!terminal(current)) {
            check(dismissed && !current.pinnedError && current.incoming == head)
            lbBindCurrentIncoming(head)
            remaining()
            check(remaining() > 30_000) { "Test16 insufficient remaining authentication budget" }
            check(lbExactlyOne("lb-approve-${head.requestId}"))
            val approveControl = onNodeWithTag("lb-approve-${head.requestId}", useUnmergedTree = true)
                .assertIsDisplayed().assertIsEnabled()
            check(remaining() > 30_000) { "Test16 final authentication reserve expired" }
            approveControl.performClick()
            remaining()
            // Existing actual OS helper can wait15s for prompt PLUS15s completion.
            check(remaining() > 30_000) { "Test16 insufficient remaining authentication budget" }
            lbApproveSystemIfPresent()
            remaining()
        }
    }
    lbIncomingTerminal(head, deadline)
    remaining()
}
