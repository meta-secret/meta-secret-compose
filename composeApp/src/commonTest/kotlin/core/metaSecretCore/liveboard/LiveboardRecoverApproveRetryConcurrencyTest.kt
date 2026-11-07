package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import models.apiModels.AppStateModel
import models.apiModels.VaultFullInfo
import models.apiModels.liveboard.LiveboardStateDecoder

class LiveboardRecoverApproveRetryConcurrencyTest {
    @Test fun testOriginalHeldWorkerHasNoPublishedFailureOrSecondAdmission() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                val session = f.start()
                val identity = f.identity(LiveboardKind.Recover)
                f.core.decisionRaw = RecoverApproveRetryWire.proof()
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val gate = f.hold("accept")
                val caller = f.track(async(f.main) { session.actions.decide(intent) })
                gate.entered.await()
                session.actions.publishEnteredFailures()
                assertNull(session.inbox.state.value.error, "Unsettled original has no failure to acknowledge")
                assertEquals(LiveboardActionPhase.Busy, session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(identity)
                }.phase)
                assertNull(session.actions.decisionIntent(identity, true))
                assertIs<NativeLiveboardOutcome.NotEntered>(session.actions.decide(intent))
                assertEquals(1, f.auth.calls)
                assertEquals(1, accepts(f))
                gate.release.complete(Unit)
                gate.exited.await()
                val result = caller.await()
                session.actions.awaitSettled()
                publish(session)
                assertSame<Any>(result, session.actions.decide(intent))
                assertEquals(1, f.auth.calls)
                assertEquals(1, accepts(f))
            } finally { f.close() }
        }
    }

    @Test fun testSameKeyDoubleClickCannotEnterTwiceAfterManualRearm() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                val session = f.start()
                val identity = f.identity(LiveboardKind.Recover)
                f.core.decisionRaw = RecoverApproveRetryWire.proof()
                val old = assertNotNull(session.actions.decisionIntent(identity, true))
                val memo = session.actions.decide(old)
                session.actions.awaitSettled()
                publish(session)
                ack(f, session)
                val fresh = assertNotNull(session.actions.decisionIntent(identity, true))
                assertNull(session.actions.decisionIntent(identity, true), "Second click must not reserve same key")
                f.core.decisionRaw = "{\"success\":true}"
                val gate = f.hold("accept")
                val first = f.track(async(f.main) { session.actions.decide(fresh) })
                gate.entered.await()
                val second = f.track(async(f.main) { session.actions.decide(fresh) })
                assertIs<NativeLiveboardOutcome.NotEntered>(second.await())
                assertEquals(2, f.auth.calls)
                assertEquals(2, accepts(f))
                gate.release.complete(Unit)
                gate.exited.await()
                val returned = first.await()
                session.actions.awaitSettled()
                assertSame<Any>(returned, session.actions.decide(fresh))
                assertSame<Any>(memo, session.actions.decide(old))
                assertEquals(2, f.auth.calls)
                assertEquals(2, accepts(f))
            } finally { f.close() }
        }
    }

    @Test fun testInFlightAcknowledgementCannotCrossHardPrivacyBoundaries() = runBlocking {
        withTimeout(45_000) {
            for (boundary in listOf("hide", "background", "dispose")) {
                val f = LiveboardFeatureFixture()
                try {
                    val session = f.start()
                    val identity = f.identity(LiveboardKind.Recover)
                    f.core.decisionRaw = RecoverApproveRetryWire.proof()
                    val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                    val memo = session.actions.decide(intent)
                    session.actions.awaitSettled()
                    publish(session)
                    val gate = f.hold("read")
                    f.io.queue()
                    session.inbox.dismissError()
                    val worker = f.run(f.io.take(), this)
                    try {
                        gate.entered.await()
                        f.io.resumeInline()
                        assertNull(session.inbox.state.value.error)
                        when (boundary) {
                            "hide" -> session.sensitive.hide("pass-a")
                            "background" -> f.socket.onAppBackground()
                            "dispose" -> session.dispose()
                        }
                        gate.release.complete(Unit)
                        gate.exited.await()
                        worker.join()
                    } finally {
                        f.io.resumeInline()
                        gate.release.complete(Unit)
                    }
                    f.settleSocket()
                    f.proof()
                    session.actions.awaitSettled()
                    assertNull(session.actions.decisionIntent(identity, true))
                    assertSame<Any>(memo, session.actions.decide(intent))
                    assertEquals(1, f.auth.calls)
                    assertEquals(1, accepts(f))
                } finally { f.close() }
            }
        }
    }

    @Test fun testEarlierAcknowledgementCannotCoverLaterProofAndEachMemoStaysExact() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                configure(f, true)
                val session = f.start()
                val a = assertNotNull(session.currentMember()).operations.single {
                    it.identity.requestId == FeatureIds.claim
                }.identity
                val b = assertNotNull(session.currentMember()).operations.single {
                    it.identity.requestId == SECOND
                }.identity
                f.core.decisionRaw = RecoverApproveRetryWire.proof(a.requestId)
                val first = assertNotNull(session.actions.decisionIntent(a, true))
                val firstMemo = session.actions.decide(first)
                session.actions.awaitSettled()
                publish(session)
                configure(f, false)
                ack(f, session)
                assertNull(session.actions.decisionIntent(a, true), "Non-actionable A must retain its reservation")
                f.core.decisionRaw = RecoverApproveRetryWire.proof(b.requestId)
                val second = assertNotNull(session.actions.decisionIntent(b, true))
                val secondMemo = session.actions.decide(second)
                session.actions.awaitSettled()
                publish(session)
                configure(f, true)
                f.proof()
                session.actions.awaitSettled()
                assertNull(session.actions.decisionIntent(a, true), "Earlier ACK was revoked by later hard stamp")
                assertNull(session.actions.decisionIntent(b, true), "Earlier ACK cannot cover later proof")
                assertSame<Any>(firstMemo, session.actions.decide(first))
                assertSame<Any>(secondMemo, session.actions.decide(second))
                assertEquals(2, f.auth.calls)
                assertEquals(2, accepts(f))
                ack(f, session)
                assertNotNull(session.actions.decisionIntent(a, true))
                assertNotNull(session.actions.decisionIntent(b, true))
                assertSame<Any>(firstMemo, session.actions.decide(first))
                assertSame<Any>(secondMemo, session.actions.decide(second))
                assertEquals(2, f.auth.calls)
                assertEquals(2, accepts(f), "Observation/ACK must never invoke a new approval")
            } finally { f.close() }
        }
    }

    @Test fun testDismissWithoutPublishedVisibleFailureNeverAcknowledgesProof() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                val session = f.start()
                val identity = f.identity(LiveboardKind.Recover)
                f.core.decisionRaw = RecoverApproveRetryWire.proof()
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val memo = session.actions.decide(intent)
                session.actions.awaitSettled()
                // Deliberately do NOT invoke Coordinator's failure publication step.
                assertNull(session.inbox.state.value.error)
                val gate = f.hold("read")
                f.io.queue()
                session.inbox.dismissError()
                val worker = f.run(f.io.take(), this)
                try {
                    gate.entered.await()
                    f.io.resumeInline()
                    assertNull(session.inbox.state.value.error)
                    assertNull(session.actions.decisionIntent(identity, true))
                    gate.release.complete(Unit)
                    gate.exited.await()
                    worker.join()
                } finally {
                    f.io.resumeInline()
                    gate.release.complete(Unit)
                }
                f.settleSocket()
                f.proof()
                session.actions.awaitSettled()
                assertNull(session.actions.decisionIntent(identity, true),
                    "No visible published failure existed: dismiss cannot acknowledge its proof")
                assertSame<Any>(memo, session.actions.decide(intent))
                assertEquals(1, f.auth.calls)
                assertEquals(1, accepts(f))
            } finally { f.close() }
        }
    }

    @Test fun testUnpublishedProofCannotBorrowUnrelatedNotificationAcknowledgement() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            try {
                val session = f.start()
                val identity = f.identity(LiveboardKind.Recover)
                f.core.decisionRaw = RecoverApproveRetryWire.proof()
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val memo = session.actions.decide(intent)
                session.actions.awaitSettled()
                assertNull(session.inbox.state.value.error)
                // Actual NotificationCoordinator/privacy path, NOT a command-failure publication.
                f.notifications.showError("Fixture unrelated notification")
                assertNotNull(session.inbox.state.value.error)
                ack(f, session)
                assertNull(session.actions.decisionIntent(identity, true),
                    "Unpublished retained proof must not borrow another error's actual dismissal/read")
                assertSame<Any>(memo, session.actions.decide(intent))
                assertEquals(1, f.auth.calls)
                assertEquals(1, accepts(f))
            } finally { f.close() }
        }
    }

    private fun publish(session: LiveboardSession) {
        session.actions.publishEnteredFailures()
        val message = assertNotNull(session.inbox.state.value.error, "Actual same-session error precedes ACK")
        assertTrue(!message.contains(RecoverApproveRetryWire.CANARY))
    }
    private suspend fun ack(f: LiveboardFeatureFixture, session: LiveboardSession) = coroutineScope {
        assertNotNull(session.inbox.state.value.error)
        val gate = f.hold("read")
        f.io.queue()
        session.inbox.dismissError()
        val worker = f.run(f.io.take(), this)
        try {
            gate.entered.await()
            f.io.resumeInline()
            assertNull(session.inbox.state.value.error)
            assertTrue(!session.inbox.state.value.ready)
            gate.release.complete(Unit)
            gate.exited.await()
            worker.join()
        } finally {
            f.io.resumeInline()
            gate.release.complete(Unit)
        }
        f.settleSocket()
        f.proof()
        session.actions.awaitSettled()
    }
    private fun accepts(f: LiveboardFeatureFixture) = f.core.calls.value.count { it.name == "accept" }
    private fun configure(f: LiveboardFeatureFixture, firstAllowed: Boolean) {
        val model = AppStateModel.fromJson(f.core.state.value)
        val member = (model.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = assertNotNull(member.liveboardState).jsonObject.toMutableMap()
        val original = (metadata.getValue("operations") as JsonArray).filter {
            it.jsonObject["requestId"] != JsonPrimitive(SECOND)
        }
        val operations = original.map { element ->
            val fields = element.jsonObject.toMutableMap()
            if (fields["kind"] == JsonPrimitive("recover")) {
                fields["canApprove"] = JsonPrimitive(firstAllowed)
                fields["canDecline"] = JsonPrimitive(firstAllowed)
            }
            JsonObject(fields)
        }
        val second = original.single { it.jsonObject["kind"] == JsonPrimitive("recover") }
            .jsonObject.toMutableMap().apply {
                put("requestId", JsonPrimitive(SECOND)); put("secretId", JsonPrimitive("pass-a"))
                put("canApprove", JsonPrimitive(true)); put("canDecline", JsonPrimitive(true))
            }
        metadata["operations"] = JsonArray(operations + JsonObject(second))
        val wire = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        assertNotNull(LiveboardStateDecoder.decode(wire))
        f.core.state.value = wire
    }
    private companion object { const val SECOND = "second-opaque::claim-for-pass-a" }
}
