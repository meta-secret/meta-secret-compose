package ui.liveboard

import core.metaSecretCore.liveboard.FeatureIds
import core.metaSecretCore.liveboard.RecoveredRevealFixture
import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.LiveboardShowObservationRecord
import core.metaSecretCore.liveboard.LiveboardShowObservationTicket
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.ShowPostEvent
import core.metaSecretCore.liveboard.ShowPostStore
import core.metaSecretCore.liveboard.encodedShowObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class LiveboardShowWriterObservationTest {
    @Test
    fun testActualRootRecoveredShowThenHideNamesTheCollectedHideSnapshot() = runBlocking {
        withTimeout(45_000) {
            withRoot { c ->
                val ticket = showRecovered(c)
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                val reads = c.f.core.reads.value

                c.session.sensitive.hide("pass-b")
                c.io.scheduler.runCurrent()

                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.copyValue("pass-b"))
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
                assertEquals(reads, c.f.core.reads.value)
                val empty = sample(c, ticket, ShowPostEvent.FIRST_EMPTY)
                assertEquals(ShowPostStore.MAP_EMPTY, empty.postDelivery!!.store)
                assertReason(empty, "HIDE_CARD")
            }
        }
    }

    @Test
    fun testActualHeldReadNamesMaskAndSameSelectionRestoreWithoutAnotherShow() = runBlocking {
        withTimeout(45_000) {
            withRoot(provedRecovery = true) { c ->
                val ticket = showRecovered(c)
                val disclosure = assertNotNull(c.session.sensitive.state.value.revealed["pass-b"])
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                val reads = c.f.core.reads.value
                val scope = assertNotNull(c.f.initialization.currentScope())
                val target = assertNotNull(c.f.reads.requireFreshRead(scope))
                val held = c.f.hold("read")
                val caller = c.f.track(async(Dispatchers.IO) { c.f.manager.getStateModel() })
                held.entered.await()

                assertFalse(c.session.inbox.state.value.ready)
                assertTrue(c.session.sensitive.state.value.revealed.isEmpty())
                assertNull(c.session.sensitive.copyValue("pass-b"))
                c.io.scheduler.runCurrent()
                val empty = sample(c, ticket, ShowPostEvent.FIRST_EMPTY)

                held.release.complete(Unit)
                held.exited.await()
                caller.await()
                val proof = target.completion.await()
                c.f.idle()
                c.io.scheduler.runCurrent()

                assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
                assertEquals(scope, proof.scope)
                assertTrue(target in proof.coveredRequirements)
                assertTrue(c.session.inbox.state.value.ready)
                assertSame(disclosure, c.session.sensitive.state.value.revealed["pass-b"])
                assertSame(ticket, disclosure.showObservation)
                assertNotNull(c.session.sensitive.copyValue("pass-b"))
                assertEquals(calls, c.f.core.calls.value)
                assertEquals(auth, c.f.auth.calls)
                assertEquals(reads + 1, c.f.core.reads.value)
                val restored = sample(c, ticket, ShowPostEvent.FIRST_REAPPEARANCE)
                assertEquals(ShowPostStore.MAP_EMPTY, empty.postDelivery!!.store)
                assertEquals(ShowPostStore.TICKET_PRESENT, restored.postDelivery!!.store)
                assertReason(empty, "READ_MASK")
                assertReason(restored, "READ_RESTORE")
            }
        }
    }

    @Test
    fun testChangedFullSelectionAndRemovedSecretStayConcealedWithoutInventingAnotherWriter() = runBlocking {
        withTimeout(60_000) {
            val collected = mutableListOf<LiveboardShowObservationRecord>()
            for (change in listOf("claim", "removed")) withRoot { c ->
                val ticket = showRecovered(c)
                val calls = c.f.core.calls.value.toList()
                val auth = c.f.auth.calls
                if (change == "claim") c.f.core.primary("showRecovered", "replacement-claim", "pass-b")
                else c.f.core.state.value = withoutSecrets(c.f.core.state.value)

                c.f.proof()
                c.f.idle()
                c.io.scheduler.runCurrent()

                assertNull(c.session.sensitive.state.value.revealed["pass-b"])
                assertNull(c.session.sensitive.copyValue("pass-b"))
                val empty = sample(c, ticket, ShowPostEvent.FIRST_EMPTY)
                assertEquals(ShowPostStore.MAP_EMPTY, empty.postDelivery!!.store)
                // Retirement during an existing empty mask may be business-equal and suppressed.
                assertTrue(rows(c, ticket).none { it.postDelivery?.event == ShowPostEvent.FIRST_REAPPEARANCE })

                c.f.core.fresh()
                c.f.proof()
                c.f.idle()
                c.io.scheduler.runCurrent()

                assertNull(c.session.sensitive.state.value.revealed["pass-b"])
                assertNull(c.session.sensitive.copyValue("pass-b"))
                assertEquals(calls, c.f.core.calls.value, "Canonical reads cannot repeat a native Show")
                assertEquals(auth, c.f.auth.calls, "Canonical reads cannot renew OS consent")
                assertTrue(rows(c, ticket).none { it.postDelivery?.event == ShowPostEvent.FIRST_REAPPEARANCE })
                showRecovered(c)
                assertEquals(calls.size + 1, c.f.core.calls.value.size)
                assertEquals(auth + 1, c.f.auth.calls)
                collected += empty
            }
            assertEquals(2, collected.size, "Both physical canonical retirement variants must reach their controls")
            collected.forEach { assertReason(it, "READ_MASK") }
        }
    }

    private data class Context(val f: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val io: TestDispatcher)

    private suspend fun withRoot(provedRecovery: Boolean = false, block: suspend (Context) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.observationEnabled = true
            if (provedRecovery) RecoveredRevealFixture.install(f)
            f.start()
            val io = StandardTestDispatcher()
            val root = f.root(io = io)
            block(Context(f, root, f.session(root), io))
        } finally { f.close() }
    }

    private suspend fun showRecovered(c: Context): LiveboardShowObservationTicket {
        val calls = c.f.core.calls.value.size
        val auth = c.f.auth.calls
        val ticket = assertNotNull(c.root.beginShowObservation(true))
        val caller = c.f.track(c.root.show("pass-b", ticket))
        finish(c, caller)
        assertFalse(caller.isCancelled)
        assertEquals(auth + 1, c.f.auth.calls)
        assertEquals(1, c.f.core.calls.value.drop(calls).size)
        assertEquals("recovered", c.f.core.calls.value.last().name)
        assertEquals(listOf(FeatureIds.claim), c.f.core.calls.value.last().arguments)
        val disclosure = assertNotNull(c.session.sensitive.state.value.revealed["pass-b"])
        assertSame(ticket, disclosure.showObservation)
        assertEquals(ShowPostStore.TICKET_PRESENT, sample(c, ticket, ShowPostEvent.FIRST_SAMPLE).postDelivery!!.store)
        return ticket
    }

    private suspend fun finish(c: Context, caller: Job) {
        while (!caller.isCompleted) { c.io.scheduler.runCurrent(); yield() }
        caller.join()
        c.io.scheduler.runCurrent()
    }

    private fun rows(c: Context, ticket: LiveboardShowObservationTicket): List<LiveboardShowObservationRecord> =
        c.f.platform.postDeliveryObservationRecords.value.filter { it.attempt == ticket.attempt }

    private fun sample(c: Context, ticket: LiveboardShowObservationTicket,
        event: ShowPostEvent): LiveboardShowObservationRecord =
        assertNotNull(rows(c, ticket).singleOrNull { it.postDelivery?.event == event })

    private fun assertReason(record: LiveboardShowObservationRecord, expected: String) {
        val encoded = assertNotNull(record.encodedShowObservation())
        val payload = Json.parseToJsonElement(encoded.removePrefix("LIVEBOARD_SHOW_POST_DELIVERY ")).jsonObject
        assertEquals(expected, payload.getValue("REASON").jsonPrimitive.content,
            "Writer of the guard-admitted collected snapshot; not an atomic-current or first-ticket-loss claim")
    }

    private fun withoutSecrets(raw: String): String {
        fun remove(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                if (key == "secrets") JsonArray(emptyList()) else remove(value)
            })
            is JsonArray -> JsonArray(element.map(::remove))
            else -> element
        }
        return remove(Json.parseToJsonElement(raw)).toString()
    }
}
