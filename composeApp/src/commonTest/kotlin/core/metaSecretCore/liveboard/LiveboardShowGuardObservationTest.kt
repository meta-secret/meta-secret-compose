package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.*
import ui.liveboard.ShowGuardInput
import ui.liveboard.assertShowGuardRow
import ui.liveboard.observeShowGuardScenario

class LiveboardShowGuardObservationTest {
    @Test
    fun testMissingReceiptIsObservedWithoutInventingAMatchOrRestoration() = runBlocking {
        withTimeout(45_000) {
            val off = observeShowGuardScenario(ShowGuardInput.MissingReceipt, false)
            val on = observeShowGuardScenario(ShowGuardInput.MissingReceipt, true)
            assertEquals(off.business, on.business)
            assertTrue(off.rows.isEmpty())
            assertShowGuardRow(on.rows, "BIND", "FALSE", mapOf(
                "CAUSE" to "RECEIPT_ABSENT", "A" to "TRUE", "R" to "FALSE",
                "MATCH" to "UNOBSERVED", "BOUND" to "UNOBSERVED",
            ), "Absent receipt produces exactly one actual bind refusal record")
        }
    }

    @Test
    fun testClosedImplementedCodebookRejectsEveryUnsupportedTypedTuple() {
        val legal = legalRows()
        assertEquals(12, legal.size)
        for (point in ShowGuardPoint.entries) for (result in ShowGuardResult.entries) {
            for (cause in ShowGuardCause.entries) for (flags in flagRows()) {
                val fact = ShowGuardFacts(point, result, cause,
                    ShowGuardValues(flags[0], flags[1], flags[2], flags[3]))
                assertEquals(fact in legal, encodedShowGuard(1, fact) != null, "Closed typed codebook: $fact")
            }
        }
        for (attempt in listOf(Int.MIN_VALUE, 0, 33, Int.MAX_VALUE)) {
            assertNull(encodedShowGuard(attempt, legal.first()))
            assertNull(LiveboardShowObservationRecord(attempt, legal.first()).encodedShowObservation())
        }
        for (attempt in 1..32) legal.forEach { assertWire(attempt, it) }
    }

    @Test
    fun testInvalidOrdinaryFactsCannotWakeOrSpendTheFirstReservation() {
        val legal = legalRows()
        val invalid = listOf(
            legal[0].copy(values = ShowGuardValues(accepted = no)) to legal[0],
            legal[7].copy(values = ShowGuardValues(yes, no, yes, yes)) to legal[7],
            legal[8].copy(result = ShowGuardResult.FALSE) to legal[8],
            legal[11].copy(point = ShowGuardPoint.RETIRE) to legal[0],
        )
        for ((bad, valid) in invalid) {
            val wake = ShowGuardWake()
            val observer = LiveboardShowGuardObserver(wake)
            val before = wake.changes.value
            observer.note(bad)
            assertSame(before, wake.changes.value, "Rejected metadata must not wake a collector")
            assertTrue(observer.drain().isEmpty(), "Rejected metadata must not reserve a slot")
            observer.note(valid)
            assertEquals(listOf(valid), observer.drain(), "No invalid row or premature CAPPED sentinel")
            assertTrue(observer.drain().isEmpty())
        }
    }

