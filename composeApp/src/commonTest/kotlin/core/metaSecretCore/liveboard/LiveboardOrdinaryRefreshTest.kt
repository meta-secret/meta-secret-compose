package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import ui.liveboard.LiveboardRootTestFixture
import ui.liveboard.rootCheck

class LiveboardOrdinaryRefreshTest {
    private val ids = listOf("pass-a", "pass-b")
    private class Reading(val gate: NativeTransitionGate, val target: NativeReadRequirement, val caller: Deferred<IllegalStateException?>?)

    @Test
    fun testOnlyUnchangedAuthorizedSuccessfulReadsRestoreExistingCardsWithoutAnotherShow() = runBlocking {
        withTimeout(60_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (source in listOf("manager", "socket")) ordinary(this, source, failures, reached)
            for (effect in listOf("hide", "error", "failed", "background", "claim", "tombstone", "member", "key", "disposed")) {
                revoked(this, effect, failures, reached)
            }
            assertEquals(11, reached.size)
            assertEquals(emptyList(), failures,
                "Actual busy/read-success, both disclosure routes, revocation and fresh-owner controls reached: $reached")
        }
    }

    private suspend fun ordinary(scope: CoroutineScope, source: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        var cleanup: LiveboardSession? = null
        try {
            RecoveredRevealFixture.install(f)
            f.start(); val session = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { cleanup = it }
            val values = disclose(f, session)
            val origin = assertNotNull(f.initialization.currentScope())
            val calls = f.core.calls.value.toList(); val auth = f.auth.calls
            val reading = enter(f, scope, source)
            masked(f, session, failures, "$source/busy")
            rootCheck(failures, "$source/entry blocked") {
                assertNull(session.actions.deleteIntent("pass-a"))
                assertNull(session.removeDeviceIntent("device-b"))
            }
            val attemptedShow = session.sensitive.show("pass-a")
            rootCheck(failures, "$source/Show blocked") { assertFalse(attemptedShow) }
            assertEquals(calls, f.core.calls.value); assertEquals(auth, f.auth.calls)
            val proof = finish(f, reading)
            assertEquals(origin, proof.scope); assertEquals(origin, f.initialization.currentScope())
            assertTrue(session.inbox.state.value.ready)
            rootCheck(failures, "$source/same authorized values restored") {
                assertEquals(values, session.sensitive.state.value.revealed.mapValues { it.value.value })
                ids.forEach { assertEquals(values.getValue(it), session.sensitive.copyValue(it)) }
            }
            assertEquals(calls, f.core.calls.value, "A physical refresh must not call either native disclosure again")
            assertEquals(auth, f.auth.calls, "The same authorization is preserved, not re-prompted")
            ids.forEach { session.sensitive.hide(it) }
            assertTrue(session.sensitive.state.value.revealed.isEmpty())
            RecoveredRevealFixture.install(f, RecoveredRevealFixture.freshClaim)
            f.proof(); f.idle() // A genuinely new eligible Claim precedes this deliberate new Show.
            disclose(f, session, RecoveredRevealFixture.freshClaim)
            assertEquals(calls.size + 2, f.core.calls.value.size)
            reached.add("$source/SUCCESS/local+recovered/no-repeat/fresh-Show")
        } finally { cleanup?.dispose(); f.close() }
    }

