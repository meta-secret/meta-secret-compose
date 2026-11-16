package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardShowWriterContractTest {
    @Test
    fun testActualRootHardConcealNamesItsOwnSnapshotWithoutAdditionalWork() = runBlocking {
        withTimeout(45_000) {
            withRoot { c ->
                val ticket = assertNotNull(show(c, "pass-b"))
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                val reads = c.f.core.reads.value
                c.f.notifications.showError("Synthetic writer hard-conceal error")
                c.io.scheduler.runCurrent()
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.copyValue("pass-b"))
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
                assertEquals(reads, c.f.core.reads.value)
                assertWriter(c, ticket, ShowSnapshotWriter.CONCEAL)
                c.f.proof(); c.f.idle()
                assertNull(c.session.sensitive.copyValue("pass-b"))
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
            }
        }
    }

    @Test
    fun testSnapshotFiveArgumentConstructorAndCopiesKeepBusinessValueAndNoopSuppression() = runBlocking {
        val constructor: (LiveboardDraft?, Map<String, LiveboardDisclosure>, Boolean, String,
            LiveboardCreateCompletion?) -> LiveboardSensitiveSnapshot = ::LiveboardSensitiveSnapshot
        val draft = draft()
        val disclosure = LiveboardDisclosure("fixture-card", "synthetic contract input")
        val completion = LiveboardCreateCompletion()
        val original = constructor(draft, mapOf("fixture-card" to disclosure), true, "", completion)
        val marked = LiveboardSensitiveSnapshot.copyRevealed(original, original.revealed, ShowSnapshotWriter.HIDE_CARD)
        assertNotSame(original, marked)
        assertNull(original.showWriter)
        assertEquals(ShowSnapshotWriter.HIDE_CARD, marked.showWriter)
        assertEquals(original, marked)
        assertEquals(original.hashCode(), marked.hashCode())
        val (authored, revealed, open, hint, finished) = marked
        assertSame(draft, authored); assertSame(original.revealed, revealed)
        assertTrue(open); assertEquals("", hint); assertSame(completion, finished)
        assertEquals("LiveboardSensitiveSnapshot(redacted)", marked.toString())
        val copied = marked.copy()
        assertNotSame(marked, copied)
        assertNull(copied.showWriter)
        assertEquals(marked, copied)
        val flow = MutableStateFlow(original)
        var emissions = 0
        val subscriber = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { emissions++ } }
        try {
            flow.value = marked
            yield()
            assertEquals(1, emissions)
            assertSame(original, flow.value, "Equal candidate cannot replace or relabel the published object")
        } finally { subscriber.cancelAndJoin() }
    }

    @Test
    fun testActualRootOnAndOffKeepSameBusinessEmissionsAndDisclosureIdentity() = runBlocking {
        withTimeout(60_000) {
            assertEquals(transcript(false), transcript(true))
        }
    }

    @Test
    fun testHeldRootCollectorDistinguishesNonEqualAndEqualEmptySuccessors() = runBlocking {
        withTimeout(60_000) {
            for (different in listOf(false, true)) {
                val ui = NativeCheckedIo()
                withRoot(ui = ui) { c ->
                    c.root.editForm(draft()); assertTrue(c.root.openCreate())
                    val ticket = assertNotNull(show(c, "pass-b"))
                    val calls = c.f.core.calls.value.toList()
                    val auth = c.f.auth.calls
                    val reads = c.f.core.reads.value
                    ui.queue()
                    c.session.sensitive.hide("pass-b")
                    val hidden = c.session.sensitive.state.value
                    assertEquals(ShowSnapshotWriter.HIDE_CARD, hidden.showWriter)
                    if (different) c.session.sensitive.background() else c.session.sensitive.routeChanged()
                    if (different) {
                        assertNotSame(hidden, c.session.sensitive.state.value)
                        assertNull(c.session.sensitive.state.value.draft)
                    } else assertSame(hidden, c.session.sensitive.state.value,
                        "An equal empty CONCEAL candidate cannot overwrite the published HIDE_CARD")
                    c.f.finishControlled(ui)
                    c.io.scheduler.runCurrent()
                    assertWriter(c, ticket, if (different) ShowSnapshotWriter.CONCEAL else ShowSnapshotWriter.HIDE_CARD)
                    assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                    assertNull(c.session.sensitive.copyValue("pass-b"))
                    assertEquals(calls, c.f.core.calls.value)
                    assertEquals(auth, c.f.auth.calls)
                    assertEquals(reads, c.f.core.reads.value)
                }
            }
        }
    }

    @Test
    fun testRootReentryNamesGuardAdmittedSnapshotWithoutClaimingCurrentState() = runBlocking {
        withTimeout(45_000) {
            withRoot { c ->
                c.root.editForm(draft()); assertTrue(c.root.openCreate())
                val ticket = assertNotNull(show(c, "pass-b"))
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                val reads = c.f.core.reads.value
                val entered = CompletableDeferred<Unit>()
                var armed = false
                var returned = false
                var inline = false
                val callback = c.f.track(launch(c.f.main, CoroutineStart.UNDISPATCHED) {
                    c.root.state.collect { presentation ->
                        if (armed && presentation.chrome.overlay == LiveboardRootOverlay.None) {
                            armed = false
                            c.root.editForm(draft())
                            assertTrue(c.root.openCreate())
                            inline = !returned
                            entered.complete(Unit)
                        }
                    }
                })
                try {
                    armed = true
                    c.session.sensitive.conceal(clearDraft = true)
                    returned = true
                    entered.await()
                    c.io.scheduler.runCurrent()
                    assertTrue(inline, "Existing Root presentation callback must finish before outer conceal returns")
                    assertEquals(LiveboardRootOverlay.Create, c.root.state.value.chrome.overlay)
                    assertEquals(draft(), c.session.sensitive.state.value.draft)
                    assertTrue(c.session.sensitive.state.value.createOpen)
                    assertNull(c.session.sensitive.state.value.showWriter,
                        "Current ordinary copy is newer than the already guard-admitted CONCEAL sample")
                    assertWriter(c, ticket, ShowSnapshotWriter.CONCEAL)
                    assertEquals(calls, c.f.core.calls.value)
                    assertEquals(auth, c.f.auth.calls)
                    assertEquals(reads, c.f.core.reads.value)
                } finally { callback.cancelAndJoin() }
            }
        }
    }

    private data class Context(val f: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val io: TestDispatcher)
    private data class Business(val cards: Map<String, Int>, val draft: Boolean, val open: Boolean,
        val pasteEmpty: Boolean, val completed: Boolean)
    private data class Transcript(val emissions: List<Business>, val routes: List<String>, val auth: Int, val reads: Int)

    private suspend fun withRoot(enabled: Boolean = true, ui: CoroutineDispatcher? = null,
        provedRecovery: Boolean = false,
        block: suspend (Context) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = enabled
            if (provedRecovery) RecoveredRevealFixture.install(f)
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(ui = ui ?: f.main, io = io)
            block(Context(f, root, f.session(root), io))
        } finally { f.close() }
    }

    private suspend fun show(c: Context, id: String): LiveboardShowObservationTicket? {
        val before = c.f.core.calls.value.size
        val auth = c.f.auth.calls
        val ticket = c.root.beginShowObservation(true)
        val caller = c.f.track(c.root.show(id, ticket))
        while (!caller.isCompleted) { c.io.scheduler.runCurrent(); yield() }
        caller.join(); c.io.scheduler.runCurrent()
        assertFalse(caller.isCancelled)
        assertEquals(auth + 1, c.f.auth.calls)
        val call = c.f.core.calls.value.drop(before).single()
        assertEquals(if (id == "pass-a") "local" else "recovered", call.name)
        assertEquals(listOf(if (id == "pass-a") id else FeatureIds.claim), call.arguments)
        val disclosure = assertNotNull(c.session.sensitive.state.value.revealed[id])
        assertSame(ticket, disclosure.showObservation)
        if (ticket != null) assertTrue(c.f.platform.postDeliveryObservationRecords.value.any {
            it.attempt == ticket.attempt && it.postDelivery?.store == ShowPostStore.TICKET_PRESENT
        })
        return ticket
    }

    private fun assertWriter(c: Context, ticket: LiveboardShowObservationTicket, writer: ShowSnapshotWriter) {
        val row = c.f.platform.postDeliveryObservationRecords.value.single {
            it.attempt == ticket.attempt && it.postDelivery?.event == ShowPostEvent.FIRST_EMPTY
        }
        assertEquals(writer, row.postDelivery!!.writer,
            "Writer of the guard-admitted object, not necessarily the first ticket loss or current snapshot")
    }

    private suspend fun transcript(enabled: Boolean): Transcript {
        var result: Transcript? = null
        withRoot(enabled, provedRecovery = true) { c ->
            val emissions = mutableListOf<Business>()
            val beforeCalls = c.f.core.calls.value.size
            val beforeAuth = c.f.auth.calls
            val beforeReads = c.f.core.reads.value
            val collector = c.f.track(CoroutineScope(currentCoroutineContext()).launch(
                c.f.main, CoroutineStart.UNDISPATCHED) {
                c.session.sensitive.state.collect { emissions += project(it) }
            })
            try {
                c.root.editForm(draft()); assertTrue(c.root.openCreate())
                show(c, "pass-a"); show(c, "pass-b")
                val first = assertNotNull(c.session.sensitive.state.value.revealed["pass-a"])
                val second = assertNotNull(c.session.sensitive.state.value.revealed["pass-b"])
                val scope = assertNotNull(c.f.initialization.currentScope())
                val target = assertNotNull(c.f.reads.requireFreshRead(scope))
                val gate = c.f.hold("read")
                val caller = c.f.track(CoroutineScope(currentCoroutineContext()).async(Dispatchers.IO) {
                    c.f.manager.getStateModel()
                })
                gate.entered.await()
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.copyValue("pass-a")); assertNull(c.session.sensitive.copyValue("pass-b"))
                gate.release.complete(Unit); gate.exited.await(); caller.await()
                assertEquals(NativeReadOutcome.SUCCESS, target.completion.await().outcome)
                c.f.idle(); c.io.scheduler.runCurrent()
                assertSame(first, c.session.sensitive.state.value.revealed["pass-a"])
                assertSame(second, c.session.sensitive.state.value.revealed["pass-b"])
                assertNotNull(c.session.sensitive.copyValue("pass-a")); assertNotNull(c.session.sensitive.copyValue("pass-b"))
                val restored = c.session.sensitive.state.value
                if (enabled) {
                    val emitter = assertNotNull(c.root.showObservationEmitter)
                    repeat(8) { emitter(assertNotNull(second.showObservation)) }
                } else {
                    assertNull(c.root.showObservationEmitter)
                    assertEquals(0, c.f.platform.showObservationAttempts.value)
                }
                c.io.scheduler.runCurrent()
                assertSame(restored, c.session.sensitive.state.value)
                c.session.sensitive.hide("pass-a")
                assertSame(second, c.session.sensitive.state.value.revealed["pass-b"])
                c.f.notifications.showError("Synthetic ON/OFF writer error")
                c.session.sensitive.background()
                c.io.scheduler.runCurrent()
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.state.value.draft)
                val routes = c.f.core.calls.value.drop(beforeCalls).map { it.name }
                assertEquals(listOf("local", "recovered"), routes)
                assertEquals(2, c.f.auth.calls - beforeAuth)
                assertEquals(1, c.f.core.reads.value - beforeReads)
                result = Transcript(emissions.toList(), routes, c.f.auth.calls - beforeAuth,
                    c.f.core.reads.value - beforeReads)
            } finally { collector.cancelAndJoin() }
        }
        return assertNotNull(result)
    }

    private fun project(snapshot: LiveboardSensitiveSnapshot): Business {
        val cards = snapshot.revealed.mapValues { (id, disclosure) ->
            val tag = if (id == "pass-a") 1 else 2
            assertTrue(id in listOf("pass-a", "pass-b"))
            val expected = if (tag == 1) "local fixture value"
                else "one two three four five six seven eight nine ten eleven twelve"
            assertTrue(expected == disclosure.value, "Controlled fixture payload content must remain exact")
            tag
        }
        if (snapshot.draft != null) assertEquals(draft(), snapshot.draft)
        assertEquals("", snapshot.pasteHint)
        return Business(cards, snapshot.draft != null, snapshot.createOpen, snapshot.pasteHint.isEmpty(),
            snapshot.createCompletion != null)
    }

    private fun draft() = LiveboardDraft("Writer fixture draft", "Synthetic writer draft input")
}
