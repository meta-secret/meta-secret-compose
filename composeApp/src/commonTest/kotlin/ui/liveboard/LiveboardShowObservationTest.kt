package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardShowObservationTest {
    @Test
    fun testActualRootShowObservesBothOriginalRoutes() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                for (id in listOf("pass-a", "pass-b")) {
                    val beforeCalls = c.fixture.core.calls.value.size
                    val beforeAuth = c.fixture.auth.calls
                    val beforeRecords = c.fixture.platform.showObservationRecords.value.size
                    val ticket = assertNotNull(c.root.beginShowObservation(true))
                    val caller = c.fixture.track(c.root.show(id, ticket))
                    joinCaller(c, caller)
                    assertFalse(caller.isCancelled)
                    assertEquals(beforeAuth + 1, c.fixture.auth.calls)
                    val calls = c.fixture.core.calls.value.drop(beforeCalls)
                    assertEquals(1, calls.size)
                    assertEquals(if (id == "pass-a") "local" else "recovered", calls.single().name)
                    assertTrue(calls.single().arguments == listOf(if (id == "pass-a") id else FeatureIds.claim))
                    assertNotNull(c.session.sensitive.state.value.revealed[id])
                    assertNotNull(c.session.sensitive.copyValue(id))
                    val rows = c.fixture.platform.showObservationRecords.value.drop(beforeRecords)
                    assertEquals(2, rows.size)
                    val initial = rows.single { it.stage == ShowObservationStage.POST_REGISTRATION }
                    val final = rows.single { it.stage == ShowObservationStage.SAME_JOB_COMPLETION }
                    assertEquals(initial.attempt, final.attempt)
                    assertEquals(ShowObservationFlag.TRUE, final.facts.rendered)
                    assertEquals(ShowObservationFlag.TRUE, final.facts.session)
                    assertEquals(ShowObservationFlag.TRUE, final.facts.progress.returned)
                    assertEquals(ShowObservationPhase.UI_DELIVERY_TRUE, final.facts.progress.phase)
                    assertEquals(ShowObservationRaw.VALUE, final.facts.progress.raw)
                    assertTrue(final.facts.progress.checkedCallStarted)
                    assertEquals(ShowObservationCompletion.COMPLETED, final.completion)
                    assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, final.rejection)
                }
            }
        }
    }

    @Test
    fun testDefaultOffActualRootShowOffersNoDiagnosticWork() = runBlocking {
        withTimeout(45_000) {
            withFixture(false) { c ->
                assertNull(c.root.beginShowObservation(true))
                val originalShow: (String) -> Job = c.root::show
                val beforeAuth = c.fixture.auth.calls
                val caller = c.fixture.track(originalShow("pass-a"))
                joinCaller(c, caller)
                assertFalse(caller.isCancelled)
                assertEquals(beforeAuth + 1, c.fixture.auth.calls)
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "local" })
                assertNotNull(c.session.sensitive.copyValue("pass-a"))
                assertEquals(0, c.fixture.platform.showObservationAttempts.value)
                assertTrue(c.fixture.platform.showObservationRecords.value.isEmpty())
            }
        }
    }

    @Test
    fun testHeldCancelledShowKeepsSameJobAndUnknownReturn() = runBlocking {
        withTimeout(45_000) {
            for (mode in listOf("auth", "native")) withFixture(true) { c ->
                c.fixture.auth.held = mode == "auth"
                val gate = if (mode == "native") c.fixture.hold("recovered") else null
                val ticket = assertNotNull(c.root.beginShowObservation(true))
                val caller = c.fixture.track(c.root.show("pass-b", ticket))
                val reply = if (mode == "auth") c.fixture.auth.take() else null
                gate?.entered?.await()
                c.io.scheduler.runCurrent()
                assertFalse(caller.isCompleted)
                assertTrue(c.fixture.platform.showObservationRecords.value.none {
                    it.stage == ShowObservationStage.SAME_JOB_COMPLETION
                })
                caller.cancel()
                if (gate != null) {
                    assertFalse(gate.exited.isCompleted)
                    assertFalse(caller.isCompleted, "Original native hold must drain before same Job completion")
                    gate.release.complete(Unit)
                    gate.exited.await()
                }
                joinCaller(c, caller)
                reply?.success?.invoke()
                c.io.scheduler.runCurrent()
                assertTrue(caller.isCancelled)
                val final = c.fixture.platform.showObservationRecords.value.single {
                    it.stage == ShowObservationStage.SAME_JOB_COMPLETION
                }
                assertEquals(ShowObservationCompletion.CANCELLED, final.completion)
                assertEquals(ShowObservationFlag.UNKNOWN, final.facts.progress.returned)
                assertEquals(mode == "native", final.facts.progress.checkedCallStarted)
                assertEquals(if (mode == "native") 1 else 0,
                    c.fixture.core.calls.value.count { it.name == "recovered" })
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.copyValue("pass-b"))
            }
        }
    }

    @Test
    fun testImmediateRootCompletionReservesImmutableRecordsOnce() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                val missing = c.fixture.track(c.root.show("absent-selection",
                    assertNotNull(c.root.beginShowObservation(true))))
                assertTrue(missing.isCompleted)
                joinCaller(c, missing)
                val firstRows = c.fixture.platform.showObservationRecords.value
                assertEquals(2, firstRows.size)
                val first = firstRows.single { it.stage == ShowObservationStage.SAME_JOB_COMPLETION }
                assertEquals(ShowObservationPhase.NO_SELECTION, first.facts.progress.phase)
                assertEquals(ShowObservationFlag.FALSE, first.facts.progress.returned)
                val successor = c.fixture.root(io = c.io)
                assertSame(c.session, c.fixture.session(successor))
                val stale = c.fixture.track(c.root.show("pass-a", assertNotNull(c.root.beginShowObservation(true))))
                assertTrue(stale.isCompleted)
                joinCaller(c, stale)
                val rows = c.fixture.platform.showObservationRecords.value.drop(firstRows.size)
                assertEquals(2, rows.size)
                val final = rows.single { it.stage == ShowObservationStage.SAME_JOB_COMPLETION }
                assertEquals(ShowObservationFlag.FALSE, final.facts.session)
                assertEquals(ShowObservationFlag.UNKNOWN, final.facts.progress.returned)
                assertEquals(ShowObservationCompletion.COMPLETED, final.completion)
                assertEquals(first, firstRows.single { it.stage == ShowObservationStage.SAME_JOB_COMPLETION })
                assertTrue(c.fixture.core.calls.value.none { it.name in listOf("local", "recovered") })
                assertEquals(0, c.fixture.auth.calls)
            }
        }
    }

    @Test
    fun testActualQueuedShowPublishesAdmissionRejectionOnce() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                val session = f.start()
                val observations = LiveboardShowObservations()
                val ticket = assertNotNull(observations.begin(true, true))
                assertTrue(observations.claim(ticket))
                ticket.body(true)
                f.io.queue()
                val actual = f.track(async(f.main, CoroutineStart.UNDISPATCHED) {
                    session.sensitive.show("pass-a", ticket).also(ticket::returned)
                })
                val pending = f.io.take()
                val initial = assertNotNull(observations.registered(ticket))
                session.sensitive.hide("pass-a")
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, ticket.rejection.source())
                f.io.resumeInline()
                f.run(pending, this).join()
                assertFalse(actual.await())
                assertEquals(1, f.auth.calls)
                assertTrue(f.core.calls.value.none { it.name == "local" })
                assertTrue(f.disclosuresEmpty(session))
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, ticket.rejection.source())
                val final = assertNotNull(observations.completed(ticket, false, false))
                assertEquals(LiveboardDecisionRejectionSource.ADMIT_SCOPE_OR_SELECTION_INVALID, final.rejection)
                assertFalse(final.facts.progress.checkedCallStarted)
                assertEquals(ShowObservationRaw.RETIRED, final.facts.progress.raw)
                assertEquals(ShowObservationFlag.FALSE, final.facts.progress.returned)
                assertNull(observations.completed(ticket, true, true))
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, initial.rejection)
                assertEquals(final.rejection, ticket.rejection.source())
            } finally { f.close() }
        }
    }

    @Test
    fun testAtomicOwnerReservationsAndBudgetCodecStayBounded() = runBlocking {
        val observations = LiveboardShowObservations()
        val foreign = LiveboardShowObservations()
        val ticket = assertNotNull(observations.begin(true, true))
        assertTrue(observations.claim(ticket))
        assertFalse(observations.claim(ticket))
        assertFalse(foreign.claim(ticket))
        assertNull(foreign.registered(ticket))
        assertNull(foreign.completed(ticket, false, false))
        ticket.body(true)
        val initial = coroutineScope {
            List(16) { async(Dispatchers.Default) { observations.registered(ticket) } }.awaitAll().filterNotNull()
        }
        assertEquals(1, initial.size)
        ticket.phase(ShowObservationPhase.UI_DELIVERY_TRUE)
        ticket.returned(true)
        val final = coroutineScope {
            List(16) { async(Dispatchers.Default) { observations.completed(ticket, false, false) } }
                .awaitAll().filterNotNull()
        }
        assertEquals(1, final.size)
        assertEquals(ShowObservationPhase.ROOT_BODY, initial.single().facts.progress.phase)
        ticket.phase(ShowObservationPhase.NOT_READY)
        assertEquals(ShowObservationPhase.UI_DELIVERY_TRUE, final.single().facts.progress.phase)
        val bounded = LiveboardShowObservations()
        assertNull(bounded.begin(false, true))
        val tickets = List(40) { bounded.begin(true, true) }.filterNotNull()
        assertEquals(32, tickets.size)
        val records = tickets.flatMap {
            assertTrue(bounded.claim(it))
            listOfNotNull(bounded.registered(it), bounded.completed(it, false, false))
        }
        assertEquals(64, records.size)
        assertNull(bounded.begin(true, true))
        for (record in records) assertCodec(record)
        for (source in LiveboardDecisionRejectionSource.entries) {
            for (phase in ShowObservationPhase.entries) {
                assertCodec(LiveboardShowObservationRecord(32, ShowObservationStage.SAME_JOB_COMPLETION,
                    ShowObservationFacts(ShowObservationFlag.UNKNOWN, progress = ShowObservationProgress(
                        phase, true, ShowObservationRaw.UNOBSERVED, ShowObservationFlag.UNKNOWN)),
                    ShowObservationCompletion.UNOBSERVED, source))
            }
        }
        val rejectedOwner = LiveboardShowObservations()
        assertNull(rejectedOwner.begin(false, false))
        val rejected = assertNotNull(rejectedOwner.begin(true, false))
        assertNotNull(rejectedOwner.rejected(rejected))
        assertNull(rejectedOwner.rejected(rejected))
        assertFalse(rejectedOwner.claim(rejected))
        assertNull(rejectedOwner.completed(rejected, false, false))
    }

    @Test
    fun testThrowingShowSinkCannotReplaceOriginalReturnOrCancellation() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                c.fixture.platform.observationFailure = IllegalStateException("PRIVATE_SHOW_DIAGNOSTIC_SENTINEL")
                val caller = c.fixture.track(c.root.show("pass-a", assertNotNull(c.root.beginShowObservation(true))))
                joinCaller(c, caller)
                assertFalse(caller.isCancelled)
                assertNotNull(c.session.sensitive.copyValue("pass-a"))
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "local" })
                assertTrue(c.fixture.platform.showObservationAttempts.value > 0)
                assertTrue(c.fixture.platform.showObservationRecords.value.isEmpty())
                c.fixture.auth.held = true
                val cancelled = c.fixture.track(c.root.show("pass-a",
                    assertNotNull(c.root.beginShowObservation(true))))
                val reply = c.fixture.auth.take()
                cancelled.cancel()
                joinCaller(c, cancelled)
                reply.success()
                assertTrue(cancelled.isCancelled)
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "local" })
                assertNull(c.session.sensitive.copyValue("pass-a"))
                assertTrue(c.fixture.platform.showObservationRecords.value.isEmpty())
            }
        }
    }

    private data class Context(val fixture: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val io: TestDispatcher)

    private suspend fun withFixture(enabled: Boolean, block: suspend (Context) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = enabled
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(io = io)
            block(Context(f, root, f.session(root), io))
        } finally { f.close() }
    }

    private suspend fun joinCaller(c: Context, caller: Job) {
        while (!caller.isCompleted) { c.io.scheduler.runCurrent(); yield() }
        caller.join()
        c.io.scheduler.runCurrent()
    }

    private fun assertCodec(record: LiveboardShowObservationRecord) {
        val line = assertNotNull(record.encodedShowObservation())
        assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
        assertTrue(line.startsWith("LIVEBOARD_SHOW_OBSERVATION "))
        val payload = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_SHOW_OBSERVATION ")).jsonObject
        assertEquals(setOf("VERSION", "KIND", "ATTEMPT", "STAGE", "RENDERED", "SESSION", "PHASE", "ENTRY",
            "RAW", "RETURNED", "COMPLETION", "REJECTION_SOURCE"), payload.keys)
        assertEquals("1", payload.getValue("VERSION").jsonPrimitive.content)
        assertEquals("SHOW", payload.getValue("KIND").jsonPrimitive.content)
        assertFalse(line.any { it in 'a'..'z' })
    }
}
