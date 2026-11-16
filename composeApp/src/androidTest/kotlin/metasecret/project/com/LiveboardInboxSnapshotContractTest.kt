package metasecret.project.com

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test

/** Real Compose API/adapter contracts only; not Core, OS-auth or scenario acceptance. */
class LiveboardInboxSnapshotContractTest {
    val compose = createComposeRule()
    @get:Rule val componentResources = RuleChain.outerRule(LiveboardComponentStringsRule()).around(compose)

    private data class Frame(
        val head: String? = "Recover-opaque-claim",
        val sender: List<String> = listOf("Canonical peer"),
        val subject: List<String> = listOf("Controlled subject"),
        val counts: List<String> = listOf("1"),
        val duplicateHead: Boolean = false,
    )

    private fun render(initial: Frame): (Frame) -> Unit {
        val current = mutableStateOf(initial)
        compose.setContent {
            val frame = current.value
            Column(Modifier.testTag("contract-root")) {
                frame.head?.let {
                    Column(Modifier.testTag("lb-inbox-head-$it")) { Text("Incoming") }
                    if (frame.duplicateHead) Text("Other", Modifier.testTag("lb-inbox-head-$it"))
                    Column(Modifier.testTag("lb-request-sender")) {
                        frame.sender.forEach { text -> Text(text) }
                    }
                    Column(Modifier.testTag("lb-request-subject")) {
                        frame.subject.forEach { text -> Text(text) }
                    }
                }
                frame.counts.forEach { Text(it, Modifier.testTag("lb-inbox-count")) }
            }
        }
        return { frame -> compose.runOnIdle { current.value = frame } }
    }

    @Test fun publicMatcherEagerlyRunsOnMainAndMaterializesBeforeReturn() {
        render(Frame())
        var callbacks = 0
        val ids = mutableSetOf<Int>()
        val selected = compose.onAllNodes(SemanticsMatcher("API callback qualification") {
            assertTrue(Looper.myLooper() === Looper.getMainLooper())
            assertTrue(it.layoutInfo.isAttached)
            assertTrue(ids.add(it.id))
            callbacks++
            false
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(selected.isEmpty())
        assertTrue(callbacks > 0)
        val completed = callbacks
        compose.runOnIdle { Unit }
        assertEquals(completed, callbacks)
        val actual = compose.lbCaptureInbox()
        assertTrue(actual.scannedNodes > 0)
        assertEquals("opaque-claim", lbProjectInbox(actual).incoming?.requestId)
    }

    @Test fun actualCaptureCopiesChildTextBeforeLaterTerminalFrame() {
        val change = render(Frame())
        val captured = compose.lbCaptureInbox()
        val before = lbProjectInbox(captured)
        assertEquals("Canonical peer", before.incoming?.requesterName)
        assertEquals("Controlled subject", before.incoming?.subject)
        assertEquals(1, before.count)
        change(Frame(head = null, counts = emptyList()))
        assertNull(compose.lbVisibleIncoming())
        assertEquals(before, lbProjectInbox(captured))
    }

    @Test fun emptyAndReceiptWithPositiveBadgeAreDifferentValidFrames() {
        val change = render(Frame(head = null, counts = emptyList()))
        var current = lbProjectInbox(compose.lbCaptureInbox())
        assertNull(current.incoming)
        assertEquals(0, current.count)
        change(Frame(head = null, counts = listOf("2")))
        current = lbProjectInbox(compose.lbCaptureInbox())
        assertNull(current.incoming)
        assertEquals(2, current.count)
        assertFalse(current.pinnedError)
    }

    @Test fun stableMalformedPendingStillFailsInsteadOfBecomingEmpty() {
        val change = render(Frame())
        val invalid = listOf(
            Frame(sender = listOf("")),
            Frame(sender = listOf("A", "B")),
            Frame(subject = listOf("A", "B")),
            Frame(counts = emptyList()),
            Frame(counts = listOf("0")),
            Frame(counts = listOf("-1")),
            Frame(counts = listOf("bad")),
            Frame(counts = listOf("1", "2")),
            Frame(head = "Unknown-claim"),
            Frame(head = "Recover-"),
            Frame(head = "Recover-invalid\nidentity"),
            Frame(duplicateHead = true),
        )
        for (frame in invalid) {
            change(frame)
            assertThrows(IllegalStateException::class.java) { compose.lbVisibleIncoming() }
        }
    }

    @Test fun malformedReceiptCounterIsNotSilentlyConvertedToZero() {
        val change = render(Frame(head = null, counts = emptyList()))
        for (count in listOf("bad", "0", "-1")) {
            change(Frame(head = null, counts = listOf(count)))
            assertThrows(IllegalStateException::class.java) { compose.lbIncomingCount() }
        }
    }

    @Test fun duplicateInboxAcrossRealComposeRootsCannotChooseFirst() {
        compose.setContent {
            Column(Modifier.testTag("lb-inbox-head-Recover-first")) {
                Text("Peer", Modifier.testTag("lb-request-sender"))
                Text("1", Modifier.testTag("lb-inbox-count"))
            }
            Dialog(onDismissRequest = {}) {
                Text("Other", Modifier.testTag("lb-inbox-head-Recover-second"))
            }
        }
        assertThrows(IllegalStateException::class.java) { compose.lbVisibleIncoming() }
    }

    @Test fun terminalCompletionStillNeedsDisappearanceAndExactCountDrop() {
        val change = render(Frame())
        val head = checkNotNull(compose.lbVisibleIncoming())
        change(Frame(head = null, counts = emptyList()))
        compose.lbIncomingTerminal(head)
        assertTrue(head.requestId in compose.lbScope().completedRequests)
        assertNull(compose.lbScope().incoming)
    }
}
