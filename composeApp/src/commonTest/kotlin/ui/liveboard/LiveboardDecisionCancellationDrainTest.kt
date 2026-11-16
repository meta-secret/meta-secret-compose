package ui.liveboard

import core.metaSecretCore.liveboard.FeatureAuthReply
import core.metaSecretCore.liveboard.LiveboardKind
import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.NativeCheckedIo
import core.metaSecretCore.liveboard.NativeHeldPublication
import core.metaSecretCore.liveboard.NativeReadOutcome
import core.metaSecretCore.liveboard.NativeReadRequirement
import core.metaSecretCore.liveboard.NativeTransitionGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Actual Root awaiter cancellation; native/journal dispatchers remain the fixture's real IO. */
class LiveboardDecisionCancellationDrainTest {
    private class Running(val session: LiveboardSession, val caller: Job, val prompt: FeatureAuthReply,
        val read: NativeTransitionGate, val required: NativeReadRequirement)

    @Test fun testCompletedCallerCancellationBlocksEntryBeforeAndAfterLateAuthentication() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            var running: Running? = null
            try {
                f.start()
                val root = f.root()
                val r = begin(f, root).also { running = it }
                r.caller.cancelAndJoin()
                assertTrue(r.caller.isCancelled && r.caller.isCompleted)
                noEntries(f)
                releaseRead(f, r)
                r.prompt.success()
                r.session.actions.awaitSettled()
                f.idle()
                noEntries(f)
                assertEquals(1, f.auth.calls)
            } finally {
                withContext(NonCancellable) {
                    running?.read?.release?.complete(Unit)
                    f.close()
                }
            }
        }
    }

    @Test fun testCancellationSignalBlocksEntryEvenBeforeRootAwaiterContinuationDrains() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardRootTestFixture()
            val callerIo = NativeCheckedIo()
            var running: Running? = null
            var cancellation: NativeHeldPublication? = null
            try {
                f.start()
                val root = f.root(io = callerIo)
                val r = begin(f, root).also { running = it }
                // Before this point callerIo is inline and no task is queued.
                // It is used ONLY by Root views; real native/journal IO is not replaced.
                callerIo.queue()
                r.caller.cancel()
                val held = callerIo.take().also { cancellation = it }
                assertTrue(r.caller.isCancelled && !r.caller.isCompleted,
                    "Actual caller cancellation continuation must remain held before drain")
                noEntries(f)
                releaseRead(f, r)
                r.prompt.success()
                r.session.actions.awaitSettled()
                f.idle()
                // Critical oracle BEFORE running the queued cancellation continuation.
                assertTrue(r.caller.isCancelled && !r.caller.isCompleted)
                noEntries(f)
                assertEquals(1, f.auth.calls)
                callerIo.resumeInline()
                f.publish(held, this)
                r.caller.join()
                r.session.actions.awaitSettled()
                f.idle()
                assertTrue(r.caller.isCancelled && r.caller.isCompleted)
                noEntries(f)
                assertEquals(1, f.auth.calls)
            } finally {
                // Drain the exact owned continuation before fixture.close can join Root views.
                withContext(NonCancellable) {
                    try {
                        running?.read?.release?.complete(Unit)
                        callerIo.resumeInline()
                        cancellation?.let { f.publish(it, this) }
                    } finally {
                        try { f.finishControlled(callerIo) } finally { f.close() }
                    }
                }
            }
        }
    }

    private suspend fun begin(f: LiveboardRootTestFixture, root: LiveboardRootCoordinator): Running {
        val session = f.session(root)
        val identity = assertNotNull(session.currentMember()).operations.single {
            it.identity.kind == LiveboardKind.Recover
        }.identity
        assertTrue(session.inbox.state.value.ready)
        f.auth.held = true
        val caller = f.track(root.decide(identity, true))
        val prompt = f.auth.take()
        val scope = assertNotNull(f.initialization.currentScope())
        val required = assertNotNull(f.reads.requireFreshRead(scope))
        val read = f.hold("read")
        f.socket.refreshAppState(required)
        read.entered.await()
        assertTrue(f.cache.synchronization.value.loading)
        assertTrue(f.cache.synchronization.value.activeRefreshes > 0)
        assertEquals(1, f.auth.calls)
        noEntries(f)
        return Running(session, caller, prompt, read, required)
    }
    private suspend fun releaseRead(f: LiveboardRootTestFixture, r: Running) {
        r.read.release.complete(Unit)
        r.read.exited.await()
        val frame = r.required.completion.await()
        f.idle()
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertEquals(r.session.scope, frame.scope)
        assertTrue(r.required in frame.coveredRequirements)
    }
    private fun noEntries(f: LiveboardRootTestFixture) {
        assertTrue(f.core.calls.value.none { it.name in DECISIONS },
            "Original cancelled gesture must not enter any native decision or another head")
    }
    private companion object {
        val DECISIONS = setOf("accept", "decline", "join", "decideRemove", "decideDelete")
    }
}
