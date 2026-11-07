package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeQueuedReadOriginTest {
    @Test
    fun testManagerLocalAndQueuedSocketOriginsCannotReadOrCertifyAnUnrelatedSuccessor() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            managerObservableOrigin(this, failures, reached)
            localMutexOrigin(this, failures, reached)
            socketScheduledOrigin(this, failures, reached)
            assertEquals(3, reached.size)
            assertEquals(emptyList(), failures, "All three actual origins, owner boundaries and fresh-positive tails reached: $reached")
        }
    }

    private suspend fun managerObservableOrigin(
        scope: CoroutineScope,
        failures: MutableList<String>,
        reached: MutableList<String>,
    ) {
        val f = NativeSocketVisibilityFixture()
        try {
            f.start(scope)
            val origin = assertNotNull(f.initialization.currentScope())
            val profile = f.profile()
            // There is no held native work here; fast real key/native calls may run reentrantly.
            f.io.resumeInline()
            var armed = true
            var checkpoint = -1
            var successor: NativeReadScope? = null
            var late: NativeReadRequirement? = null
            val replaced = CompletableDeferred<Unit>()
            val observer = f.track(scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                f.cache.synchronization.collect { sync ->
                    if (armed && sync.activeRefreshes == 1) {
                        armed = false
                        f.reset(profile)
                        f.resetFinished(profile)
                        f.initialize("visibility-key-b")
                        successor = assertNotNull(f.initialization.currentScope())
                        late = assertNotNull(f.reads.requireFreshRead())
                        checkpoint = f.trace.calls.value.size
                        replaced.complete(Unit)
                    }
                }
            })
            val old = f.manager.getStateModel()
            assertTrue(replaced.isCompleted, "Actual observer Reset and init B must finish before the old call continues; otherwise harness ordering is unproven")
            assertTrue(checkpoint >= 0)
            val current = assertNotNull(successor)
            assertNotSame(origin.owner, current.owner)
            val target = assertNotNull(late)
            if (old?.success == true) failures.add("Manager: its pre-read cache observer changed owner, but old A returned usable B state")
            recordOldTail(f, checkpoint, target, "Manager-before-observable", failures)
            assertEquals(current, f.initialization.currentScope())
            assertEquals("visibility-key-b", f.keys.getString("master_key"))
            observer.cancel(); observer.join()
            val fresh = f.freshManagerProof()
            assertEquals(current, fresh.scope)
            recordLateCompletion(target, fresh, "Manager-before-observable", failures)
            assertEquals(2, f.trace.count("core.init"))
            reached.add("Manager/actual-cache-observer-Reset-init-B/old-A/fresh-B")
        } finally { f.close() }
    }

    private suspend fun localMutexOrigin(
        scope: CoroutineScope,
        failures: MutableList<String>,
        reached: MutableList<String>,
    ) {
        val f = NativeSocketVisibilityFixture()
        try {
            f.start(scope)
            val origin = assertNotNull(f.initialization.currentScope())
            val profile = f.profile()
            val physical = f.hold("read")
            val entered = f.track(scope.async(Dispatchers.IO) { f.manager.getStateModel() })
            physical.entered.await()
            val old = f.track(scope.async(f.caller, CoroutineStart.UNDISPATCHED) { f.local.new() })
            assertTrue(!old.isCompleted && !old.isCancelled)
            f.reset(profile)
            assertNull(f.initialization.currentScope(), "Actual Reset closed admission while waiting for entered A")
            physical.release.complete(Unit)
            // Take the actual Mutex-restoration runnable; the pending waiter now owns the mutex.
            val continuation = f.caller.take()
            physical.exited.await()
            entered.await()
            f.resetFinished(profile)
            f.initialize("visibility-key-b")
            val current = assertNotNull(f.initialization.currentScope())
            assertNotSame(origin.owner, current.owner)
            assertTrue(!old.isCompleted && !old.isCancelled, "Old Local caller remains live through actual successor initialization")
            val target = assertNotNull(f.reads.requireFreshRead())
            val checkpoint = f.trace.calls.value.size
            continuation.publish()
            f.track(f.caller.drain(scope))
            assertNull(old.await())
            assertTrue(!old.isCancelled)
            recordOldTail(f, checkpoint, target, "Local-before-mutex", failures)
            assertEquals(current, f.initialization.currentScope())
            assertEquals("visibility-key-b", f.keys.getString("master_key"))
            // A fresh read cannot precede execution of the waiter which already owns the mutex.
            val fresh = f.freshManagerProof()
            assertEquals(current, fresh.scope)
            recordLateCompletion(target, fresh, "Local-before-mutex", failures)
            assertEquals(2, f.trace.count("core.init"))
            reached.add("Local/live-mutex-waiter-A/actual-drain-Reset-init-B/old-runnable/fresh-B")
        } finally { f.close() }
    }

    private suspend fun socketScheduledOrigin(
        scope: CoroutineScope,
        failures: MutableList<String>,
        reached: MutableList<String>,
    ) {
        val f = NativeSocketVisibilityFixture(queueSocket = true)
        try {
            f.start(scope)
            val origin = assertNotNull(f.initialization.currentScope())
            f.socket.onAppForeground()
            val scheduled = f.caller.take()
            assertEquals(0, f.cache.synchronization.value.activeRefreshes)
            assertEquals(0, f.trace.count("core.read"))
            // Actual key change, deliberately without background/pause cancelling the old job.
            f.initialize("visibility-key-b")
            val current = assertNotNull(f.initialization.currentScope())
            assertNotSame(origin.owner, current.owner)
            val target = assertNotNull(f.reads.requireFreshRead())
            val checkpoint = f.trace.calls.value.size
            scheduled.publish()
            f.caller.take().publish()
            f.track(f.caller.drain(scope))
            assertEquals(0, f.cache.synchronization.value.activeRefreshes)
            recordOldTail(f, checkpoint, target, "Socket-before-schedule", failures)
            assertEquals(current, f.initialization.currentScope())
            assertEquals("visibility-key-b", f.keys.getString("master_key"))

            val freshTarget = assertNotNull(f.reads.requireFreshRead())
            f.socket.onAppForeground()
            freshTarget.completion.await()
            f.idle()
            val fresh = assertNotNull(f.cache.canonicalFrames.value)
            assertEquals(NativeReadOutcome.SUCCESS, fresh.outcome)
            assertEquals(current, fresh.scope)
            assertSame(fresh.state, f.cache.appState.value)
            assertTrue(freshTarget in fresh.coveredRequirements)
            assertTrue(freshTarget.completion.isCompleted)
            assertSame(fresh, freshTarget.completion.await())
            recordLateCompletion(target, fresh, "Socket-before-schedule", failures)
            assertEquals(2, f.trace.count("core.init"))
            reached.add("Socket/live-scheduled-A/direct-key-init-B/old-runnable/fresh-Socket-B")
        } finally { f.close() }
    }

    private fun recordOldTail(
        f: NativeSocketVisibilityFixture,
        checkpoint: Int,
        late: NativeReadRequirement,
        label: String,
        failures: MutableList<String>,
    ) {
        val tail = f.trace.calls.value.drop(checkpoint)
        if (tail.isNotEmpty()) failures.add("$label: old origin performed successor native/key work: $tail")
        if (late.completion.isCompleted) failures.add("$label: old origin certified the later B requirement")
    }

    private suspend fun recordLateCompletion(
        target: NativeReadRequirement,
        fresh: NativeReadFrame,
        label: String,
        failures: MutableList<String>,
    ) {
        if (!target.completion.isCompleted) failures.add("$label: fresh actual B read did not fulfill the pending B requirement")
        else if (target.completion.await() !== fresh) failures.add("$label: B requirement retained proof from the old-origin read")
    }
}
