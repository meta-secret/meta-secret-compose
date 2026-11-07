package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardActionPhase
import core.metaSecretCore.liveboard.LiveboardActionTarget
import core.metaSecretCore.liveboard.LiveboardKind
import core.metaSecretCore.liveboard.NativeCall
import core.metaSecretCore.liveboard.NativeReadOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardDecisionRefreshCoordinationTest {
    @Test
    fun testSameDecisionWaitsForTechnicalReadAndEntersOnce() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val fixture = LiveboardRootTestFixture()
            try {
                fixture.start()
                val root = fixture.root()
                val session = fixture.session(root)
                val scope = assertNotNull(fixture.initialization.currentScope())
                val identity = assertNotNull(session.currentMember()).operations.single {
                    it.identity.kind == LiveboardKind.Recover
                }.identity
                assertTrue(session.inbox.state.value.ready)
                assertEquals(0, fixture.auth.calls)
                fixture.auth.held = true
                val caller = fixture.track(root.decide(identity, true))
                val prompt = fixture.auth.take()

                val required = assertNotNull(fixture.reads.requireFreshRead(scope))
                val read = fixture.hold("read")
                fixture.socket.refreshAppState(required)
                read.entered.await()
                assertTrue(fixture.cache.synchronization.value.loading)
                assertTrue(fixture.cache.synchronization.value.activeRefreshes > 0)
                assertEquals(scope, fixture.initialization.currentScope())
                assertEquals(1, fixture.auth.calls)
                assertEquals(0, fixture.core.calls.value.count { it.name == "accept" })

                read.release.complete(Unit)
                read.exited.await()
                val proof = required.completion.await()
                fixture.idle()
                assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
                assertEquals(scope, proof.scope)
                assertTrue(required in proof.coveredRequirements)
                prompt.success()
                // join also handles the baseline cancelled child; never replace or reissue this Job.
                caller.join()
                session.actions.awaitSettled()
                fixture.idle()

                assertTrue(caller.isCompleted)
                assertFalse(session.isDisposed)
                assertSame(session, root.state.value.content?.session)
                assertEquals(scope, fixture.initialization.currentScope())
                val current = assertNotNull(fixture.reads.frames.value)
                assertEquals(scope, current.scope)
                assertEquals(NativeReadOutcome.SUCCESS, current.outcome)
                assertEquals(identity, assertNotNull(session.currentMember()).operations.single {
                    it.identity == identity
                }.identity)
                assertEquals(1, fixture.auth.calls)
                assertEquals(1, fixture.core.calls.value.count { it.name == "accept" },
                    "Expected one native entry for the original decision after the real technical read")
                assertEquals(listOf(NativeCall("accept", listOf(identity.requestId))),
                    fixture.core.calls.value.filter { it.name == "accept" })
                assertFalse(caller.isCancelled)
                assertEquals(LiveboardActionPhase.Submitted, session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(identity)
                }.phase)
            } finally {
                fixture.close()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}
