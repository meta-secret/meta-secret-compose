package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeQueuedAdmissionTest {
    @Test
    fun testCancelledQueuedManagerReadOwnsNoLeaseBeforeItsRunnableIsExecuted() = runBlocking {
        withTimeout(10_000) {
            val f = NativeTransitionFixture()
            try {
                f.initialize(this)
                val prior = assertNotNull(f.initialization.currentScope())
                val queued = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) { f.manager.getStateModel() })
                queued.cancel()
                assertEquals(0, f.core.reads.value)

                // Keep the old IO runnable queued. New Reset uses inline IO and must not drain it.
                f.io.resumeInline()
                val reset = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) { f.reset.reset(prior.owner) })
                assertTrue(reset.isCompleted, "Actual Reset must finish BEFORE executing cancelled queued work: it owns zero leases")
                assertEquals(true, assertNotNull(reset.await()).value)
                assertEquals(1, f.keys.clears.value)
                assertEquals(0, f.core.reads.value)
                assertNull(f.cache.canonicalFrames.value)
                assertNull(f.keys.getString("master_key"))

                f.track(f.io.runNext(this)).join()
                queued.join()
                assertTrue(queued.isCancelled)
                assertEquals(0, f.core.reads.value, "Executing the cancelled runnable must enter no Core call")
                f.keys.saveString("master_key", "fresh-after-queued-cancel")
                assertIs<core.metaSecretCore.InitResult.Success>(f.manager.initWithSavedKey())
                val state = assertNotNull(f.manager.getStateModel())
                val fresh = assertNotNull(f.cache.canonicalFrames.value)
                assertSame(state, fresh.state)
                assertEquals(1, f.core.reads.value)
                assertEquals(2, f.core.inits.value)
                assertTrue(f.notifications.errorMessages.isEmpty())
            } finally { f.close() }
        }
    }
}
