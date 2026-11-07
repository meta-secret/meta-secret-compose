package ui.liveboard

import core.metaSecretCore.liveboard.FeatureIds
import core.metaSecretCore.liveboard.NativeCall
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.RecoveredFixtureReply
import core.metaSecretCore.liveboard.RecoveredRevealFixture
import core.metaSecretCore.liveboard.encodedShowObservation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LiveboardShowContinuityGuardTest {
    @Test
    fun testValidBindAndReadRestoreKeepTheSameBusinessTranscript() = runBlocking {
        withTimeout(45_000) {
            val off = observeShowGuardScenario(ShowGuardInput.Valid, false)
            val on = observeShowGuardScenario(ShowGuardInput.Valid, true)
            assertEquals(off.business, on.business)
            assertTrue(off.rows.isEmpty())
            assertShowGuardRow(on.rows, "BIND", "TRUE", mapOf(
                "CAUSE" to "NONE", "A" to "TRUE", "R" to "TRUE", "MATCH" to "TRUE", "BOUND" to "TRUE",
            ), "Valid A/R bind produces exactly one guard record")
            assertShowGuardRow(on.rows, "SELECT", "TRUE", mapOf("A" to "TRUE", "R" to "UNOBSERVED",
                "MATCH" to "UNOBSERVED", "BOUND" to "UNOBSERVED"), "Accepted availability is not permission")
            for (point in listOf("CAPTURE", "STAGE", "RESTORE")) {
                assertShowGuardRow(on.rows, point, "TRUE", mapOf("CAUSE" to "NONE"),
                    "$point records the original winning result")
            }
        }
    }

    @Test
    fun testMissingAcceptedLeavesUnconsultedReceiptUnknownAndDoesNotRestore() = runBlocking {
        withTimeout(45_000) {
            val off = observeShowGuardScenario(ShowGuardInput.MissingAccepted, false)
            val on = observeShowGuardScenario(ShowGuardInput.MissingAccepted, true)
            assertEquals(off.business, on.business)
            assertTrue(off.rows.isEmpty())
            assertShowGuardRow(on.rows, "BIND", "FALSE", mapOf(
                "CAUSE" to "ACCEPTED_ABSENT", "A" to "FALSE", "R" to "UNOBSERVED",
                "MATCH" to "UNOBSERVED", "BOUND" to "UNOBSERVED",
            ), "Absent Accepted records the original short-circuit, not an invented receipt check")
            assertShowGuardRow(on.rows, "SELECT", "FALSE", mapOf("A" to "FALSE", "R" to "UNOBSERVED",
                "MATCH" to "UNOBSERVED", "BOUND" to "UNOBSERVED"), "Absent Accepted is observed at selection")
        }
    }

    @Test
    fun testWellFormedMismatchedReceiptDoesNotBindOrRestoreAndKeepsBusinessTranscript() = runBlocking {
        withTimeout(45_000) {
            val off = observeShowGuardScenario(ShowGuardInput.ReceiptMismatch, false)
            val on = observeShowGuardScenario(ShowGuardInput.ReceiptMismatch, true)
            assertEquals(off.business, on.business)
            assertTrue(off.rows.isEmpty())
            assertShowGuardRow(on.rows, "BIND", "FALSE", mapOf(
                "CAUSE" to "RECEIPT_MISMATCH", "A" to "TRUE", "R" to "TRUE",
                "MATCH" to "FALSE", "BOUND" to "UNOBSERVED",
            ), "A valid but different receipt observes the actual match refusal, not a failed CAS")
            assertTrue(on.rows.none { it["POINT"]?.jsonPrimitive?.content == "RESTORE" })
        }
    }

    @Test
    fun testThrowingSinkKeepsActualRecoveryPublicationAndReadRestoreUnchanged() = runBlocking {
        withTimeout(45_000) {
            val off = observeShowGuardScenario(ShowGuardInput.Valid, false)
            val throwing = observeShowGuardScenario(ShowGuardInput.Valid, true, sinkFailure = true)
            assertEquals(off.business, throwing.business)
            assertTrue(throwing.attempts > 0, "The existing sink was actually attempted")
            assertTrue(throwing.rows.isEmpty(), "A throwing sink cannot fabricate delivered metadata")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testThrowingSinkCannotReplaceActualShowCancellationOrStartNativeWork() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            try {
                RecoveredRevealFixture.install(f)
                f.platform.observationEnabled = true
                f.platform.observationFailure = IllegalStateException("CONTROLLED_SINK_FAILURE")
                f.start()
                val io = StandardTestDispatcher()
                val root = f.root(io = io)
                val session = f.session(root)
                f.auth.held = true
                val calls = f.core.calls.value.toList()
                val reads = f.core.reads.value
                val showing = f.track(root.show("pass-b", assertNotNull(root.beginShowObservation(true))))
                val reply = f.auth.take()
                var completion: Throwable? = null
                showing.invokeOnCompletion { completion = it }
                val cancellation = CancellationException("CONTROLLED_CALLER_CANCEL")
                showing.cancel(cancellation)
                while (!showing.isCompleted) { io.scheduler.runCurrent(); yield() }
                showing.join()
                reply.success()
                io.scheduler.runCurrent()
                assertTrue(showing.isCancelled)
                assertSame(cancellation, assertIs<CancellationException>(completion))
                assertEquals(calls, f.core.calls.value)
                assertEquals(reads, f.core.reads.value)
                assertEquals(1, f.auth.calls)
                assertNull(session.sensitive.copyValue("pass-b"))
                assertTrue(f.platform.showObservationAttempts.value > 0)
                assertTrue(f.platform.allShowObservationRecords.value.isEmpty())
            } finally { f.close() }
        }
    }
}

