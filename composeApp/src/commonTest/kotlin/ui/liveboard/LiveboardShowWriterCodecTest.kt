package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.serialization.json.*

class LiveboardShowWriterCodecTest {
    @Test
    fun testEncoderRejectsAttributedWriterOnCommitAndCappedEvents() {
        val outcomes = listOf(ShowPostEvent.VALUE_BRANCH_COMMITTED, ShowPostEvent.CAPPED).flatMap { event ->
            ShowSnapshotWriter.entries.filter { it != ShowSnapshotWriter.UNOBSERVED }.map { writer ->
                record(event, writer).encodedShowObservation() == null
            }
        }
        assertEquals(8, outcomes.size)
        assertEquals(8, outcomes.count { it }, "COMMIT/CAPPED reject all eight attributed writer pairs")
    }

    @Test
    fun testAllPermittedWriterEventPairsAtMaxIntegersKeepExactKeysAndFramedLimit() {
        var pairs = 0
        var checked = 0
        for (event in ShowPostEvent.entries) for (writer in ShowSnapshotWriter.entries) {
            if (terminal(event) && writer != ShowSnapshotWriter.UNOBSERVED) continue
            pairs++
            for (store in ShowPostStore.entries) for (maximum in listOf(false, true)) for (loss in listOf(false, true)) {
                val value = record(event, writer, maximum, loss, store)
                val line = assertNotNull(value.encodedShowObservation())
                assertTrue(line.startsWith("LIVEBOARD_SHOW_POST_DELIVERY "))
                assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
                val payload = Json.parseToJsonElement(line.removePrefix("LIVEBOARD_SHOW_POST_DELIVERY ")).jsonObject
                assertEquals(keys, payload.keys)
                assertEquals(1, payload.getValue("VERSION").jsonPrimitive.int)
                assertEquals("SHOW_POST_DELIVERY", payload.getValue("KIND").jsonPrimitive.content)
                assertEquals(if (maximum) 32 else 1, payload.getValue("ATTEMPT").jsonPrimitive.int)
                assertEquals(event.name, payload.getValue("EVENT").jsonPrimitive.content)
                assertEquals(store.name, payload.getValue("STORE").jsonPrimitive.content)
                assertEquals(writer.name, payload.getValue("REASON").jsonPrimitive.content)
                assertEquals(if (event == ShowPostEvent.VALUE_BRANCH_COMMITTED) "VALUE_BRANCH_COMMITTED"
                    else "UNOBSERVED", payload.getValue("COMMIT").jsonPrimitive.content)
                assertEquals(if (maximum) Long.MAX_VALUE else 0L, payload.getValue("ORDER").jsonPrimitive.long)
                assertEquals(if (maximum) Long.MAX_VALUE else 0L, payload.getValue("LAST_PRESENT").jsonPrimitive.long)
                assertEquals(if (maximum) Long.MAX_VALUE else 0L, payload.getValue("LAST_EMPTY").jsonPrimitive.long)
                assertEquals(if (maximum) 65535 else 0, payload.getValue("CYCLES").jsonPrimitive.int)
                assertEquals(if (loss) "TRUE" else "FALSE", payload.getValue("ASSOCIATION_LOSS").jsonPrimitive.content)
                assertEquals("COLLECTED_SNAPSHOTS_ONLY", payload.getValue("COVERAGE").jsonPrimitive.content)
                assertFalse(line.any { it in 'a'..'z' })
                checked++
            }
        }
        assertEquals(22, pairs)
        assertEquals(352, checked, "Every permitted pair, store, numeric endpoint and loss flag must encode")
    }

    @Test
    fun testOneRootLifetimeKeepsOldAndPostBudgetsThroughSaturation() {
        val owner = LiveboardShowObservations()
        val generation = Any()
        val tickets = List(40) { owner.begin(true, true) }.filterNotNull()
        assertEquals(32, tickets.size)
        val old = tickets.flatMap { ticket ->
            assertTrue(owner.claim(ticket))
            ticket.body(true, generation)
            ticket.returned(true)
            listOfNotNull(owner.registered(ticket), owner.completed(ticket, false, false))
        }
        assertEquals(64, old.size)
        val post = mutableListOf<LiveboardShowObservationRecord>()
        post += owner.collected(generation, false, tickets)
        repeat(65535) {
            post += owner.collected(generation, true, emptyList())
            post += owner.collected(generation, false, tickets)
        }
        val commits = tickets.mapNotNull(owner::committed)
        post += commits
        assertEquals(32, commits.size, "Store saturation preserves each independent commit reservation")
        assertEquals(32, post.count { it.postDelivery?.event == ShowPostEvent.CAPPED })
        assertTrue(post.groupBy { it.attempt }.values.all { it.size <= 13 })
        assertTrue(post.size <= 416)
        assertTrue(old.size + post.size <= 480)
        assertTrue(post.all { assertNotNull(it.postDelivery).writer == ShowSnapshotWriter.UNOBSERVED })
        for (row in old + post) {
            val line = assertNotNull(row.encodedShowObservation())
            assertTrue(("E2E_DIAG " + line + "\n").encodeToByteArray().size <= 512)
        }
        assertNull(owner.begin(true, true))
        assertTrue(owner.collected(generation, true, emptyList()).isEmpty())
        assertTrue(tickets.mapNotNull(owner::committed).isEmpty())
        owner.close()
        assertTrue(owner.collected(generation, false, tickets).isEmpty())
    }

    private fun record(event: ShowPostEvent, writer: ShowSnapshotWriter, maximum: Boolean = true,
        loss: Boolean = false, store: ShowPostStore = ShowPostStore.MAP_EMPTY): LiveboardShowObservationRecord {
        val order = if (maximum) Long.MAX_VALUE else 0L
        return LiveboardShowObservationRecord(if (maximum) 32 else 1, ShowPostFacts(event, order, store,
            if (maximum) 65535 else 0, order, order, loss, writer))
    }

    private fun terminal(event: ShowPostEvent) =
        event == ShowPostEvent.VALUE_BRANCH_COMMITTED || event == ShowPostEvent.CAPPED

    private val keys = setOf("VERSION", "KIND", "ATTEMPT", "EVENT", "ORDER", "STORE", "CYCLES",
        "LAST_PRESENT", "LAST_EMPTY", "ASSOCIATION_LOSS", "COVERAGE", "REASON", "COMMIT")
}
