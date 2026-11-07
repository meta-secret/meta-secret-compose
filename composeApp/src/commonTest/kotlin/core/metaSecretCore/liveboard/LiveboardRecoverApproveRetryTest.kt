package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.apiModels.liveboard.LiveboardOperationIdentity

class LiveboardRecoverApproveRetryTest {
    private class Probe(val f: LiveboardFeatureFixture, val session: LiveboardSession,
        val identity: LiveboardOperationIdentity, val intent: LiveboardDecisionIntent,
        val outcome: NativeLiveboardOutcome.Entered<*>)

    @Test fun testProofDecodedAsMetadataButNeverSubmittedOrAutomaticallyReleased() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                // Semantic baseline RED: current real returnedDecision decoder yields Unavailable.
                assertIs<NativeResult.Success<*>>(p.outcome.result)
                assertEquals(LiveboardActionPhase.Unknown, phase(p))
                publishError(p)
                p.f.proof()
                p.session.actions.awaitSettled()
                assertNull(p.session.actions.decisionIntent(p.identity, true), "Read alone is not acknowledgement")
                memoAndCounts(p)
                assertFalse(p.outcome.toString().contains(RecoverApproveRetryWire.CANARY))
            }
        }
    }

    @Test fun testActualErrorAckAndCoveredReadPermitOnlyNewIntentAndFreshAuthentication() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                acknowledgeThroughActualRead(p)
                val fresh = assertNotNull(p.session.actions.decisionIntent(p.identity, true))
                assertNotSame(p.intent, fresh)
                memoAndCounts(p)
                p.f.core.decisionRaw = "{\"success\":true}"
                val result = p.session.actions.decide(fresh)
                assertIs<NativeLiveboardOutcome.Entered<*>>(result)
                p.session.actions.awaitSettled()
                assertEquals(2, p.f.auth.calls)
                assertEquals(2, entries(p))
            }
        }
    }

    @Test fun testMalformedForeignAndLegacyFalseNeverAcquireRearmPrivilege() = runBlocking {
        withTimeout(45_000) {
            for (raw in RecoverApproveRetryWire.invalid()) entered(raw) { p ->
                assertIs<NativeResult.Unavailable>(p.outcome.result)
                publishError(p)
                acknowledgeThroughActualRead(p)
                assertNull(p.session.actions.decisionIntent(p.identity, true))
                assertEquals(LiveboardActionPhase.Unknown, phase(p))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testProofOnDeclineOrDeleteDecisionRemainsInvalid() = runBlocking {
        withTimeout(45_000) {
            for ((kind, accepted) in listOf(LiveboardKind.Recover to false, LiveboardKind.DeleteSecret to true)) {
                entered(RecoverApproveRetryWire.proof(FeatureIds.request(kind)), kind, accepted) { p ->
                    assertIs<NativeResult.Unavailable>(p.outcome.result)
                    publishError(p)
                    acknowledgeThroughActualRead(p)
                    assertNull(p.session.actions.decisionIntent(p.identity, accepted))
                    assertSame<Any>(p.outcome, p.session.actions.decide(p.intent))
                    assertEquals(1, p.f.auth.calls)
                    assertEquals(1, p.f.core.calls.value.count { it.name in listOf("accept", "decline", "decideDelete") })
                }
            }
        }
    }

    @Test fun testObservedVerifiedReceiverTuplesRetirePresentationWithoutNewEntry() = runBlocking {
        withTimeout(45_000) {
            for (case in listOf("own-approve", "peer-approve", "peer-decline")) entered { p ->
                publishError(p)
                RecoverApproveRetryWire.terminal(p.f, case)
                p.f.proof()
                p.session.actions.awaitSettled()
                assertTrue(p.session.actions.presentation().none { it.target == target(p) },
                    "Observed waiting/unknown-or-needApprove verified tuple must retire only presentation")
                assertSame<Any>(p.outcome, p.session.actions.decide(p.intent))
                assertEquals(1, p.f.auth.calls)
                assertEquals(1, entries(p))
            }
        }
    }

    @Test fun testObservedUnverifiedOwnDeclineTupleIsNeverTerminalOrRetryPermission() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                RecoverApproveRetryWire.terminal(p.f, "own-decline-negative")
                acknowledgeThroughActualRead(p)
                assertEquals(LiveboardActionPhase.Unknown, phase(p))
                assertNull(p.session.actions.decisionIntent(p.identity, true))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testPartialTerminalOrChangedFullIdentityNeverReleasesPresentation() = runBlocking {
        withTimeout(45_000) {
            for (change in listOf("missing-outcome", "missing-responder", "unknown-resolution",
                "actionable", "requester", "secret", "claim", "generic-terminal-without-evidence")) entered { p ->
                publishError(p)
                RecoverApproveRetryWire.terminal(p.f, "peer-approve", change)
                p.f.proof()
                p.session.actions.awaitSettled()
                assertEquals(LiveboardActionPhase.Unknown, phase(p))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testHardHideBetweenManualAckAndFreshReadInvalidatesAck() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                val read = p.f.hold("read")
                p.f.io.queue()
                p.session.inbox.dismissError()
                val worker = p.f.run(p.f.io.take(), this)
                try {
                    read.entered.await()
                    p.f.io.resumeInline()
                    p.session.sensitive.hide("pass-a")
                    read.release.complete(Unit)
                    read.exited.await()
                    worker.join()
                } finally {
                    p.f.io.resumeInline()
                    read.release.complete(Unit)
                }
                p.f.settleSocket()
                p.f.proof()
                p.session.actions.awaitSettled()
                assertNull(p.session.actions.decisionIntent(p.identity, true))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testFailedAckReadDoesNotAuthorizeNewSelection() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                p.f.core.failRead = true
                val read = p.f.hold("read")
                p.f.io.queue()
                p.session.inbox.dismissError()
                val worker = p.f.run(p.f.io.take(), this)
                try {
                    read.entered.await()
                    p.f.io.resumeInline()
                    read.release.complete(Unit)
                    read.exited.await()
                    worker.join()
                } finally {
                    p.f.io.resumeInline()
                    read.release.complete(Unit)
                }
                p.f.settleSocket()
                assertFalse(p.session.inbox.state.value.ready)
                assertNull(p.session.actions.decisionIntent(p.identity, true))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testFreshManualSelectionStillRequiresNewSuccessfulAuthentication() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                acknowledgeThroughActualRead(p)
                val fresh = assertNotNull(p.session.actions.decisionIntent(p.identity, true))
                p.f.auth.held = true
                val caller = p.f.track(async(p.f.main) { p.session.actions.decide(fresh) })
                val prompt = p.f.auth.take()
                prompt.error("Fixture authentication refused")
                assertIs<NativeLiveboardOutcome.NotEntered>(caller.await())
                p.session.actions.awaitSettled()
                assertEquals(2, p.f.auth.calls)
                assertEquals(1, entries(p))
                assertSame<Any>(p.outcome, p.session.actions.decide(p.intent))
            }
        }
    }

    @Test fun testTerminalDuringNewAuthenticationPreventsSecondNativeEntry() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                acknowledgeThroughActualRead(p)
                val fresh = assertNotNull(p.session.actions.decisionIntent(p.identity, true))
                p.f.auth.held = true
                val caller = p.f.track(async(p.f.main) { p.session.actions.decide(fresh) })
                val prompt = p.f.auth.take()
                RecoverApproveRetryWire.terminal(p.f, "peer-approve")
                p.f.proof()
                prompt.success()
                assertIs<NativeLiveboardOutcome.NotEntered>(caller.await())
                p.session.actions.awaitSettled()
                assertEquals(2, p.f.auth.calls)
                assertEquals(1, entries(p))
            }
        }
    }

    @Test fun testDuplicateCanonicalOperationCannotReleaseOrChooseAnotherHead() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                RecoverApproveRetryWire.duplicateOperation(p.f)
                acknowledgeThroughActualRead(p)
                assertEquals(LiveboardActionPhase.Unknown, phase(p))
                assertNull(p.session.actions.decisionIntent(p.identity, true))
                memoAndCounts(p)
            }
        }
    }

    @Test fun testProofAndAckDoNotTransferThroughActualResetAndNewOwner() = runBlocking {
        withTimeout(45_000) {
            entered { p ->
                publishError(p)
                val profile = p.f.profile()
                val scope = p.session.scope
                val successor = p.f.successor(profile)
                assertFalse(successor.scope == scope)
                assertTrue(successor.actions.presentation().isEmpty())
                assertSame<Any>(p.outcome, p.session.actions.decide(p.intent))
                assertEquals(1, p.f.auth.calls)
                assertEquals(1, entries(p))
            }
        }
    }

    private suspend fun entered(raw: String = RecoverApproveRetryWire.proof(), kind: LiveboardKind = LiveboardKind.Recover,
        accepted: Boolean = true, block: suspend (Probe) -> Unit) {
        val f = LiveboardFeatureFixture()
        try {
            val session = f.start()
            val identity = f.identity(kind)
            f.core.decisionRaw = raw
            val intent = assertNotNull(session.actions.decisionIntent(identity, accepted))
            val outcome = assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.decide(intent))
            session.actions.awaitSettled()
            block(Probe(f, session, identity, intent, outcome))
        } finally { f.close() }
    }

    private fun publishError(p: Probe) {
        // Same real publication step/order as RootCoordinator.observeSettlement, after worker settlement.
        p.session.actions.publishEnteredFailures()
        val error = assertNotNull(p.session.inbox.state.value.error, "Acknowledge actual same-session visible error")
        assertFalse(error.contains(RecoverApproveRetryWire.CANARY))
        assertEquals(LiveboardActionPhase.Unknown, phase(p))
    }

    private suspend fun acknowledgeThroughActualRead(p: Probe) = coroutineScope {
        val read = p.f.hold("read")
        p.f.io.queue()
        p.session.inbox.dismissError()
        val worker = p.f.run(p.f.io.take(), this)
        try {
            read.entered.await()
            p.f.io.resumeInline()
            assertNull(p.session.inbox.state.value.error)
            assertFalse(p.session.inbox.state.value.ready)
            assertNull(p.session.actions.decisionIntent(p.identity, true), "Ack with in-flight read is insufficient")
            assertEquals(1, p.f.auth.calls)
            assertEquals(1, p.f.core.calls.value.count { it.name in listOf("accept", "decline", "decideDelete") })
            read.release.complete(Unit)
            read.exited.await()
            worker.join()
        } finally {
            p.f.io.resumeInline()
            read.release.complete(Unit)
        }
        p.f.settleSocket()
        p.f.proof()
        p.session.actions.awaitSettled()
    }
    private fun target(p: Probe) = LiveboardActionTarget.Decision(p.identity)
    private fun phase(p: Probe) = p.session.actions.presentation().single { it.target == target(p) }.phase
    private fun entries(p: Probe) = p.f.core.calls.value.count { it.name == "accept" }
    private suspend fun memoAndCounts(p: Probe) {
        assertSame<Any>(p.outcome, p.session.actions.decide(p.intent), "Old intent must keep exact immutable memo")
        assertEquals(1, p.f.auth.calls)
        assertEquals(1, entries(p))
    }
}
