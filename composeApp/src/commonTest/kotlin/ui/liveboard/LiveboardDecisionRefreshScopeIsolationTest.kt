package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardDecisionRefreshScopeIsolationTest {
    @Test
    fun testLateRetiredScopeCannotCancelOriginalSuccessorDecision() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            val fixture = LiveboardRootTestFixture()
            try {
                fixture.start()
                val rootA = fixture.root()
                val sessionA = fixture.session(rootA)
                val scopeA = assertNotNull(fixture.initialization.currentScope())

                val successor = fixture.newOwner()
                val scopeB = assertNotNull(successor.scope)
                assertNotEquals(scopeA, scopeB)
                val rootB = fixture.root()
                val sessionB = fixture.session(rootB)
                assertNotSame(sessionA, sessionB)
                val identityB = assertNotNull(sessionB.currentMember()).operations.single {
                    it.identity.kind == LiveboardKind.Recover
                }.identity
                val currentB = assertNotNull(fixture.reads.frames.value)
                assertEquals(scopeB, currentB.scope)
                assertEquals(NativeReadOutcome.SUCCESS, currentB.outcome)
                assertTrue(sessionB.inbox.state.value.ready)
                assertEquals(0, fixture.auth.calls)
                fixture.auth.held = true
                val caller = fixture.track(rootB.decide(identityB, true))
                val prompt = fixture.auth.take()
                assertFalse(caller.isCompleted)
                assertEquals(1, fixture.auth.calls)
                assertTrue(fixture.core.calls.value.isEmpty())

                // An actual late A finalizer, not a forged failure or a callback between B guards.
                fixture.reads.retireScope(scopeA)
                assertSame(currentB, fixture.reads.frames.value)
                assertEquals(scopeB, fixture.initialization.currentScope())
                assertSame(sessionB, rootB.state.value.content?.session)
                prompt.success()
                caller.join()
                sessionB.actions.awaitSettled()
                fixture.idle()

                assertTrue(caller.isCompleted)
                assertFalse(sessionB.isDisposed)
                assertEquals(scopeB, fixture.initialization.currentScope())
                assertSame(sessionB, rootB.state.value.content?.session)
                assertEquals(identityB, assertNotNull(sessionB.currentMember()).operations.single {
                    it.identity == identityB
                }.identity)
                assertEquals(1, fixture.auth.calls)
                assertEquals(listOf(NativeCall("accept", listOf(identityB.requestId))),
                    fixture.core.calls.value,
                    "Late scope A retirement must preserve exactly one original scope B decision entry")
                assertFalse(caller.isCancelled)
                val returned = sessionB.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(identityB)
                }
                assertEquals(LiveboardActionPhase.Submitted, returned.phase)
                assertEquals(identityB.requestId, returned.requestId)
            } finally {
                fixture.close()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}
