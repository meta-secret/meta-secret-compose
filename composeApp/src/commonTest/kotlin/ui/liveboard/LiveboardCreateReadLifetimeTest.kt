package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher

class LiveboardCreateReadLifetimeTest {
    @Test
    fun testCanonicalSavedKeepsItsExactTokenAcrossHarmlessPhysicalReadsButCannotReplayOrCloseNewInput() = runBlocking {
        withTimeout(45_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (source in listOf("manager", "socket")) saved(this, source, failures, reached)
            assertEquals(2, reached.size)
            assertEquals(emptyList(), failures,
                "Same-token busy/SUCCESS, new authored input, remount, hard privacy and fresh native controls reached: $reached")
        }
    }

    private suspend fun saved(scope: CoroutineScope, source: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        try {
            f.start()
            val ui = StandardTestDispatcher()
            val root = f.root(ui)
            val session = f.session(root)
            ui.scheduler.runCurrent()
            val completion = canonical(f, root, session)
            ui.scheduler.runCurrent()
            assertSame(completion, root.state.value.createCompletion)
            assertEquals(LiveboardRootOverlay.Create, root.state.value.chrome.overlay)
            val owner = assertNotNull(f.initialization.currentScope())
            val before = f.core.calls.value.toList(); val auth = f.auth.calls
            val target = assertNotNull(f.reads.requireFreshRead(owner))
            val gate = f.hold("read")
            val reading = if (source == "manager") f.track(scope.async(Dispatchers.IO) { f.manager.getStateModel() })
                else { f.socket.refreshAppState(); null }
            gate.entered.await(); ui.scheduler.runCurrent()
            assertTrue(f.cache.synchronization.value.activeRefreshes > 0)
            rootCheck(failures, "$source/busy Saved token") {
                assertFalse(session.inbox.state.value.ready)
                assertSame(completion, session.sensitive.state.value.createCompletion)
                assertSame(completion, root.state.value.createCompletion)
                assertEquals(LiveboardRootOverlay.Create, root.state.value.chrome.overlay)
                assertNull(session.sensitive.state.value.draft)
                assertFalse(session.sensitive.state.value.createOpen)
            }
            gate.release.complete(Unit); gate.exited.await(); reading?.await(); f.idle()
            val proof = target.completion.await()
            assertEquals(NativeReadOutcome.SUCCESS, proof.outcome); assertEquals(owner, proof.scope)
            assertTrue(target in proof.coveredRequirements)
            assertEquals(owner, f.initialization.currentScope())
            ui.scheduler.runCurrent()
            rootCheck(failures, "$source/SUCCESS retains exact completion") {
                assertSame(completion, session.sensitive.state.value.createCompletion)
                assertSame(completion, root.state.value.createCompletion)
                assertEquals(LiveboardRootOverlay.Create, root.state.value.chrome.overlay)
                assertNull(session.sensitive.state.value.draft)
                assertFalse(session.sensitive.state.value.createOpen)
            }
            assertEquals(before, f.core.calls.value); assertEquals(auth, f.auth.calls)
            // This is a state/token test. The Android render clock separately proves 519/520ms.
            root.close(); ui.scheduler.runCurrent()
            val remount = f.root()
            assertSame(session, f.session(remount))
            assertNull(remount.state.value.createCompletion)
            assertEquals(LiveboardRootOverlay.None, remount.state.value.chrome.overlay)
            f.proof(); f.idle()
            assertNull(remount.state.value.createCompletion, "An ordinary read must not replay a historical token")
            val newer = draft("New editor after Saved")
            assertTrue(remount.openCreate()); remount.editForm(newer)
            assertFalse(root.finishCreate(completion, session))
            assertFalse(remount.finishCreate(completion, session))
            assertEquals(newer, session.sensitive.state.value.draft)
            assertEquals(LiveboardRootOverlay.Create, remount.state.value.chrome.overlay)
            assertNull(session.sensitive.state.value.createCompletion)
            // Genuine new native issue, followed by actual exact canonical completion again.
            val hardToken = canonical(f, remount, session)
            assertSame(hardToken, remount.state.value.createCompletion)
            if (source == "manager") {
                f.socket.onAppBackground()
                assertNull(session.sensitive.state.value.createCompletion)
                assertNull(session.sensitive.state.value.draft)
                assertEquals(LiveboardRootOverlay.None, remount.state.value.chrome.overlay)
                f.socket.onAppForeground(); f.proof(); f.idle()
                assertNull(session.sensitive.state.value.createCompletion)
                assertNull(remount.state.value.createCompletion)
                assertEquals(LiveboardRootOverlay.None, remount.state.value.chrome.overlay)
            } else {
                f.newOwner()
                assertNull(session.sensitive.state.value.createCompletion)
                assertNull(session.sensitive.state.value.draft)
            }
            val frame = f.newOwner()
            val current = f.root()
            val live = f.session(current)
            assertNotSame(session, live); assertEquals(frame.scope, f.initialization.currentScope())
            assertNull(current.state.value.createCompletion)
            assertEquals(LiveboardRootOverlay.None, current.state.value.chrome.overlay)
            val positive = draft("Current owner after privacy")
            assertTrue(current.openCreate()); current.editForm(positive)
            val writes = f.core.calls.value.size
            current.submitCreate().join(); live.awaitSettled()
            assertEquals(writes + 1, f.core.calls.value.size)
            assertEquals(NativeCall("create", listOf(positive.name, positive.value)), f.core.calls.value.last())
            reached.add("$source/held-SUCCESS/old-token-no-replay/new-input/hard-boundary/live-native")
        } finally { f.close() }
    }

    private suspend fun canonical(f: LiveboardRootTestFixture, root: LiveboardRootCoordinator,
        session: LiveboardSession): LiveboardCreateCompletion {
        f.waitingCreatedSecret(); f.proof(); f.idle()
        val selected = draft("Exact canonical selected form")
        assertTrue(root.openCreate()); root.editForm(selected)
        val before = f.core.calls.value.size
        root.submitCreate().join(); session.awaitSettled()
        assertEquals(before + 1, f.core.calls.value.size)
        assertEquals(NativeCall("create", listOf(selected.name, selected.value)), f.core.calls.value.last())
        assertNull(session.sensitive.state.value.createCompletion, "Returned Submitted is not Saved")
        assertEquals(LiveboardCreatePhase.Submitted, session.actions.createPresentation().phase)
        assertEquals(selected, session.sensitive.state.value.draft)
        f.core.fresh(); f.proof(); f.idle()
        assertNull(session.sensitive.state.value.draft)
        assertFalse(session.sensitive.state.value.createOpen)
        return assertNotNull(session.sensitive.state.value.createCompletion)
    }

    private fun draft(name: String) = LiveboardDraft(name, "Selected synthetic value", LiveboardDraftMode.Plain,
        24, List(24) { "inactive${it + 1}" })
}