internal enum class ShowGuardInput { Valid, MissingAccepted, MissingReceipt, ReceiptMismatch }
internal data class ShowGuardSample(
    val business: Triple<List<NativeCall>, Int, Int>,
    val rows: List<JsonObject>,
    val attempts: Int = 0,
)

/** Existing physical ports and controlled wire inputs only; never a copy of a continuity predicate. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun observeShowGuardScenario(input: ShowGuardInput, enabled: Boolean,
    sinkFailure: Boolean = false): ShowGuardSample =
    coroutineScope {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = enabled
            if (sinkFailure) f.platform.observationFailure = IllegalStateException("CONTROLLED_SINK_FAILURE")
            if (input == ShowGuardInput.MissingAccepted) {
                f.core.state.value = RecoveredRevealFixture.member(proof = null)
                f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim,
                    RecoveredRevealFixture.response(), RecoveredRevealFixture.member(done = true))
            } else if (input == ShowGuardInput.ReceiptMismatch) {
                val proof = RecoveredRevealFixture.field(RecoveredRevealFixture.context(),
                    "claimScopeCommitment", JsonPrimitive("56".repeat(32)))
                f.core.state.value = RecoveredRevealFixture.member()
                f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim,
                    RecoveredRevealFixture.response(RecoveredRevealFixture.receipt(proof = proof)),
                    RecoveredRevealFixture.member(done = true))
            } else RecoveredRevealFixture.install(f, receiptPresent = input != ShowGuardInput.MissingReceipt)
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(io = io)
            val session = f.session(root)
            val beforeCalls = f.core.calls.value.size
            val beforeAuth = f.auth.calls
            val beforeReads = f.core.reads.value
            val ticket = root.beginShowObservation(true)
            if (enabled) assertNotNull(ticket) else assertNull(ticket)
            val showing = f.track(root.show("pass-b", ticket))
            while (!showing.isCompleted) { io.scheduler.runCurrent(); yield() }
            showing.join()
            io.scheduler.runCurrent()
            assertFalse(showing.isCancelled)
            val disclosure = assertNotNull(session.sensitive.state.value.revealed["pass-b"])
            assertSame(ticket, disclosure.showObservation)
            assertEquals(RecoveredRevealFixture.value, session.sensitive.copyValue("pass-b"))
            val calls = f.core.calls.value.drop(beforeCalls)
            assertEquals(listOf(NativeCall("recovered", listOf(FeatureIds.claim))), calls)
            assertEquals(beforeAuth + 1, f.auth.calls)

            val scope = assertNotNull(f.initialization.currentScope())
            val target = assertNotNull(f.reads.requireFreshRead(scope))
            val held = f.hold("read")
            val reading = f.track(async(Dispatchers.IO) { f.manager.getStateModel() })
            held.entered.await()
            assertFalse(session.inbox.state.value.ready)
            assertTrue(session.sensitive.state.value.revealed.isEmpty())
            assertNull(session.sensitive.copyValue("pass-b"))
            held.release.complete(Unit)
            held.exited.await()
            reading.await()
            val proof = target.completion.await()
            f.idle()
            io.scheduler.runCurrent()
            assertEquals(scope, proof.scope)
            assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
            assertTrue(target in proof.coveredRequirements)
            assertTrue(session.inbox.state.value.ready)
            if (input == ShowGuardInput.Valid) {
                assertSame(disclosure, session.sensitive.state.value.revealed["pass-b"])
                assertEquals(RecoveredRevealFixture.value, session.sensitive.copyValue("pass-b"))
            } else {
                assertTrue(session.sensitive.state.value.revealed.isEmpty())
                assertNull(session.sensitive.copyValue("pass-b"))
            }
            assertEquals(calls, f.core.calls.value.drop(beforeCalls), "No repeated native Show")
            assertEquals(beforeAuth + 1, f.auth.calls, "No renewed consent")
            assertEquals(beforeReads + 1, f.core.reads.value, "Only the existing physical read")
            val prefix = "LIVEBOARD_SHOW_CONTINUITY "
            val rows = f.platform.allShowObservationRecords.value.mapNotNull { it.encodedShowObservation() }
                .filter { it.startsWith(prefix) }.map { line ->
                    Json.parseToJsonElement(line.removePrefix(prefix)).jsonObject.also { row ->
                        assertEquals("1", row.getValue("VERSION").jsonPrimitive.content)
                        assertEquals("SHOW_CONTINUITY", row.getValue("KIND").jsonPrimitive.content)
                        assertEquals(assertNotNull(ticket).attempt.toString(), row.getValue("ATTEMPT")
                            .jsonPrimitive.content)
                        assertEquals("FIRST_FIXED_GUARD_OBSERVATIONS_ONLY", row.getValue("COVERAGE")
                            .jsonPrimitive.content)
                    }
                }
            ShowGuardSample(Triple(calls, f.auth.calls - beforeAuth, f.core.reads.value - beforeReads), rows,
                f.platform.showObservationAttempts.value)
        } finally { f.close() }
    }

/** Check real emitted records; skipped operands are not filled from earlier records. */
internal fun assertShowGuardRow(rows: List<JsonObject>, point: String, result: String,
    expected: Map<String, String>, message: String) {
    val matched = rows.filter { it.getValue("POINT").jsonPrimitive.content == point &&
        it.getValue("RESULT").jsonPrimitive.content == result }
    assertEquals(1, matched.size, message)
    val row = matched.single()
    assertEquals(setOf("VERSION", "KIND", "ATTEMPT", "POINT", "RESULT", "CAUSE", "A", "R", "MATCH",
        "BOUND", "COVERAGE"), row.keys)
    expected.forEach { (key, value) -> assertEquals(value, row.getValue(key).jsonPrimitive.content, "$point/$key") }
}