    @Test
    fun testOwnerCloseForeignAndLateTicketsCannotReachASuccessor() {
        val first = LiveboardShowObservations()
        val next = LiveboardShowObservations()
        repeat(40) { assertNull(first.begin(false, true)) }
        val old = assertNotNull(first.begin(true, true))
        assertEquals(1, old.attempt, "Disabled observation spends no attempt")
        assertFalse(next.claim(old))
        assertTrue(first.claim(old))
        old.guards.note(legalRows().first())
        assertTrue(next.guardRecords().isEmpty())
        first.close()
        assertFalse(first.guardsOpen())
        assertNull(first.begin(true, true))
        assertTrue(first.guardRecords().isEmpty(), "Close discards even reserved, undrained metadata")
        old.guards.note(legalRows()[7])
        assertTrue(first.guardRecords().isEmpty())
        val fresh = assertNotNull(next.begin(true, true))
        assertEquals(1, fresh.attempt)
        assertFalse(next.claim(old))
        fresh.guards.note(legalRows()[1])
        assertEquals(listOf(legalRows()[1]), next.guardRecords().map { it.guardFacts })
        assertTrue(next.guardRecords().isEmpty())
        next.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testConflatedWakeKeepsDistinctFactsAndImmutableFirstResultWithOneCap() = runBlocking {
        val owner = LiveboardShowObservations()
        val ticket = assertNotNull(owner.begin(true, true))
        val io = StandardTestDispatcher()
        val batches = mutableListOf<List<ShowGuardFacts?>>()
        val collector = launch(io) { owner.guardChanges.collect {
            val records = owner.guardRecords()
            if (records.isNotEmpty()) batches.add(records.map { it.guardFacts })
        } }
        try {
            io.scheduler.runCurrent()
            val selected = legalRows()[0]
            val firstRefusal = legalRows()[2]
            ticket.guards.note(selected)
            ticket.guards.note(firstRefusal)
            assertTrue(batches.isEmpty(), "The actual StateFlow collector is held, not a copied queue")
            io.scheduler.runCurrent()
            assertEquals(listOf(listOf<ShowGuardFacts?>(selected, firstRefusal)), batches)
            val frozen = batches.first().toList()
            repeat(20) { ticket.guards.note(legalRows()[5]) }
            io.scheduler.runCurrent()
            assertEquals(frozen, batches.first(), "Later false facts cannot rewrite the first observed operands")
            assertEquals(listOf(legalRows()[11]), batches.drop(1).flatten())
            assertTrue(owner.guardRecords().isEmpty())
            ticket.guards.note(legalRows()[8])
            owner.close()
            io.scheduler.runCurrent()
            assertEquals(2, batches.size, "A queued wake after close cannot publish the reserved CAPTURE")
        } finally { owner.close(); collector.cancel(); io.scheduler.runCurrent(); collector.join() }
    }

    @Test
    fun testThirtyTwoAttemptsAndImplementedPointReservationsStayWithinRootCaps() {
        val owner = LiveboardShowObservations()
        val points = legalRows().filter { it.point != ShowGuardPoint.CONTROL }
            .distinctBy { it.point to it.result }
        val tickets = (1..32).map { expected ->
            assertNotNull(owner.begin(true, true)).also { assertEquals(expected, it.attempt) }
        }
        assertNull(owner.begin(true, true))
        tickets.forEach { ticket ->
            points.forEach(ticket.guards::note)
            repeat(20) { ticket.guards.note(points.first()) }
        }
        val records = owner.guardRecords()
        assertEquals(32 * (points.size + 1), records.size)
        assertTrue(records.size <= 416)
        records.groupBy { it.attempt }.values.forEach { group ->
            assertTrue(group.size <= 13)
            assertEquals(1, group.count { it.guardFacts?.result == ShowGuardResult.CAPPED })
            assertEquals(points, group.mapNotNull { it.guardFacts }.filter { it.point != ShowGuardPoint.CONTROL })
        }
        assertTrue(owner.guardRecords().isEmpty())
        owner.close()
    }

    private fun assertWire(attempt: Int, fact: ShowGuardFacts) {
        val line = assertNotNull(encodedShowGuard(attempt, fact))
        assertEquals(line, LiveboardShowObservationRecord(attempt, fact).encodedShowObservation())
        assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
        val row = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_SHOW_CONTINUITY ")).jsonObject
        assertEquals(setOf("VERSION", "KIND", "ATTEMPT", "POINT", "RESULT", "CAUSE", "A", "R", "MATCH",
            "BOUND", "COVERAGE"), row.keys)
        assertFalse(row.getValue("VERSION").jsonPrimitive.isString)
        assertFalse(row.getValue("ATTEMPT").jsonPrimitive.isString)
        row.filterKeys { it !in setOf("VERSION", "ATTEMPT") }.values.forEach {
            assertTrue(it.jsonPrimitive.isString)
        }
        assertEquals("1", row.getValue("VERSION").jsonPrimitive.content)
        assertEquals(attempt.toString(), row.getValue("ATTEMPT").jsonPrimitive.content)
        assertEquals("SHOW_CONTINUITY", row.getValue("KIND").jsonPrimitive.content)
        assertEquals("FIRST_FIXED_GUARD_OBSERVATIONS_ONLY", row.getValue("COVERAGE").jsonPrimitive.content)
        assertEquals(listOf(fact.point.name, fact.result.name, fact.cause.name, fact.values.accepted.name,
            fact.values.receipt.name, fact.values.match.name, fact.values.bound.name),
            listOf("POINT", "RESULT", "CAUSE", "A", "R", "MATCH", "BOUND").map {
                row.getValue(it).jsonPrimitive.content
            })
    }

    private fun flagRows(): List<List<ShowGuardFlag>> = List(4) { ShowGuardFlag.entries }
        .fold(listOf(emptyList())) { prefixes, flags ->
            prefixes.flatMap { prefix -> flags.map { prefix + it } }
        }

    /** Fixed implemented wire shapes, not an alternative encoder or a copy of a continuity predicate. */
    private fun legalRows(): List<ShowGuardFacts> = listOf(
        ShowGuardFacts(ShowGuardPoint.SELECT, ShowGuardResult.TRUE, values = ShowGuardValues(accepted = yes)),
        ShowGuardFacts(ShowGuardPoint.SELECT, ShowGuardResult.FALSE, values = ShowGuardValues(accepted = no)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.ACCEPTED_ABSENT,
            ShowGuardValues(accepted = no)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.DONE_REQUIRED,
            ShowGuardValues(accepted = yes)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.RECEIPT_ABSENT,
            ShowGuardValues(yes, no)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.RECEIPT_MISMATCH,
            ShowGuardValues(yes, yes, no)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.BIND_NOT_WON,
            ShowGuardValues(yes, yes, yes)),
        ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.TRUE, values = ShowGuardValues(yes, yes, yes, yes)),
        ShowGuardFacts(ShowGuardPoint.CAPTURE, ShowGuardResult.TRUE),
        ShowGuardFacts(ShowGuardPoint.STAGE, ShowGuardResult.TRUE),
        ShowGuardFacts(ShowGuardPoint.RESTORE, ShowGuardResult.TRUE),
        ShowGuardFacts(ShowGuardPoint.CONTROL, ShowGuardResult.CAPPED, ShowGuardCause.CAPPED),
    )
}

private val yes = ShowGuardFlag.TRUE
private val no = ShowGuardFlag.FALSE
