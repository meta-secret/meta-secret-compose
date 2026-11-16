package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardShowPostDeliveryObservationTest {
    @Test
    fun testDisclosureEqualityCopyAndActualBusinessEmissionsRemainNeutral() = runBlocking {
        val owner = LiveboardShowObservations()
        val ticket = assertNotNull(owner.begin(true, true))
        val constructor: (String, String) -> LiveboardDisclosure = ::LiveboardDisclosure
        val original = constructor("fixture-card", "fixture-value")
        val observed = LiveboardDisclosure.observed("fixture-card", "fixture-value", ticket)
        assertTrue(original == observed)
        assertEquals(original.hashCode(), observed.hashCode())
        val (id, value) = observed
        assertTrue(id == original.secretId && value == original.value)
        assertSame(ticket, observed.showObservation)
        val copied = observed.copy()
        assertTrue(copied == observed)
        assertNull(copied.showObservation)
        assertEquals("LiveboardDisclosure(redacted)", observed.toString())
        assertEquals(businessEmissions(false), businessEmissions(true))
        val flow = MutableStateFlow(LiveboardSensitiveSnapshot(revealed = mapOf("fixture-card" to original)))
        var emissions = 0
        val subscriber = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { emissions++ } }
        try {
            flow.value = flow.value.copy(revealed = mapOf("fixture-card" to observed))
            yield()
            assertEquals(1, emissions, "Metadata must not defeat original equal-value suppression")
            assertSame(original, flow.value.revealed.values.single())
        } finally { subscriber.cancelAndJoin() }
    }

    @Test
    fun testActualReadBurstKeepsSameTicketsAndLateCommitDoesNotRestoreStore() = runBlocking {
        withTimeout(60_000) {
            withFixture(true, provedRecovery = true) { c ->
                val disclosures = mutableListOf<LiveboardDisclosure>()
                for (id in listOf("pass-a", "pass-b")) {
                    val ticket = assertNotNull(c.root.beginShowObservation(true))
                    join(c, c.f.track(c.root.show(id, ticket)))
                    disclosures += assertNotNull(c.session.sensitive.state.value.revealed[id])
                    assertSame(ticket, disclosures.last().showObservation)
                }
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                repeat(64) {
                    val scope = assertNotNull(c.f.initialization.currentScope())
                    val target = assertNotNull(c.f.reads.requireFreshRead(scope))
                    val gate = c.f.hold("read")
                    val caller = c.f.track(async(Dispatchers.IO) { c.f.manager.getStateModel() })
                    gate.entered.await()
                    assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                    gate.release.complete(Unit)
                    gate.exited.await()
                    caller.await()
                    assertEquals(NativeReadOutcome.SUCCESS, target.completion.await().outcome)
                    c.f.idle()
                    for (disclosure in disclosures) assertSame(disclosure,
                        c.session.sensitive.state.value.revealed[disclosure.secretId])
                }
                c.io.scheduler.runCurrent()
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
                c.f.notifications.showError("Synthetic post-delivery retirement")
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                val emitter = assertNotNull(c.root.showObservationEmitter)
                for (disclosure in disclosures) repeat(9) { emitter(assertNotNull(disclosure.showObservation)) }
                c.io.scheduler.runCurrent()
                val all = c.f.platform.postDeliveryObservationRecords.value
                for (disclosure in disclosures) {
                    val ticket = assertNotNull(disclosure.showObservation)
                    val rows = all.filter { it.attempt == ticket.attempt }
                    assertTrue(rows.size <= 13)
                    assertEquals(1, rows.count { it.postDelivery?.event == ShowPostEvent.VALUE_BRANCH_COMMITTED })
                    val committed = rows.single { it.postDelivery?.event == ShowPostEvent.VALUE_BRANCH_COMMITTED }
                    assertEquals(ShowPostStore.MAP_EMPTY, assertNotNull(committed.postDelivery).store)
                    assertEquals(64, committed.postDelivery!!.cycles)
                    assertEquals(listOf(1), rows.filter {
                        it.postDelivery?.event == ShowPostEvent.FIRST_REAPPEARANCE
                    }.map { it.postDelivery!!.cycles })
                    assertEquals(listOf(4, 16, 64), rows.filter {
                        it.postDelivery?.event == ShowPostEvent.CYCLE_SUMMARY
                    }.map { it.postDelivery!!.cycles })
                    assertTrue(rows.none { it.postDelivery?.event == ShowPostEvent.CAPPED })
                    assertCodec(rows, mapOf(ShowPostEvent.FIRST_EMPTY to ShowSnapshotWriter.READ_MASK,
                        ShowPostEvent.FIRST_REAPPEARANCE to ShowSnapshotWriter.READ_RESTORE,
                        ShowPostEvent.CYCLE_SUMMARY to ShowSnapshotWriter.READ_RESTORE))
                }
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
                assertTrue(c.f.platform.unrecognizedShowObservationRecords.value.isEmpty())
            }
        }
    }

    @Test
    fun testHeldCollectorConflationAndLostAssociationStayUnknown() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            try {
                f.platform.observationEnabled = true
                f.start()
                val ui = StandardTestDispatcher()
                val io = StandardTestDispatcher()
                val root = f.root(ui = ui, io = io)
                val session = f.session(root)
                val ticket = assertNotNull(root.beginShowObservation(true))
                val caller = f.track(root.show("pass-a", ticket))
                while (!caller.isCompleted) { io.scheduler.runCurrent(); yield() }
                caller.join()
                val disclosed = assertNotNull(session.sensitive.state.value.revealed["pass-a"])
                session.sensitive.hide("pass-a")
                ui.scheduler.runCurrent()
                io.scheduler.runCurrent()
                val rows = f.platform.postDeliveryObservationRecords.value
                assertTrue(rows.isEmpty(), "A fully conflated business publication has no collected sample")
                assertNotNull(root.showObservationEmitter).invoke(assertNotNull(disclosed.showObservation))
                io.scheduler.runCurrent()
                val committed = f.platform.postDeliveryObservationRecords.value.single()
                assertEquals(ticket.attempt, committed.attempt)
                val facts = assertNotNull(committed.postDelivery)
                assertEquals(ShowPostEvent.VALUE_BRANCH_COMMITTED, facts.event)
                assertEquals(ShowPostStore.UNOBSERVED, facts.store)
                assertEquals(0, facts.cycles)
                assertEquals(0L, facts.lastPresent)
                assertEquals(0L, facts.lastEmpty)
                assertTrue(session.sensitive.state.value.revealed.isEmpty())
                val owner = LiveboardShowObservations()
                val own = assertNotNull(owner.begin(true, true))
                assertTrue(owner.claim(own))
                val generation = Any()
                own.body(true, generation)
                own.returned(true)
                val copied = LiveboardDisclosure.observed("fixture-card", "fixture-value", own).copy()
                assertNull(copied.showObservation)
                val missing = owner.collected(generation, false, listOfNotNull(copied.showObservation))
                assertEquals(ShowPostStore.NONEMPTY_TICKET_UNOBSERVED,
                    assertNotNull(missing.single().postDelivery).store)
                assertTrue(missing.single().postDelivery!!.loss)
                assertTrue(owner.collected(Any(), true, emptyList()).isEmpty())
            } finally { f.close() }
        }
    }

    @Test
    fun testAtomicSemanticReservationsAndCodecRemainBounded() = runBlocking {
        val owner = LiveboardShowObservations()
        val generation = Any()
        val ticket = assertNotNull(owner.begin(true, true))
        assertTrue(owner.claim(ticket))
        ticket.body(true, generation)
        ticket.returned(true)
        val records = mutableListOf<LiveboardShowObservationRecord>()
        records += owner.collected(generation, false, listOf(ticket))
        repeat(65_535) {
            records += owner.collected(generation, true, emptyList())
            records += owner.collected(generation, false, listOf(ticket))
        }
        val commits = coroutineScope {
            List(32) { async(Dispatchers.Default) { owner.committed(ticket) } }.awaitAll().filterNotNull()
        }
        records += commits
        assertEquals(1, commits.size, "Store saturation cannot spend the independent commit reservation")
        assertEquals(1, records.count { it.postDelivery?.event == ShowPostEvent.CAPPED })
        assertTrue(records.size <= 13)
        assertCodec(records)
        assertTrue(owner.collected(generation, true, emptyList()).isEmpty())
        val foreign = LiveboardShowObservations()
        assertNull(foreign.committed(ticket))
        owner.close()
        assertNull(owner.committed(ticket))
        assertTrue(owner.collected(generation, false, listOf(ticket)).isEmpty())
        val bounded = LiveboardShowObservations()
        val issued = List(40) { bounded.begin(true, true) }.filterNotNull()
        assertEquals(32, issued.size)
        assertNull(bounded.begin(true, true))
        val platform = RootPlatform()
        val oldRecord = LiveboardShowObservationRecord(1, ShowObservationStage.POST_REGISTRATION,
            ShowObservationFacts(ShowObservationFlag.TRUE))
        val unknown = LiveboardShowObservationRecord(0, ShowObservationStage.POST_REGISTRATION,
            ShowObservationFacts(ShowObservationFlag.UNKNOWN))
        for (record in listOf(oldRecord, commits.single(), unknown)) platform.emitShowObservation(record)
        assertEquals(3, platform.showObservationAttempts.value)
        assertEquals(listOf(oldRecord, commits.single(), unknown), platform.allShowObservationRecords.value)
        assertEquals(listOf(oldRecord), platform.showObservationRecords.value)
        assertEquals(commits, platform.postDeliveryObservationRecords.value)
        assertEquals(listOf(unknown), platform.unrecognizedShowObservationRecords.value)
    }

    @Test
    fun testDefaultOffAndThrowingSinkPreserveOriginalWorkAndCancellation() = runBlocking {
        withTimeout(45_000) {
            withFixture(false) { c ->
                val originalShow: (String) -> Job = c.root::show
                assertNull(c.root.beginShowObservation(true))
                join(c, c.f.track(originalShow("pass-a")))
                assertNotNull(c.session.sensitive.copyValue("pass-a"))
                assertNull(c.root.showObservationEmitter)
                assertNull(c.session.sensitive.state.value.revealed["pass-a"]?.showObservation)
                assertEquals(0, c.f.platform.showObservationAttempts.value)
                assertTrue(c.f.platform.allShowObservationRecords.value.isEmpty())
            }
            withFixture(true) { c ->
                c.f.platform.observationFailure = IllegalStateException("POST_DIAGNOSTIC_FAILURE")
                val ticket = assertNotNull(c.root.beginShowObservation(true))
                join(c, c.f.track(c.root.show("pass-a", ticket)))
                assertNotNull(c.session.sensitive.copyValue("pass-a"))
                assertTrue(c.f.platform.showObservationAttempts.value > 0)
                assertTrue(c.f.platform.allShowObservationRecords.value.isEmpty())
                c.f.auth.held = true
                val cancelled = c.f.track(c.root.show("pass-a",
                    assertNotNull(c.root.beginShowObservation(true))))
                val reply = c.f.auth.take()
                cancelled.cancel()
                join(c, cancelled)
                reply.success()
                assertTrue(cancelled.isCancelled)
                assertEquals(1, c.f.core.calls.value.count { it.name == "local" })
                assertNull(c.session.sensitive.copyValue("pass-a"))
                assertTrue(c.f.platform.allShowObservationRecords.value.isEmpty())
            }
        }
    }

    @Test
    fun testLateCommitAndRetiredOwnerCannotPublishIntoSuccessor() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                val ticket = assertNotNull(c.root.beginShowObservation(true))
                join(c, c.f.track(c.root.show("pass-a", ticket)))
                val emitter = assertNotNull(c.root.showObservationEmitter)
                c.session.sensitive.hide("pass-a")
                c.io.scheduler.runCurrent()
                val beforeState = c.session.sensitive.state.value
                val beforeCalls = c.f.core.calls.value.toList()
                val beforeAuth = c.f.auth.calls
                repeat(16) { emitter(ticket) }
                c.io.scheduler.runCurrent()
                assertSame(beforeState, c.session.sensitive.state.value)
                assertEquals(beforeCalls, c.f.core.calls.value)
                assertEquals(beforeAuth, c.f.auth.calls)
                val commit = c.f.platform.postDeliveryObservationRecords.value.single {
                    it.postDelivery?.event == ShowPostEvent.VALUE_BRANCH_COMMITTED
                }
                assertEquals(ShowPostStore.MAP_EMPTY, commit.postDelivery!!.store)
                c.root.close()
                c.io.scheduler.runCurrent()
                val beforeRecords = c.f.platform.allShowObservationRecords.value.size
                emitter(ticket)
                c.io.scheduler.runCurrent()
                assertEquals(beforeRecords, c.f.platform.allShowObservationRecords.value.size)
                val other = LiveboardShowObservations()
                assertNull(other.committed(ticket))
            }
        }
    }

    private data class Context(val f: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val io: TestDispatcher)

    private suspend fun withFixture(enabled: Boolean, provedRecovery: Boolean = false,
        block: suspend (Context) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = enabled
            if (provedRecovery) RecoveredRevealFixture.install(f)
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(io = io)
            block(Context(f, root, f.session(root), io))
        } finally { f.close() }
    }

    private suspend fun join(c: Context, caller: Job) {
        while (!caller.isCompleted) { c.io.scheduler.runCurrent(); yield() }
        caller.join()
        c.io.scheduler.runCurrent()
    }

    private suspend fun businessEmissions(enabled: Boolean): List<Pair<Int, Boolean>> = withTimeout(45_000) {
        val seen = mutableListOf<Pair<Int, Boolean>>()
        withFixture(enabled) { c ->
            val collector = c.f.track(CoroutineScope(currentCoroutineContext()).launch(
                c.f.main, CoroutineStart.UNDISPATCHED) {
                c.session.sensitive.state.collect { seen += it.revealed.size to it.createOpen }
            })
            try {
                val ticket = c.root.beginShowObservation(true)
                join(c, c.f.track(c.root.show("pass-a", ticket)))
                val disclosure = assertNotNull(c.session.sensitive.state.value.revealed["pass-a"])
                if (ticket != null) {
                    val emitter = assertNotNull(c.root.showObservationEmitter)
                    repeat(16) { emitter(assertNotNull(disclosure.showObservation)) }
                    c.io.scheduler.runCurrent()
                }
                c.session.sensitive.hide("pass-a")
                c.io.scheduler.runCurrent()
                assertEquals(1, c.f.auth.calls)
                assertEquals(1, c.f.core.calls.value.count { it.name == "local" })
            } finally { collector.cancelAndJoin() }
        }
        seen
    }

    private fun assertCodec(records: List<LiveboardShowObservationRecord>,
        writers: Map<ShowPostEvent, ShowSnapshotWriter> = emptyMap()) {
        for (record in records) {
            assertEquals(ShowObservationVariant.POST_DELIVERY_V1, record.variant)
            val line = assertNotNull(record.encodedShowObservation())
            assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
            val payload = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_SHOW_POST_DELIVERY ")).jsonObject
            assertEquals(setOf("VERSION", "KIND", "ATTEMPT", "EVENT", "ORDER", "STORE", "CYCLES",
                "LAST_PRESENT", "LAST_EMPTY", "ASSOCIATION_LOSS", "COVERAGE", "REASON", "COMMIT"), payload.keys)
            assertEquals("SHOW_POST_DELIVERY", payload.getValue("KIND").jsonPrimitive.content)
            assertEquals((writers[assertNotNull(record.postDelivery).event] ?: ShowSnapshotWriter.UNOBSERVED).name,
                payload.getValue("REASON").jsonPrimitive.content)
            assertEquals("COLLECTED_SNAPSHOTS_ONLY", payload.getValue("COVERAGE").jsonPrimitive.content)
            assertFalse(line.any { it in 'a'..'z' })
        }
    }
}
