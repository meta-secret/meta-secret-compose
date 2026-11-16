package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardDecisionRejectionObservationTest {
    @Test
    fun testActualReleasedIntentReportsAdmissionMissing() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { f, session ->
                val identity = f.identity(LiveboardKind.Recover)
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val trace = LiveboardDecisionRejectionTrace()
                session.sensitive.conceal(false)
                val result = assertIs<NativeLiveboardOutcome.NotEntered>(session.actions.decideObserved(intent, trace))
                assertEquals(session.scope, result.scope)
                assertEquals(NativeFailure.OperationUnavailable, result.code)
                assertEquals(0, f.auth.calls)
                assertEquals(0, f.core.calls.value.count { it.name == "accept" })
                session.actions.awaitSettled()
                assertTrue(session.actions.presentation().none { it.target == LiveboardActionTarget.Decision(identity) })
                // The real old-intent refusal and settlement must precede this intended semantic RED.
                assertEquals(LiveboardDecisionRejectionSource.ACTION_ADMISSION_MISSING, trace.source(),
                    "Expected the actual missing-admission return to publish its fixed reason")
            }
        }
    }

    @Test
    fun testActualEnteredDecisionDoesNotInventRejection() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { f, session ->
                val identity = f.identity(LiveboardKind.Recover)
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val trace = LiveboardDecisionRejectionTrace()
                val outcome = assertIs<NativeLiveboardOutcome.Entered<*>>(session.actions.decideObserved(intent, trace))
                val success = assertIs<NativeResult.Success<*>>(outcome.result)
                val returned = assertIs<LiveboardDecisionAttempt.Returned>(success.value)
                assertEquals(session.scope, outcome.scope)
                assertEquals(identity.kind, returned.kind)
                assertEquals(identity.requestId, returned.requestId)
                assertTrue(returned.accepted)
                assertEquals(1, f.auth.calls)
                assertEquals(1, f.core.calls.value.count { it.name == "accept" })
                session.actions.awaitSettled()
                assertNull(session.actions.decisionIntent(identity, true), "Retained same intent cannot resubmit")
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, trace.source())
            }
        }
    }

    @Test
    fun testActualAuthDenialKeepsWinningCancellationCategory() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { f, session ->
                val intent = assertNotNull(session.actions.decisionIntent(f.identity(LiveboardKind.Recover), true))
                val trace = LiveboardDecisionRejectionTrace()
                f.auth.held = true
                val caller = f.track(async(f.main, CoroutineStart.UNDISPATCHED) {
                    session.actions.decideObserved(intent, trace)
                })
                f.auth.take().error("Fixture OS denial")
                val result = assertIs<NativeLiveboardOutcome.NotEntered>(caller.await())
                assertEquals(session.scope, result.scope)
                assertEquals(NativeFailure.OperationUnavailable, result.code)
                session.actions.awaitSettled()
                assertEquals(1, f.auth.calls)
                assertEquals(0, f.core.calls.value.count { it.name == "accept" })
                // Denied() settles/cancels before confirm(false) returns; no fixed AUTH_DECLINED winner.
                assertCancellationCategory(trace.source())
            }
        }
    }

    @Test
    fun testActualQueuedCancellationCannotBeRelabelledByLateConsent() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { f, session ->
                val intent = assertNotNull(session.actions.decisionIntent(f.identity(LiveboardKind.Recover), true))
                val trace = LiveboardDecisionRejectionTrace()
                f.auth.held = true
                val caller = f.track(async(f.main, CoroutineStart.UNDISPATCHED) {
                    session.actions.decideObserved(intent, trace)
                })
                val reply = f.auth.take()
                caller.cancelAndJoin()
                session.actions.awaitSettled()
                assertTrue(caller.isCancelled)
                assertEquals(1, f.auth.calls)
                assertEquals(0, f.core.calls.value.count { it.name == "accept" })
                val winner = trace.source()
                assertCancellationCategory(winner)
                reply.success()
                session.actions.awaitSettled()
                assertEquals(winner, trace.source(), "A late auth reply cannot relabel the winning cancellation")
                assertEquals(0, f.core.calls.value.count { it.name == "accept" })
            }
        }
    }

    @Test
    fun testActualEnteredPrivateWorkerSurvivesCallerCancellation() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { f, session ->
                val identity = f.identity(LiveboardKind.Recover)
                val intent = assertNotNull(session.actions.decisionIntent(identity, true))
                val trace = LiveboardDecisionRejectionTrace()
                val native = f.hold("accept")
                val caller = f.track(async(f.main, CoroutineStart.UNDISPATCHED) {
                    session.actions.decideObserved(intent, trace)
                })
                native.entered.await()
                caller.cancelAndJoin()
                assertTrue(caller.isCancelled)
                assertFalse(native.exited.isCompleted)
                assertEquals(1, f.auth.calls)
                assertEquals(1, f.core.calls.value.count { it.name == "accept" })
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, trace.source())
                native.release.complete(Unit)
                native.exited.await()
                session.actions.awaitSettled()
                assertEquals(LiveboardActionPhase.Submitted, session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(identity)
                }.phase)
                assertNull(session.actions.decisionIntent(identity, true))
                assertEquals(1, f.core.calls.value.count { it.name == "accept" })
                assertEquals(LiveboardDecisionRejectionSource.UNOBSERVED, trace.source())
            }
        }
    }

    private fun assertCancellationCategory(actual: LiveboardDecisionRejectionSource) {
        assertTrue(actual in setOf(
            LiveboardDecisionRejectionSource.JOURNAL_CANCELLED_SETTLEMENT,
            LiveboardDecisionRejectionSource.JOURNAL_COMPLETION_BEFORE_ADMISSION,
            LiveboardDecisionRejectionSource.JOURNAL_EXCEPTION_BEFORE_ADMISSION,
        ), "Only the actual winning pre-admission cancellation path may publish the category")
    }

    private suspend fun withFixture(block: suspend (LiveboardFeatureFixture, LiveboardSession) -> Unit) {
        val fixture = LiveboardFeatureFixture()
        try { block(fixture, fixture.start()) } finally { fixture.close() }
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}
