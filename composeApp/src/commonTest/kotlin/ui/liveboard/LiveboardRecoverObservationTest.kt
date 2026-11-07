package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardDecisionRejectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/** Actual primitive owner/helper API; no mirrored action or admission model. */
class LiveboardRecoverObservationTest {
    @Test
    fun testSixTicketsBoundEveryStageAndCodecWithoutPayloadFields() {
        val owner = LiveboardRecoverObservations()
        assertNull(owner.begin(false))
        assertTrue(owner.continuous)
        val tickets = List(6) { assertNotNull(owner.begin(true)) }
        val rows = tickets.flatMap { t ->
            listOf(assertNotNull(owner.callback(t)), assertNotNull(owner.registered(t,
                RecoverObservationFlag.ONE, RecoverObservationFlag.ONE))).also { owner.body(t) } +
                listOf(assertNotNull(owner.returned(t, RecoverObservationOutcome.ENTERED_SUCCESS)),
                    assertNotNull(owner.completed(t, RecoverObservationCompletion.COMPLETED)))
        }
        assertEquals((1..6).toSet(), rows.map { it.sequence }.toSet())
        assertEquals(24, rows.size)
        for (ticket in tickets) {
            assertNull(owner.callback(ticket))
            assertNull(owner.registered(ticket, RecoverObservationFlag.ZERO, RecoverObservationFlag.ZERO))
            assertNull(owner.returned(ticket, RecoverObservationOutcome.ENTERED_UNCERTAIN))
            assertNull(owner.completed(ticket, RecoverObservationCompletion.THREW))
        }
        val longest = LiveboardDecisionRejectionSource.entries.maxBy { it.name.length }
        for (outcome in RecoverObservationOutcome.entries) for (completion in RecoverObservationCompletion.entries) {
            val row = LiveboardRecoverObservationRecord(6, RecoverObservationStage.SAME_JOB_COMPLETION,
                RecoverObservationFacts(outcome = outcome), completion, longest)
            val encoded = assertNotNull(row.encodedRecoverObservation())
            assertTrue(encoded.encodeToByteArray().size <= 256)
            assertTrue(encoded.startsWith("E2E: RECOVER_ACTION V1 ") && encoded.endsWith("\n"))
            assertEquals(setOf("SEQ", "STAGE", "S", "I", "B", "O", "C", "R"),
                encoded.trimEnd().split(' ').drop(3).map { it.substringBefore('=') }.toSet())
            assertFalse(encoded.any { it in 'a'..'z' })
        }
        for (invalid in listOf(0, 7, Int.MAX_VALUE)) assertNull(LiveboardRecoverObservationRecord(
            invalid, RecoverObservationStage.ROOT_CALLBACK, RecoverObservationFacts(),
            RecoverObservationCompletion.UNOBSERVED, LiveboardDecisionRejectionSource.UNOBSERVED,
        ).encodedRecoverObservation())
        assertNull(owner.begin(true))
        owner.close()
        assertFalse(owner.continuous, "Ticket seven overflow must remain UNKNOWN at close")
    }

    @Test
    fun testConcurrentStageReservationsAndForeignOwnerNeverCrossBind() = runBlocking {
        withTimeout(45_000) {
            val owner = LiveboardRecoverObservations()
            val tickets = List(6) { assertNotNull(owner.begin(true)) }
            val callbacks = coroutineScope {
                tickets.flatMap { t -> List(8) { async(Dispatchers.Default) { owner.callback(t) } } }
                    .awaitAll().filterNotNull()
            }
            assertEquals(6, callbacks.size)
            assertEquals((1..6).toSet(), callbacks.map { it.sequence }.toSet())
            val returned = coroutineScope {
                tickets.mapIndexed { index, t -> async(Dispatchers.Default) {
                    owner.body(t)
                    owner.returned(t, if (index % 2 == 0) RecoverObservationOutcome.ENTERED_SUCCESS
                        else RecoverObservationOutcome.ENTERED_UNCERTAIN)
                } }.awaitAll().map { assertNotNull(it) }
            }
            returned.forEach {
                assertEquals(if (it.sequence % 2 == 1) RecoverObservationOutcome.ENTERED_SUCCESS else
                    RecoverObservationOutcome.ENTERED_UNCERTAIN, it.facts.outcome)
            }
            // RETURNED before registration is legal; recording must not normalize product scheduling.
            tickets.forEach {
                assertNotNull(owner.registered(it, RecoverObservationFlag.ONE, RecoverObservationFlag.ONE))
                assertNotNull(owner.completed(it, RecoverObservationCompletion.COMPLETED))
            }
            owner.close()
            assertTrue(owner.continuous)
            val foreign = LiveboardRecoverObservations()
            assertNull(foreign.callback(tickets.first()))
            assertFalse(foreign.continuous)
            assertTrue(owner.continuous)
            assertNull(owner.returned(tickets.first(), RecoverObservationOutcome.ENTERED_UNAVAILABLE))
            assertFalse(owner.continuous, "Late callbacks cannot certify a closed observation interval")
        }
    }

