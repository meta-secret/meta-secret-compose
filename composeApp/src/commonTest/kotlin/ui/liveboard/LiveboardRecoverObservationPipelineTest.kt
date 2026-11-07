package ui.liveboard

import core.metaSecretCore.liveboard.FeatureIds
import core.metaSecretCore.liveboard.LiveboardDecisionRejectionSource
import core.metaSecretCore.liveboard.LiveboardDecisionRejectionTrace
import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.NativeFailure
import core.metaSecretCore.liveboard.NativeLiveboardOutcome
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.NativeReadRequirement
import core.metaSecretCore.liveboard.NativeTransitionGate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import core.metaSecretCore.liveboard.LiveboardActionPhase
import core.metaSecretCore.liveboard.LiveboardActionTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveboardRecoverObservationPipelineTest {
    @Test
    fun testActualRootRecoverObservesCallbackAfterSameEnteredResult() = runBlocking {
        withTimeout(45_000) {
            val fixture = LiveboardRootTestFixture()
            try {
                fixture.platform.recoverObservationOn = true
                fixture.core.primary("recover", id = "pass-b")
                fixture.start()
                val root = fixture.root()
                val session = fixture.session(root)
                assertEquals(0, fixture.auth.calls)
                val caller = fixture.track(root.recover("pass-b"))
                session.actions.awaitSettled()
                caller.join()
                assertFalse(caller.isCancelled)
                val calls = fixture.core.calls.value.filter { it.name == "recover" }
                assertEquals(1, calls.size)
                assertEquals(listOf("pass-b"), calls.single().arguments)
                assertEquals(0, fixture.auth.calls)
                val result = session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Recover("pass-b")
                }
                assertEquals(LiveboardActionPhase.Submitted, result.phase)
                assertEquals(FeatureIds.claim, result.requestId)
                assertNull(session.actions.recoverIntent("pass-b"), "Original retained intent cannot resubmit")
                assertEquals(1, fixture.core.calls.value.count { it.name == "recover" })
                // The original Root/controller must finish before missing diagnostics becomes causal RED.
                assertEquals(1, fixture.platform.recoverObservationRecords.value.count {
                    it.stage == RecoverObservationStage.ROOT_CALLBACK
                }, "Expected one passive callback observation after the actual original Recover")
            } finally { fixture.close() }
        }
    }

    @Test
    fun testDefaultDisabledRecoverKeepsOriginalEntryAndNoObservation() = runBlocking {
        withTimeout(45_000) {
            val fixture = LiveboardRootTestFixture()
            try {
                val defaults = object : LiveboardPlatformPort {
                    override fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment =
                        error("Default capability probe must not attach")
                    override suspend fun readClipboard(): String? = error("No clipboard probe")
                    override fun copyText(value: String) = error("No copy probe")
                }
                assertFalse(defaults.recoverObservationEnabled)
                assertFalse(fixture.platform.recoverObservationEnabled)
                fixture.core.primary("recover", id = "pass-b")
                fixture.start()
                val root = fixture.root()
                val session = fixture.session(root)
                assertEquals(0, fixture.auth.calls)
                val caller = fixture.track(root.recover("pass-b"))
                session.actions.awaitSettled()
                caller.join()
                assertTrue(caller.isCompleted)
                assertFalse(caller.isCancelled)
                val calls = fixture.core.calls.value.filter { it.name == "recover" }
                assertEquals(1, calls.size)
                assertEquals(listOf("pass-b"), calls.single().arguments)
                assertEquals(0, fixture.auth.calls)
                val result = session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Recover("pass-b")
                }
                assertEquals(LiveboardActionPhase.Submitted, result.phase)
                assertEquals(FeatureIds.claim, result.requestId)
                assertNull(session.actions.recoverIntent("pass-b"), "Original retained intent cannot resubmit")
                assertEquals(0, fixture.platform.recoverObservationAttempts.value)
                assertTrue(fixture.platform.recoverObservationRecords.value.isEmpty())
            } finally { fixture.close() }
        }
    }

    @Test
    fun testActualReadKeepsNullIntentUnknownAndPreselectedResultNotEntered() = runBlocking {
        withTimeout(45_000) { withObservedRoot { f, root, session ->
            val first = beginRead(f)
            try {
                assertFalse(session.inbox.state.value.ready)
                val caller = f.track(root.recover(PASS))
                caller.join()
                session.actions.awaitSettled()
                assertEquals(0, entries(f))
                val post = f.platform.recoverObservationRecords.value.single {
                    it.stage == RecoverObservationStage.POST_REGISTRATION
                }
                assertEquals(RecoverObservationFlag.ZERO, post.facts.intentPresent)
                assertEquals(RecoverObservationOutcome.UNOBSERVED, post.facts.outcome)
                assertTrue(f.platform.recoverObservationRecords.value.none {
                    it.stage == RecoverObservationStage.RETURNED
                }, "No awaited controller return exists for a null intent")
                finishRead(f, first)
                assertEquals(0, entries(f), "Fresh read cannot manufacture the omitted command")
            } finally { first.gate.release.complete(Unit) }
            val selected = assertNotNull(session.actions.recoverIntent(PASS))
            assertTrue(session.actions.presentation().any { it.target == LiveboardActionTarget.Recover(PASS) })
            val second = beginRead(f)
            try {
                assertFalse(session.inbox.state.value.ready)
                assertTrue(session.actions.presentation().none {
                    it.target == LiveboardActionTarget.Recover(PASS)
                }, "Actual read privacy settlement retired the selected admission before this call")
                val trace = LiveboardDecisionRejectionTrace()
                val result = assertIs<NativeLiveboardOutcome.NotEntered>(
                    session.actions.recoverObserved(selected, trace))
                assertEquals(session.scope, result.scope)
                assertEquals(NativeFailure.OperationUnavailable, result.code)
                session.actions.awaitSettled()
                assertEquals(0, entries(f))
                assertEquals(LiveboardDecisionRejectionSource.ACTION_ADMISSION_MISSING,
                    trace.source())
                finishRead(f, second)
                assertEquals(0, entries(f))
                assertEquals(0, f.auth.calls)
            } finally { second.gate.release.complete(Unit) }
        } }
    }

    @Test
    fun testReplacedRootObservesMissingSessionWithoutReachingCommandBody() = runBlocking {
        withTimeout(45_000) { withObservedRoot { f, root, session ->
            assertTrue(f.session(f.root()) === session)
            val caller = f.track(root.recover(PASS))
            caller.join()
            session.actions.awaitSettled()
            assertEquals(0, entries(f))
            assertEquals(0, f.auth.calls)
            val post = f.platform.recoverObservationRecords.value.single {
                it.stage == RecoverObservationStage.POST_REGISTRATION
            }
            assertEquals(RecoverObservationFlag.ZERO, post.facts.sessionPresent)
            assertEquals(RecoverObservationFlag.UNKNOWN, post.facts.intentPresent)
            assertTrue(f.platform.recoverObservationRecords.value.none {
                it.facts.bodyReached == RecoverObservationFlag.ONE || it.stage == RecoverObservationStage.RETURNED
            })
        } }
    }

    @Test
    fun testEnteredWorkerSurvivesCallerCancellationWithoutInventingReturnedEvidence() = runBlocking {
        withTimeout(45_000) { withObservedRoot { f, root, session ->
            val native = f.hold("recover")
            try {
                val caller = f.track(root.recover(PASS))
                native.entered.await()
                assertEquals(1, entries(f))
                caller.cancelAndJoin()
                assertTrue(caller.isCancelled)
                assertFalse(native.exited.isCompleted)
                val completed = f.platform.recoverObservationRecords.value.single {
                    it.stage == RecoverObservationStage.SAME_JOB_COMPLETION
                }
                assertEquals(RecoverObservationCompletion.CANCELLED, completed.completion)
                assertEquals(RecoverObservationOutcome.UNOBSERVED, completed.facts.outcome)
                native.release.complete(Unit)
                native.exited.await()
                session.actions.awaitSettled()
                assertEquals(1, entries(f))
                assertEquals(listOf(PASS), f.core.calls.value.single { it.name == "recover" }.arguments)
                assertEquals(0, f.auth.calls)
                assertTrue(f.platform.recoverObservationRecords.value.none {
                    it.stage == RecoverObservationStage.RETURNED
                }, "Caller completion is not the independent journal worker's returned value")
            } finally { native.release.complete(Unit) }
        } }
    }

    @Test
    fun testThrowingSinkAndNativeFailureKeepOriginalEffectsAndNeverExposeCanaries() = runBlocking {
        withTimeout(45_000) {
            for (nativeFailure in listOf(false, true)) withObservedRoot { f, root, session ->
                val gate = if (nativeFailure) f.hold("recover", IllegalStateException(CANARY)).also {
                    it.release.complete(Unit)
                } else null
                if (!nativeFailure) f.platform.observationFailure = IllegalStateException(CANARY)
                try {
                    val caller = f.track(root.recover(PASS))
                    session.actions.awaitSettled()
                    caller.join()
                    assertFalse(caller.isCancelled)
                    assertEquals(1, entries(f))
                    assertEquals(listOf(PASS), f.core.calls.value.single { it.name == "recover" }.arguments)
                    assertEquals(0, f.auth.calls)
                    val result = session.actions.presentation().single {
                        it.target == LiveboardActionTarget.Recover(PASS)
                    }
                    assertEquals(if (nativeFailure) LiveboardActionPhase.Unknown else
                        LiveboardActionPhase.Submitted, result.phase)
                    assertNull(session.actions.recoverIntent(PASS))
                    assertTrue(f.platform.recoverObservationAttempts.value > 0)
                    if (!nativeFailure) assertTrue(f.platform.recoverObservationRecords.value.isEmpty())
                    else {
                        val returned = f.platform.recoverObservationRecords.value.single {
                            it.stage == RecoverObservationStage.RETURNED
                        }
                        assertEquals(RecoverObservationOutcome.ENTERED_UNCERTAIN, returned.facts.outcome)
                    }
                    f.platform.recoverObservationRecords.value.forEach {
                        val line = assertNotNull(it.encodedRecoverObservation())
                        assertFalse(line.contains(CANARY) || line.contains(PASS) || line.contains(FeatureIds.claim))
                    }
                } finally { gate?.release?.complete(Unit) }
            }
        }
    }

    private class Read(val gate: NativeTransitionGate, val target: NativeReadRequirement)
    private suspend fun beginRead(f: LiveboardRootTestFixture): Read {
        val target = assertNotNull(f.reads.requireFreshRead(assertNotNull(f.initialization.currentScope())))
        val gate = f.hold("read")
        f.socket.refreshAppState(target)
        gate.entered.await()
        return Read(gate, target)
    }
    private suspend fun finishRead(f: LiveboardRootTestFixture, read: Read) {
        read.gate.release.complete(Unit)
        read.gate.exited.await()
        assertEquals(NativeReadOutcome.SUCCESS, read.target.completion.await().outcome)
        f.idle()
    }
    private fun entries(f: LiveboardRootTestFixture) = f.core.calls.value.count { it.name == "recover" }
    private suspend fun withObservedRoot(
        block: suspend (LiveboardRootTestFixture, LiveboardRootCoordinator, LiveboardSession) -> Unit,
    ) {
        val f = LiveboardRootTestFixture()
        try {
            f.platform.recoverObservationOn = true
            f.core.primary("recover", id = PASS)
            f.start()
            val root = f.root()
            val session = f.session(root)
            block(f, root, session)
        } finally { withContext(NonCancellable) { f.close() } }
    }
    private companion object {
        const val PASS = "pass-b"
        const val CANARY = "PRIVATE_RECOVER_PAYLOAD_DEVICE_VAULT_RAW_ERROR_CANARY"
    }
}
