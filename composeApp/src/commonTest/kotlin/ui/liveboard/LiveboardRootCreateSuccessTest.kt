package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout

class LiveboardRootCreateSuccessTest {
    @Test
    fun testOnlyExactCanonicalCreateKeepsAVisibleSuccessWithoutClearingNewInputOrReplayingOnRemount() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            val failures = mutableListOf<String>()
            try {
                f.start(); f.waitingCreatedSecret(); f.proof()
                val ui = StandardTestDispatcher()
                val root = f.root(ui)
                val session = f.session(root)
                ui.scheduler.runCurrent()
                val original = fullDraft("Canonical owned form")
                assertTrue(root.openCreate()); root.editForm(original)
                val editing = root.state.value.chrome.overlay
                val before = f.core.calls.value.size
                val gate = f.hold("create")
                val view = root.submitCreate()
                gate.entered.await()
                assertEquals(original, session.sensitive.state.value.draft)
                assertEquals(editing, root.state.value.chrome.overlay)
                // The admitted result survives only this view awaiter's cancellation.
                view.cancel(); view.join()
                gate.release.complete(Unit); gate.exited.await(); session.awaitSettled()
                ui.scheduler.runCurrent()
                assertEquals(before + 1, f.core.calls.value.size)
                assertEquals(NativeCall("create", listOf(original.name, original.value)), f.core.calls.value.last())
                assertEquals(LiveboardCreatePhase.Submitted, session.actions.createPresentation().phase)
                assertEquals(original, session.sensitive.state.value.draft, "Returned receipt is not canonical success")
                assertEquals(editing, root.state.value.chrome.overlay)
                f.core.fresh(); f.proof(); f.idle(); ui.scheduler.runCurrent()
                assertNull(session.sensitive.state.value.draft, "Exact physical terminal clears all owned fields first")
                assertFalse(session.sensitive.state.value.createOpen)
                assertEquals(LiveboardCreatePhase.Idle, session.actions.createPresentation().phase)
                rootCheck(failures, "canonical success stays visible") {
                    assertTrue(root.state.value.chrome.overlay != LiveboardRootOverlay.None,
                        "Exact canonical success needs a visible nonsensitive completion presentation")
                }
                assertEquals(before + 1, f.core.calls.value.size)
                root.close(); ui.scheduler.runCurrent()
                val remounted = f.root()
                assertSame(session, f.session(remounted))
                assertEquals(LiveboardRootOverlay.None, remounted.state.value.chrome.overlay,
                    "A new attachment must not replay historical canonical success")
                f.proof(); f.idle()
                assertEquals(LiveboardRootOverlay.None, remounted.state.value.chrome.overlay)
                assertEquals(before + 1, f.core.calls.value.size)
                val fresh = fullDraft("Fresh after completion")
                assertTrue(remounted.openCreate()); remounted.editForm(fresh)
                remounted.submitCreate().join(); session.awaitSettled()
                assertEquals(before + 2, f.core.calls.value.size)
                assertEquals(NativeCall("create", listOf(fresh.name, fresh.value)), f.core.calls.value.last())

                f.newOwner(); f.waitingCreatedSecret(); f.proof()
                val newerRoot = f.root()
                val newerSession = f.session(newerRoot)
                val issued = fullDraft("Original selected form")
                val replacement = fullDraft("New authored form").copy(value = "new active value", words = List(24) { "new$it" })
                assertTrue(newerRoot.openCreate()); newerRoot.editForm(issued)
                val newerEditing = newerRoot.state.value.chrome.overlay
                val held = f.hold("create")
                val oldView = newerRoot.submitCreate(); held.entered.await()
                newerRoot.editForm(replacement)
                held.release.complete(Unit); held.exited.await(); oldView.join(); newerSession.awaitSettled()
                assertEquals(NativeCall("create", listOf(issued.name, issued.value)), f.core.calls.value.last())
                f.core.fresh(); f.proof(); f.idle()
                assertEquals(replacement, newerSession.sensitive.state.value.draft)
                assertTrue(newerSession.sensitive.state.value.createOpen)
                assertEquals(newerEditing, newerRoot.state.value.chrome.overlay, "New authored input stays editing")
                assertTrue(newerSession.actions.createPresentation().canSubmit)

                f.newOwner(); f.waitingCreatedSecret(); f.proof()
                val unknownRoot = f.root()
                val unknownSession = f.session(unknownRoot)
                val uncertainDraft = fullDraft("Unknown owned form")
                assertTrue(unknownRoot.openCreate()); unknownRoot.editForm(uncertainDraft)
                val uncertain = f.hold("create", IllegalStateException("Synthetic unknown success-boundary response"))
                val uncertainView = unknownRoot.submitCreate(); uncertain.entered.await()
                uncertain.release.complete(Unit); uncertain.exited.await(); uncertainView.join(); unknownSession.awaitSettled()
                assertEquals(LiveboardCreatePhase.Unknown, unknownSession.actions.createPresentation().phase)
                assertEquals(uncertainDraft, unknownSession.sensitive.state.value.draft)
                assertEquals(LiveboardRootOverlay.None, unknownRoot.state.value.chrome.overlay,
                    "Entered failure closes as error, never as success")
                val count = f.core.calls.value.size
                unknownRoot.close()
                val unknownRemount = f.root()
                assertSame(unknownSession, f.session(unknownRemount))
                assertEquals(LiveboardRootOverlay.None, unknownRemount.state.value.chrome.overlay)
                assertTrue(unknownRemount.openCreate())
                assertNotNull(unknownSession.sensitive.state.value.draft)
                unknownRemount.submitCreate().join(); unknownSession.awaitSettled()
                assertEquals(count, f.core.calls.value.size, "Remount cannot infer success or replay Unknown")
                f.newOwner()
                val live = f.root()
                val liveSession = f.session(live)
                val liveDraft = fullDraft("Current live form")
                assertTrue(live.openCreate()); live.editForm(liveDraft)
                live.submitCreate().join(); liveSession.awaitSettled()
                assertEquals(count + 1, f.core.calls.value.size)
                assertEquals(NativeCall("create", listOf(liveDraft.name, liveDraft.value)), f.core.calls.value.last())
                assertEquals(emptyList(), failures, "Returned/canonical, current success, newer input, Unknown, remount and genuine live native controls reached")
            } finally { f.close() }
        }
    }

    private fun fullDraft(name: String) = LiveboardDraft(name, "Synthetic selected input", LiveboardDraftMode.Plain,
        12, List(24) { "inactive${it + 1}" })
}
