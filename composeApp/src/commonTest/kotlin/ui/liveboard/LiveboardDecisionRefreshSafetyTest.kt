package ui.liveboard

import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import models.apiModels.liveboard.LiveboardOperationIdentity

class LiveboardDecisionRefreshSafetyTest {
    @Test
    fun testFreshChangedRequestOrPermissionCannotEnterOrSelectAnotherHead() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            for (change in listOf("claim", "actor", "permission")) withFixture { c ->
                val gesture = beginGesture(c)
                // Core snapshots raw state BEFORE its held read, so change the fixture before entry.
                c.fixture.core.stale(c.identity, change)
                val reading = beginRead(c)
                successfulRead(c, reading)
                finishGesture(c, gesture)
                val member = assertNotNull(c.session.currentMember())
                if (change == "permission") {
                    assertFalse(member.operations.single { it.identity == c.identity }.permissions.canApprove)
                } else {
                    assertTrue(member.operations.none { it.identity == c.identity })
                }
                assertTrue(member.operations.any { it.identity.kind == LiveboardKind.Join &&
                    it.permissions.canApprove }, "An actionable different request must not become a fallback")
                noDecisionEntries(c)
                assertEquals(gesture.authCalls, c.fixture.auth.calls)
                assertEquals(c.scope, c.fixture.initialization.currentScope())
                assertFalse(c.session.isDisposed)
            }
        }
    }

    @Test
    fun testFailedReadEndsOriginalDecisionEvenWhenItsDemandLaterSucceeds() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { c ->
                val gesture = beginGesture(c)
                val reading = beginRead(c)
                c.fixture.core.failRead = true
                releaseRead(c, reading)
                assertTrue(c.fixture.cache.synchronization.value.failed)
                assertFalse(reading.required.completion.isCompleted)
                finishGesture(c, gesture)
                noDecisionEntries(c)
                assertTrue(gesture.caller.isCompleted)

                c.fixture.core.failRead = false
                val later = c.fixture.proof()
                c.fixture.idle()
                assertEquals(NativeReadOutcome.SUCCESS, later.outcome)
                assertEquals(c.scope, later.scope)
                val delayedDemand = reading.required.completion.await()
                assertEquals(NativeReadOutcome.SUCCESS, delayedDemand.outcome)
                assertEquals(c.scope, delayedDemand.scope)
                c.session.actions.awaitSettled()
                assertTrue(gesture.caller.isCompleted)
                assertTrue(c.session.actions.presentation().none {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }, "Later canonical success must not recreate the ended decision")
                noDecisionEntries(c)
                assertEquals(gesture.authCalls, c.fixture.auth.calls)
            }
        }
    }

    @Test
    fun testHardRevocationsEndUnenteredDecisionAcrossTechnicalRead() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            for (boundary in listOf("background", "route", "filter", "hide", "caller-cancel",
                "dispose", "auth-denial")) withFixture { c ->
                if (boundary == "hide") {
                    c.root.show("pass-a").join()
                    assertNotNull(c.session.sensitive.copyValue("pass-a"))
                }
                val gesture = beginGesture(c)
                val reading = beginRead(c)
                when (boundary) {
                    "background" -> c.fixture.socket.onAppBackground()
                    "route" -> c.root.selectRoute(LiveboardRootRoute.Secrets)
                    "filter" -> c.root.searchChanged("different fixture filter")
                    "hide" -> c.root.hide("pass-a")
                    "caller-cancel" -> gesture.caller.cancel()
                    "dispose" -> c.session.dispose()
                    "auth-denial" -> gesture.prompt.error("Fixture OS Cancel")
                }
                releaseRead(c, reading)
                if (boundary != "auth-denial") gesture.prompt.success()
                gesture.caller.join()
                c.session.actions.awaitSettled()
                assertTrue(gesture.caller.isCompleted)
                assertEquals(c.scope, c.fixture.initialization.currentScope())
                assertEquals(gesture.authCalls, c.fixture.auth.calls)
                noDecisionEntries(c, boundary)
                assertTrue(c.session.actions.presentation().none {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }, "Hard revocation must release the original unentered decision")
                if (boundary == "dispose") assertTrue(c.session.isDisposed)
            }
        }
    }

    @Test
    fun testOriginalDecisionCannotAdoptSuccessorAfterSuccessfulTechnicalRead() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            withFixture { c ->
                val gesture = beginGesture(c)
                successfulRead(c, beginRead(c))
                // Real Reset/init B occurs before the original held OS reply; no held native lease.
                val successor = c.fixture.newOwner()
                assertNotEquals(c.scope, successor.scope)
                gesture.prompt.success()
                gesture.caller.join()
                c.session.actions.awaitSettled()
                val liveRoot = c.fixture.root()
                val live = c.fixture.session(liveRoot)
                assertNotSame(c.session, live)
                assertEquals(successor.scope, c.fixture.initialization.currentScope())
                assertTrue(gesture.caller.isCompleted)
                assertTrue(live.actions.presentation().isEmpty())
                assertEquals(gesture.authCalls, c.fixture.auth.calls)
                noDecisionEntries(c)
            }
        }
    }

    @Test
    fun testManagerAndNestedDeletionReadBalanceBeforeSingleOriginalEntry() = runBlocking {
        withTimeout(TIMEOUT_MS) {
            for (source in listOf("manager", "nested-deletion")) withFixture { c ->
                val gesture = beginGesture(c)
                val required = assertNotNull(c.fixture.reads.requireFreshRead(c.scope))
                val gate = c.fixture.hold("read")
                val publisher = c.fixture.track(async(Dispatchers.IO) {
                    if (source == "manager") {
                        assertTrue(assertNotNull(c.fixture.manager.getStateModel()).success)
                    } else {
                        assertTrue(assertNotNull(c.fixture.manager.decideDeleteSecret(FeatureIds.delete,
                            true)).success)
                    }
                })
                gate.entered.await()
                assertTrue(c.fixture.cache.synchronization.value.loading)
                assertEquals(if (source == "manager") 1 else 2,
                    c.fixture.cache.synchronization.value.activeRefreshes)
                assertEquals(0, c.fixture.core.calls.value.count { it.name == "accept" })
                gate.release.complete(Unit)
                gate.exited.await()
                publisher.await()
                val proof = required.completion.await()
                assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
                assertEquals(c.scope, proof.scope)
                assertTrue(required in proof.coveredRequirements)
                c.fixture.idle()
                assertEquals(0, c.fixture.cache.synchronization.value.activeRefreshes)
                assertFalse(c.fixture.cache.synchronization.value.loading)
                assertFalse(c.fixture.cache.synchronization.value.failed)
                finishGesture(c, gesture)
                assertEquals(gesture.authCalls, c.fixture.auth.calls)
                assertEquals(c.scope, c.fixture.initialization.currentScope())
                assertEquals(listOf(NativeCall("accept", listOf(c.identity.requestId))),
                    c.fixture.core.calls.value.filter { it.name == "accept" },
                    "The same decision must enter once after balanced real publisher settlement")
                assertEquals(LiveboardActionPhase.Submitted, c.session.actions.presentation().single {
                    it.target == LiveboardActionTarget.Decision(c.identity)
                }.phase)
                assertFalse(gesture.caller.isCancelled)
            }
        }
    }

    private data class Harness(val fixture: LiveboardRootTestFixture, val root: LiveboardRootCoordinator,
        val session: LiveboardSession, val identity: LiveboardOperationIdentity, val scope: NativeReadScope)
    private data class Gesture(val caller: Job, val prompt: FeatureAuthReply, val authCalls: Int)
    private data class Reading(val gate: NativeTransitionGate, val required: NativeReadRequirement)

    private suspend fun withFixture(block: suspend (Harness) -> Unit) {
        val f = LiveboardRootTestFixture()
        try {
            f.start()
            val root = f.root()
            val session = f.session(root)
            val identity = assertNotNull(session.currentMember()).operations.single {
                it.identity.kind == LiveboardKind.Recover
            }.identity
            block(Harness(f, root, session, identity, assertNotNull(f.initialization.currentScope())))
        } finally {
            f.close()
        }
    }

    private suspend fun beginGesture(c: Harness): Gesture {
        assertTrue(c.session.inbox.state.value.ready)
        val before = c.fixture.auth.calls
        c.fixture.auth.held = true
        val caller = c.fixture.track(c.root.decide(c.identity, true))
        val prompt = c.fixture.auth.take()
        assertEquals(before + 1, c.fixture.auth.calls)
        return Gesture(caller, prompt, c.fixture.auth.calls)
    }

    private suspend fun beginRead(c: Harness): Reading {
        val required = assertNotNull(c.fixture.reads.requireFreshRead(c.scope))
        val gate = c.fixture.hold("read")
        c.fixture.socket.refreshAppState(required)
        gate.entered.await()
        assertTrue(c.fixture.cache.synchronization.value.loading)
        assertTrue(c.fixture.cache.synchronization.value.activeRefreshes > 0)
        assertEquals(0, c.fixture.core.calls.value.count { it.name == "accept" })
        return Reading(gate, required)
    }

    private suspend fun releaseRead(c: Harness, reading: Reading) {
        reading.gate.release.complete(Unit)
        reading.gate.exited.await()
        c.fixture.idle()
    }

    private suspend fun successfulRead(c: Harness, reading: Reading) {
        releaseRead(c, reading)
        val proof = reading.required.completion.await()
        assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
        assertEquals(c.scope, proof.scope)
        assertTrue(reading.required in proof.coveredRequirements)
    }

    private suspend fun finishGesture(c: Harness, gesture: Gesture) {
        gesture.prompt.success()
        gesture.caller.join()
        c.session.actions.awaitSettled()
        c.fixture.idle()
        assertTrue(gesture.caller.isCompleted)
    }

    private fun noDecisionEntries(c: Harness, boundary: String = "unspecified") {
        assertTrue(c.fixture.core.calls.value.none { it.name in DECISION_ROUTES },
            "The original gesture must not enter any decision route or substitute another head; boundary=$boundary")
    }

    private companion object {
        const val TIMEOUT_MS = 45_000L
        val DECISION_ROUTES = setOf("accept", "decline", "join", "decideRemove", "decideDelete")
    }
}