    private suspend fun revoked(scope: CoroutineScope, effect: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardRootTestFixture()
        var cleanup: LiveboardSession? = null
        var successor: LiveboardSession? = null
        try {
            f.start(); val session = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { cleanup = it }
            disclose(f, session)
            val before = f.core.calls.value.toList(); val auth = f.auth.calls
            when (effect) {
                "claim" -> f.core.primary("showRecovered", "replacement-claim", "pass-b")
                "tombstone" -> f.core.state.value = withoutSecrets(f.core.state.value)
                "member" -> f.core.state.value = f.removedState(true)
            } // getAppState captures its raw return at actual entry, before its held gate.
            val reading = enter(f, scope, "manager")
            masked(f, session, failures, "$effect/busy")
            var transition: Deferred<NativeReadFrame>? = null
            when (effect) {
                "hide" -> ids.forEach { session.sensitive.hide(it) }
                "error" -> f.notifications.showError("Synthetic ordinary-refresh error")
                "failed" -> f.core.failRead = true
                "background" -> f.socket.onAppBackground()
                "key" -> {
                    transition = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { f.newOwner() })
                    assertNull(f.initialization.currentScope(), "Actual key transition retires A before its native lease drains")
                }
                "disposed" -> session.dispose()
            }
            reading.gate.release.complete(Unit); reading.gate.exited.await(); val readFailure = reading.caller?.await(); f.idle()
            f.core.failRead = false
            if (effect == "failed") {
                assertEquals("Synthetic unreadable feature state", assertNotNull(readFailure).message)
                assertTrue(f.reads.frames.value?.outcome != NativeReadOutcome.SUCCESS)
            } else if (effect == "key") {
                assertNull(readFailure)
                transition!!.await()
                assertEquals(NativeReadOutcome.RETIRED, reading.target.completion.await().outcome)
            } else {
                assertNull(readFailure)
                val proof = reading.target.completion.await()
                assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
                assertTrue(reading.target in proof.coveredRequirements)
            }
            // Return to the original raw metadata through another genuine same-owner proof.
            // A revoked disclosure must not resurrect even if the old Claim/Secret later returns.
            if (effect !in listOf("key", "background")) {
                f.core.fresh(); f.proof(); f.idle()
            }
            val revokedIds = if (effect == "claim") listOf("pass-b") else ids
            rootCheck(failures, "$effect/no resurrection") {
                revokedIds.forEach {
                    assertNull(session.sensitive.state.value.revealed[it]); assertNull(session.sensitive.copyValue(it))
                }
            }
            assertEquals(before, f.core.calls.value); assertEquals(auth, f.auth.calls)
            if (effect == "disposed") assertNull(f.factory.open(assertNotNull(f.reads.frames.value)), "Disposed same-key journal is a tombstone")
            if (effect != "key") f.newOwner()
            f.socket.onAppForeground()
            f.socket.pauseRefreshesAndAwait()
            f.proof(); f.idle()
            val live = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { successor = it }
            assertNotSame(session, live)
            assertTrue(live.sensitive.state.value.revealed.isEmpty(), "A genuinely new Session inherits no disclosure")
            disclose(f, live)
            session.sensitive.hide("pass-a") // Old-owner cleanup cannot affect B's new authorization.
            ids.forEach { assertNotNull(live.sensitive.copyValue(it)) }
            assertEquals(before.size + 2, f.core.calls.value.size)
            reached.add("$effect/revoked/no-resurrection/current-B-native-positive")
        } finally {
            f.core.failRead = false
            cleanup?.dispose(); successor?.dispose(); f.close()
        }
    }

    private suspend fun disclose(f: LiveboardRootTestFixture, session: LiveboardSession,
        expectedClaimId: String = FeatureIds.claim): Map<String, String> {
        assertTrue(session.inbox.state.value.ready)
        val before = f.core.calls.value.size; val auth = f.auth.calls
        ids.forEach { assertTrue(session.sensitive.show(it)) }
        assertEquals(listOf(NativeCall("local", listOf("pass-a")), NativeCall("recovered", listOf(expectedClaimId))),
            f.core.calls.value.drop(before))
        assertEquals(auth + 2, f.auth.calls)
        return ids.associateWith { assertNotNull(session.sensitive.copyValue(it)) }.also {
            assertEquals(mapOf("pass-a" to "local fixture value", "pass-b" to
                "one two three four five six seven eight nine ten eleven twelve"), it)
        }
    }

    private suspend fun enter(f: LiveboardRootTestFixture, scope: CoroutineScope, source: String): Reading {
        f.idle()
        val expected = assertNotNull(f.initialization.currentScope())
        val target = assertNotNull(f.reads.requireFreshRead(expected))
        val gate = f.hold("read")
        val caller = if (source == "manager") f.track(scope.async(Dispatchers.IO) {
            try { f.manager.getStateModel(); null } catch (failure: IllegalStateException) { failure }
        })
            else { f.socket.refreshAppState(); null }
        gate.entered.await()
        assertEquals(expected, f.initialization.currentScope())
        assertTrue(f.cache.synchronization.value.activeRefreshes > 0)
        return Reading(gate, target, caller)
    }

    private suspend fun finish(f: LiveboardRootTestFixture, reading: Reading): NativeReadFrame {
        reading.gate.release.complete(Unit); reading.gate.exited.await(); assertNull(reading.caller?.await()); f.idle()
        val proof = reading.target.completion.await()
        assertEquals(NativeReadOutcome.SUCCESS, proof.outcome); assertEquals(reading.target.scope, proof.scope)
        assertTrue(reading.target in proof.coveredRequirements)
        return proof
    }

    private fun masked(f: LiveboardRootTestFixture, session: LiveboardSession, failures: MutableList<String>, label: String) {
        assertTrue(f.cache.synchronization.value.loading)
        rootCheck(failures, label) {
            assertFalse(session.inbox.state.value.ready)
            assertTrue(session.sensitive.state.value.revealed.isEmpty())
            ids.forEach { assertNull(session.sensitive.copyValue(it)) }
        }
    }

    private fun withoutSecrets(raw: String): String {
        fun remove(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) -> if (key == "secrets") JsonArray(emptyList()) else remove(value) })
            is JsonArray -> JsonArray(element.map(::remove))
            else -> element
        }
        return remove(Json.parseToJsonElement(raw)).toString()
    }
}
