package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.serialization.json.*
import models.apiModels.liveboard.LiveboardOperationIdentity

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardDecisionObservationTest {
    @Test
    fun testEnabledDecisionObservesActualPostRegistration() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                val ticket = c.root.beginInboxDecisionObservation(true)
                val caller = c.fixture.track(c.root.decide(c.identity, true, ticket))
                settle(c, caller)
                val returned = c.session.actions.presentation().single { it.target == LiveboardActionTarget.Decision(c.identity) }
                assertEquals(LiveboardActionPhase.Submitted, returned.phase)
                assertEquals(c.identity.requestId, returned.requestId)
                assertNull(c.session.actions.decisionIntent(c.identity, true), "Retained same intent cannot resubmit")
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
                // No ticket assertion: the no-op scaffold must first execute and retain the real decision.
                assertEquals(1, c.fixture.platform.observationRecords.value.count {
                    it.stage == DecisionObservationStage.POST_REGISTRATION
                }, "Expected exactly one post-registration observation after the real decision")
            }
        }
    }

    @Test
    fun testDefaultOffPreservesDecisionWithoutDiagnosticWork() = runBlocking {
        withTimeout(45_000) {
            withFixture(false) { c ->
                val ticket = c.root.beginInboxDecisionObservation(true)
                assertNull(ticket)
                val caller = c.fixture.track(c.root.decide(c.identity, true, ticket))
                settle(c, caller)
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
                assertEquals(LiveboardActionPhase.Submitted, c.session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }.phase)
                assertEquals(0, c.fixture.platform.observationAttempts.value)
                assertTrue(c.fixture.platform.observationRecords.value.isEmpty())
            }
        }
    }

    @Test
    fun testCompletedNullIntentAndStaleOwnerKeepOutcomeUnobserved() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                val first = c.fixture.track(c.root.decide(c.identity, true, c.root.beginInboxDecisionObservation(true)))
                settle(c, first)
                val prior = c.fixture.platform.observationRecords.value.size
                val duplicate = c.fixture.track(c.root.decide(c.identity, true, c.root.beginInboxDecisionObservation(true)))
                assertTrue(duplicate.isCompleted, "Same command Job completes before handler registration")
                c.io.scheduler.runCurrent()
                duplicate.join()
                val rows = c.fixture.platform.observationRecords.value.drop(prior)
                assertEquals(2, rows.size)
                val initial = rows.single { it.stage == DecisionObservationStage.POST_REGISTRATION }
                val final = rows.single { it.stage == DecisionObservationStage.SAME_JOB_COMPLETION }
                assertEquals(DecisionObservationFlag.ONE, initial.facts.sessionPresent)
                assertEquals(DecisionObservationFlag.ZERO, initial.facts.intentPresent)
                assertEquals(DecisionObservationFlag.ONE, initial.facts.bodyReached)
                assertEquals(DecisionObservationOutcome.UNOBSERVED, final.facts.outcome)
                assertEquals(DecisionObservationCompletion.COMPLETED, final.completion)
                val successor = c.fixture.root(io = c.io)
                assertSame(c.session, c.fixture.session(successor))
                val beforeStale = c.fixture.platform.observationRecords.value.size
                val stale = c.fixture.track(c.root.decide(c.identity, true, c.root.beginInboxDecisionObservation(true)))
                assertTrue(stale.isCompleted)
                c.io.scheduler.runCurrent()
                stale.join()
                val last = c.fixture.platform.observationRecords.value.drop(beforeStale).single {
                    it.stage == DecisionObservationStage.SAME_JOB_COMPLETION
                }
                assertEquals(DecisionObservationFlag.ZERO, last.facts.sessionPresent)
                assertEquals(DecisionObservationFlag.UNKNOWN, last.facts.intentPresent)
                assertEquals(DecisionObservationFlag.ZERO, last.facts.bodyReached)
                assertEquals(DecisionObservationOutcome.UNOBSERVED, last.facts.outcome)
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
            }
        }
    }

    @Test
    fun testAtomicStageReservationsPublishOneImmutableSnapshot() = runBlocking {
        val observations = LiveboardDecisionObservations()
        val ticket = assertNotNull(observations.begin(true, true))
        observations.bodyReached(ticket)
        observations.returned(ticket, DecisionObservationOutcome.ENTERED_SUCCESS)
        val initial = coroutineScope {
            List(16) { async(Dispatchers.Default) {
                observations.postRegistration(ticket, DecisionObservationFlag.ONE, DecisionObservationFlag.ONE)
            } }.awaitAll().filterNotNull()
        }
        assertEquals(1, initial.size)
        val final = coroutineScope {
            List(16) { async(Dispatchers.Default) {
                observations.completed(ticket, DecisionObservationCompletion.COMPLETED)
            } }.awaitAll().filterNotNull()
        }
        assertEquals(1, final.size)
        assertEquals(DecisionObservationFlag.ONE, initial.single().facts.bodyReached)
        assertEquals(DecisionObservationOutcome.ENTERED_SUCCESS, final.single().facts.outcome)
        assertEquals(DecisionObservationCompletion.COMPLETED, final.single().completion)
        assertEquals(initial.single().delivery, final.single().delivery)
        assertEquals(DecisionObservationStage.POST_REGISTRATION, initial.single().stage)
        assertEquals(DecisionObservationStage.SAME_JOB_COMPLETION, final.single().stage)
    }

    @Test
    fun testBudgetAndCodecAcceptOnlyBoundedFixedMetadata() {
        val observations = LiveboardDecisionObservations()
        assertNull(observations.begin(false, true))
        val tickets = List(40) { observations.begin(true, true) }.filterNotNull()
        assertEquals(32, tickets.size)
        val records = tickets.flatMap { ticket ->
            listOfNotNull(observations.postRegistration(ticket, DecisionObservationFlag.UNKNOWN,
                DecisionObservationFlag.UNKNOWN), observations.completed(ticket, DecisionObservationCompletion.COMPLETED))
        }
        assertEquals(64, records.size)
        for (ticket in tickets) {
            assertNull(observations.postRegistration(ticket, DecisionObservationFlag.ONE, DecisionObservationFlag.ONE))
            assertNull(observations.completed(ticket, DecisionObservationCompletion.CANCELLED))
        }
        for (record in records) {
            val line = assertNotNull(record.encoded())
            assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
            assertTrue(line.startsWith("LIVEBOARD_DECISION_OBSERVATION "))
            val payload = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_DECISION_OBSERVATION ")).jsonObject
            assertEquals(setOf("VERSION", "DELIVERY", "STAGE", "RENDERED", "SESSION_PRESENT", "INTENT_PRESENT",
                "BODY_REACHED", "CURRENT", "OUTCOME", "COMPLETION", "REJECTION_SOURCE"), payload.keys)
            assertEquals("3", payload.getValue("VERSION").jsonPrimitive.content)
            assertEquals("UNOBSERVED", payload.getValue("REJECTION_SOURCE").jsonPrimitive.content)
            assertEquals("UNOBSERVED", payload.getValue("CURRENT").jsonPrimitive.content)
            assertFalse(line.any { it in 'a'..'z' })
        }
        for (source in LiveboardDecisionRejectionSource.entries) {
            val longest = LiveboardDecisionObservationRecord(32, DecisionObservationStage.SAME_JOB_COMPLETION,
                DecisionObservationFacts(DecisionObservationFlag.UNKNOWN, outcome =
                    DecisionObservationOutcome.ENTERED_UNAVAILABLE), rejectionSource = source)
            val line = assertNotNull(longest.encoded())
            assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
            val payload = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_DECISION_OBSERVATION ")).jsonObject
            assertEquals("3", payload.getValue("VERSION").jsonPrimitive.content)
            assertEquals(source.name, payload.getValue("REJECTION_SOURCE").jsonPrimitive.content)
        }
        val rejected = LiveboardDecisionObservations()
        val ticket = assertNotNull(rejected.begin(true, false))
        val record = assertNotNull(rejected.rejected(ticket))
        assertEquals(DecisionObservationStage.RENDER_REJECTED, record.stage)
        assertEquals(DecisionObservationFlag.ZERO, record.facts.rendered)
        assertNull(rejected.rejected(ticket))
    }

    @Test
    fun testThrowingSinkCannotReplaceRealDecisionResult() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                c.fixture.platform.observationFailure = IllegalStateException("PRIVATE_DIAGNOSTIC_SENTINEL")
                val ticket = c.root.beginInboxDecisionObservation(true)
                val caller = c.fixture.track(c.root.decide(c.identity, true, ticket))
                settle(c, caller)
                assertFalse(caller.isCancelled)
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
                assertEquals(LiveboardActionPhase.Submitted, c.session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }.phase)
                assertTrue(c.fixture.platform.observationAttempts.value > 0)
                assertTrue(c.fixture.platform.observationRecords.value.isEmpty())
            }
        }
    }

    @Test
    fun testCancelledCallerDoesNotInventReturnedOutcome() = runBlocking {
        withTimeout(45_000) {
            withFixture(true) { c ->
                val gate = c.fixture.hold("accept")
                val ticket = c.root.beginInboxDecisionObservation(true)
                val caller = c.fixture.track(c.root.decide(c.identity, true, ticket))
                gate.entered.await()
                c.io.scheduler.runCurrent()
                caller.cancel()
                joinCaller(c, caller)
                assertTrue(caller.isCancelled)
                assertFalse(gate.exited.isCompleted)
                val final = c.fixture.platform.observationRecords.value.single {
                    it.stage == DecisionObservationStage.SAME_JOB_COMPLETION
                }
                assertEquals(DecisionObservationCompletion.CANCELLED, final.completion)
                assertEquals(DecisionObservationOutcome.UNOBSERVED, final.facts.outcome)
                assertEquals(DecisionObservationFlag.ONE, final.facts.bodyReached)
                gate.release.complete(Unit)
                gate.exited.await()
                c.session.awaitSettled()
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
                val returned = c.session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }
                assertEquals(LiveboardActionPhase.Submitted, returned.phase)
                // Delivery was asserted above while views remained open; close may drop later records.
                c.root.close()
                val remounted = c.fixture.root(io = c.io)
                assertSame(c.session, c.fixture.session(remounted))
                assertEquals(returned, c.session.actions.presentation().single { it.target == returned.target })
                assertNull(c.session.actions.decisionIntent(c.identity, true))
                assertEquals(1, c.fixture.core.calls.value.count { it.name == "accept" })
            }
        }
    }

    private data class Context(val fixture: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val identity: LiveboardOperationIdentity, val io: TestDispatcher)

    private suspend fun withFixture(enabled: Boolean, block: suspend (Context) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = enabled
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(io = io)
            val session = f.session(root)
            val identity = assertNotNull(session.currentMember()).operations.first { it.identity.kind == LiveboardKind.Recover }.identity
            block(Context(f, root, session, identity, io))
        } finally { f.close() }
    }

    private suspend fun settle(c: Context, caller: Job) {
        c.session.awaitSettled()
        joinCaller(c, caller)
    }

    private suspend fun joinCaller(c: Context, caller: Job) {
        while (!caller.isCompleted) {
            c.io.scheduler.runCurrent()
            yield()
        }
        caller.join()
        c.io.scheduler.runCurrent()
    }
}