    @Test
    fun testCloseRequiresCallbackRegistrationAndActualRequiredReturn() {
        for (omitted in RecoverObservationStage.entries) {
            val owner = LiveboardRecoverObservations()
            val ticket = assertNotNull(owner.begin(true))
            if (omitted != RecoverObservationStage.ROOT_CALLBACK) assertNotNull(owner.callback(ticket))
            if (omitted != RecoverObservationStage.POST_REGISTRATION) assertNotNull(owner.registered(ticket,
                RecoverObservationFlag.ONE, RecoverObservationFlag.ONE))
            owner.body(ticket)
            if (omitted != RecoverObservationStage.RETURNED) assertNotNull(owner.returned(ticket,
                RecoverObservationOutcome.ENTERED_SUCCESS))
            if (omitted != RecoverObservationStage.SAME_JOB_COMPLETION) assertNotNull(owner.completed(ticket,
                RecoverObservationCompletion.COMPLETED))
            owner.close()
            assertFalse(owner.continuous, "Missing $omitted cannot certify coverage")
            assertNull(owner.completed(ticket, RecoverObservationCompletion.COMPLETED))
        }
        val owner = LiveboardRecoverObservations()
        val ticket = assertNotNull(owner.begin(true))
        owner.callback(ticket)
        owner.registered(ticket, RecoverObservationFlag.ONE, RecoverObservationFlag.ZERO)
        owner.body(ticket)
        owner.completed(ticket, RecoverObservationCompletion.COMPLETED)
        owner.close()
        assertTrue(owner.continuous, "A null intent has no controller return to invent")
    }

    @Test
    fun testHelperReturnsSameJobAndOverflowOrGetterFailureNeverSkipsOriginalRegistration() {
        for (getterFailure in listOf(false, true)) {
            val owner = LiveboardRecoverObservations()
            val platform = Platform()
            val original = Job().also { it.complete() }
            var registrations = 0
            repeat(6) {
                val returned = observeRecover(owner, RecoverObservationInput(null, CANARY, platform) {
                    registrations += 1
                    original
                })
                assertSame<Any>(original, returned)
            }
            if (getterFailure) platform.getterFailure = IllegalStateException(CANARY)
            assertSame<Any>(original, observeRecover(owner, RecoverObservationInput(null, CANARY, platform) {
                registrations += 1
                original
            }))
            assertEquals(7, registrations)
            closeRecoverObservation(owner, platform)
            assertEquals(listOf(false), platform.closes)
            assertFalse(owner.continuous)
            assertEquals(6, platform.rows.count { it.stage == RecoverObservationStage.ROOT_CALLBACK })
            platform.rows.forEach { assertFalse(assertNotNull(it.encodedRecoverObservation()).contains(CANARY)) }
        }
        val owner = LiveboardRecoverObservations()
        val platform = Platform().also { it.enabled = false }
        val original = Job().also { it.complete() }
        var registrations = 0
        assertSame<Any>(original, observeRecover(owner, RecoverObservationInput(null, CANARY, platform) {
            registrations += 1
            original
        }))
        assertEquals(1, registrations)
        assertTrue(platform.rows.isEmpty())
    }

    @Test
    fun testThrowingLoggerCannotReplaceSameOriginalCommandThrowableOrExposeCanary() {
        val beforeFlush = LiveboardRecoverObservations()
        val notDelivered = Platform().also { it.sinkFailure = IllegalArgumentException(CANARY) }
        val original = IllegalStateException(CANARY)
        var throwingRegistrations = 0
        val actual = assertFailsWith<IllegalStateException> {
            observeRecover(beforeFlush, RecoverObservationInput(null, CANARY, notDelivered) {
                throwingRegistrations += 1
                throw original
            })
        }
        assertSame<Any>(original, actual)
        assertEquals(1, throwingRegistrations)
        assertTrue(notDelivered.rows.isEmpty(), "Registration threw before the passive buffer flush")
        closeRecoverObservation(beforeFlush, notDelivered)
        assertFalse(beforeFlush.continuous, "Incomplete observation becomes UNKNOWN at close")
        assertEquals(listOf(false), notDelivered.closes)

        val duringFlush = LiveboardRecoverObservations()
        val throwingSink = Platform().also { it.sinkFailure = IllegalArgumentException(CANARY) }
        val originalJob = Job().also { it.complete() }
        var healthyRegistrations = 0
        val returned = observeRecover(duringFlush, RecoverObservationInput(null, CANARY, throwingSink) {
            healthyRegistrations += 1
            originalJob
        })
        assertSame<Any>(originalJob, returned)
        assertEquals(1, healthyRegistrations)
        assertTrue(throwingSink.rows.isNotEmpty(), "Actual sink must be invoked after successful registration")
        assertFalse(duringFlush.continuous)
        closeRecoverObservation(duringFlush, throwingSink)
        assertEquals(listOf(false), throwingSink.closes)
        throwingSink.rows.forEach {
            val line = assertNotNull(it.encodedRecoverObservation())
            assertFalse(line.contains(CANARY) || line.contains("IllegalArgumentException") ||
                line.contains("IllegalStateException"))
        }
    }

    private class Platform : LiveboardPlatformPort {
        var enabled = true
        var getterFailure: Throwable? = null
        var sinkFailure: Throwable? = null
        val rows = mutableListOf<LiveboardRecoverObservationRecord>()
        val closes = mutableListOf<Boolean>()
        override val recoverObservationEnabled: Boolean get() {
            getterFailure?.let { throw it }
            return enabled
        }
        override fun emitRecoverObservation(record: LiveboardRecoverObservationRecord) {
            rows.add(record)
            sinkFailure?.let { throw it }
        }
        override fun closeRecoverObservation(continuous: Boolean) { closes.add(continuous) }
        override fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment = error("No attach")
        override suspend fun readClipboard(): String? = error("No clipboard")
        override fun copyText(value: String) = error("No copy")
    }
    private companion object { const val CANARY = "PRIVATE_SECRET_CLAIM_DEVICE_VAULT_RAW_ERROR_CANARY" }
}
