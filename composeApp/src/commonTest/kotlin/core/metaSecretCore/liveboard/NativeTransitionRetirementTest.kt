package core.metaSecretCore.liveboard

import core.metaSecretCore.InitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeTransitionRetirementTest {
    @Test
    fun testCancelledChangedKeyAndResetOperationsRetirePendingRequirementsAfterActualCompletion() = runBlocking {
        withTimeout(10_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (stage in listOf("read", "init", "drain", "clear")) {
                val f = NativeTransitionFixture()
                try {
                    f.initialize(this)
                    f.pump(this)
                    val original = assertNotNull(f.initialization.currentScope())
                    val pending = assertNotNull(f.reads.requireFreshRead())
                    val gate = f.gate()
                    var native: Job? = null
                    when (stage) {
                        "read" -> {
                            // Cancelling a same-key read may legitimately preserve its existing epoch.
                            f.keys.saveString("master_key", "changed-key-read")
                            f.keys.readGate = gate
                        }
                        "init" -> {
                            f.keys.saveString("master_key", "changed-key-init")
                            f.core.initGate = gate
                        }
                        "drain" -> {
                            f.core.tokenGate = gate
                            native = f.track(async(Dispatchers.IO) {
                                f.initialization.invoke(original) { f.core.stateEventsAuthToken("parser-vault") }
                            })
                        }
                        else -> f.keys.clearGate = gate
                    }
                    if (stage == "drain") gate.entered.await()
                    val operation = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                        if (stage == "drain" || stage == "clear") f.reset.reset(original.owner)
                        else f.manager.initWithSavedKey()
                    })
                    if (stage != "drain") gate.entered.await()
                    assertNull(f.initialization.currentScope(), "$stage: actual transition must close admission")
                    operation.cancel()
                    gate.release.complete(Unit)
                    gate.exited.await()
                    native?.join()
                    operation.join()
                    assertTrue(operation.isCancelled, "$stage: the actual caller was cancelled")
                    assertNotSame(original.owner, f.initialization.capture())
                    assertEquals(if (stage == "read" || stage == "init") 2 else 1, f.core.inits.value)
                    assertEquals(if (stage == "drain" || stage == "clear") 1 else 0, f.keys.clears.value)
                    recordTerminal(pending, "cancel-$stage", failures)

                    // A real successor is a positive control even when the old promise is stranded.
                    f.keys.saveString("master_key", "successor-$stage")
                    assertIs<InitResult.Success>(f.manager.initWithSavedKey())
                    val successor = assertNotNull(f.initialization.currentScope())
                    assertNotSame(original.owner, successor.owner)
                    assertFreshRead(f, successor)
                    assertSame(successor.owner, f.initialization.capture())
                    assertTrue(f.notifications.errorMessages.isEmpty())
                    reached.add(stage)
                } finally { f.close() }
            }
            assertEquals(listOf("read", "init", "drain", "clear"), reached)
            assertEquals(emptyList(), failures, "Completed cancellation variants and real successor reads: $reached")
        }
    }

    @Test
    fun testNullTransitionReceiptsRetireOldRequirementsAndPreserveRealSuccessorProof() = runBlocking {
        withTimeout(10_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (stage in listOf("init", "reset")) {
                val f = NativeTransitionFixture()
                try {
                    f.initialize(this)
                    f.pump(this)
                    val original = assertNotNull(f.initialization.currentScope())
                    val pending = assertNotNull(f.reads.requireFreshRead())
                    val gate = f.gate()
                    if (stage == "init") {
                        f.keys.saveString("master_key", "changed-key")
                        f.core.initGate = gate
                    } else f.keys.clearGate = gate
                    val old = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                        if (stage == "init") f.lifecycle.initializeSavedKey(original.owner)
                        else f.reset.reset(original.owner)
                    })
                    gate.entered.await()
                    assertNull(f.initialization.currentScope())
                    f.initialization.invalidate()
                    val successorOwner = f.initialization.capture()
                    assertNotSame(original.owner, successorOwner)
                    // Both paths have already performed their old key effects before this gate.
                    f.keys.saveString("master_key", "successor-$stage")
                    val before = f.core.inits.value to f.keys.reads.value
                    val successor = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                        f.manager.initWithSavedKey()
                    })
                    assertEquals(before, f.core.inits.value to f.keys.reads.value,
                        "$stage: successor must wait for the actual old reservation")
                    assertSame(successorOwner, f.initialization.capture())
                    gate.release.complete(Unit)
                    gate.exited.await()
                    assertNull(old.await(), "$stage: invalidated transition must actually return a null receipt")
                    recordTerminal(pending, "null-$stage", failures)
                    assertIs<InitResult.Success>(successor.await())
                    val ready = assertNotNull(f.initialization.currentScope())
                    assertNotSame(original.owner, ready.owner)
                    assertSame(ready.owner, f.initialization.capture())
                    val frame = assertFreshRead(f, ready)
                    old.join()
                    assertSame(frame, f.cache.canonicalFrames.value)
                    assertEquals("successor-$stage", f.keys.getString("master_key"))
                    assertEquals(if (stage == "init") 3 else 2, f.core.inits.value)
                    assertTrue(f.notifications.errorMessages.isEmpty())
                    reached.add(stage)
                } finally { f.close() }
            }
            assertEquals(listOf("init", "reset"), reached)
            assertEquals(emptyList(), failures, "Completed null-receipt variants and real successor reads: $reached")
        }
    }

    @Test
    fun testCancellingForeignTransitionWaiterPreservesSameKeyEpochAndPendingRead() = runBlocking {
        withTimeout(10_000) {
            val f = NativeTransitionFixture()
            try {
                f.initialize(this)
                f.pump(this)
                val original = assertNotNull(f.initialization.currentScope())
                val pending = assertNotNull(f.reads.requireFreshRead())
                val gate = f.gate()
                f.keys.readGate = gate
                val operation = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.manager.initWithSavedKey()
                })
                gate.entered.await()
                assertNull(f.initialization.currentScope())
                val before = f.core.inits.value to f.keys.reads.value
                val foreign = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.reset.reset(original.owner)
                })
                assertTrue(foreign.isActive && !foreign.isCompleted,
                    "UNDISPATCHED foreign caller must suspend behind the actual held same-key reservation")
                assertEquals(before, f.core.inits.value to f.keys.reads.value)
                foreign.cancel()
                foreign.join()
                assertTrue(foreign.isCancelled)
                assertTrue(operation.isActive, "Cancelling a foreign waiter must not cancel the transition owner")
                assertTrue(!pending.completion.isCompleted, "Foreign cancellation must not retire A")
                assertEquals(0, f.keys.clears.value)
                gate.release.complete(Unit)
                gate.exited.await()
                assertIs<InitResult.Success>(operation.await())
                val same = assertNotNull(f.initialization.currentScope())
                assertSame(original.owner, same.owner)
                assertSame(original.epoch, same.epoch)
                assertEquals(1, f.core.inits.value)
                assertEquals(0, f.keys.clears.value)
                assertTrue(!pending.completion.isCompleted, "Same-key transition must preserve pending A until a real read")
                val state = assertNotNull(f.manager.getStateModel())
                val frame = assertNotNull(f.cache.canonicalFrames.value)
                assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
                assertSame(state, frame.state)
                assertEquals(original, frame.scope)
                assertTrue(pending in frame.coveredRequirements)
                assertTrue(pending.completion.isCompleted, "The real physical read must fulfill preserved A")
                assertSame(frame, pending.completion.await())
                assertEquals(1, f.core.reads.value)
                assertTrue(f.notifications.errorMessages.isEmpty())
            } finally { f.close() }
        }
    }

    private suspend fun recordTerminal(target: NativeReadRequirement, label: String, failures: MutableList<String>) {
        if (!target.completion.isCompleted) {
            failures.add("$label: completed actual operation left old requirement pending")
        } else if (target.completion.isCancelled) {
            failures.add("$label: old requirement was cancelled instead of receiving RETIRED")
        } else {
            // Never await an uncompleted promise: missing retirement is an immediate semantic result.
            val reply = target.completion.await()
            if (reply.outcome != NativeReadOutcome.RETIRED || reply.state != null ||
                reply.scope != target.scope || reply.coveredRequirements.isNotEmpty()) {
                failures.add("$label: old requirement did not receive payload-free, exact-scope RETIRED")
            }
        }
    }

    private suspend fun assertFreshRead(f: NativeTransitionFixture, scope: NativeReadScope): NativeReadFrame {
        val target = assertNotNull(f.reads.requireFreshRead())
        val before = f.core.reads.value
        val state = assertNotNull(f.manager.getStateModel())
        val frame = assertNotNull(f.cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertEquals(scope, frame.scope)
        assertSame(state, frame.state)
        assertSame(state, f.cache.appState.value)
        assertTrue(target in frame.coveredRequirements)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
        assertEquals(before + 1, f.core.reads.value)
        return frame
    }
}
