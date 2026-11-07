package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardSessionDuplicateAwaitTest {
    @Test
    fun testCancellingOnlyADuplicateCannotCancelTheOriginalQueuedProducer() = runBlocking {
        withTimeout(30_000) {
            val f = LiveboardSessionCommandFixture()
            val failures = mutableListOf<String>()
            try {
                f.initialize("duplicate-key-a")
                val profile = f.profile()
                val frameA = f.proof()
                val session = f.session(frameA)
                val intent = assertNotNull(session.removeDeviceIntent("device-b"))
                f.io.queue()
                val original = f.track(async(f.main, CoroutineStart.UNDISPATCHED) { session.removeDevice(intent) })
                val task = f.io.take()
                assertTrue(!original.isCompleted && !original.isCancelled)
                assertTrue(f.core.calls.value.isEmpty())
                val duplicate = f.track(async(f.main, CoroutineStart.UNDISPATCHED) { session.removeDevice(intent) })
                // A duplicate may reject itself immediately or wait; only its awaiter is cancelled.
                duplicate.cancel(); duplicate.join()
                if (!duplicate.isCancelled) assertIs<NativeLiveboardOutcome.NotEntered>(duplicate.await())
                assertTrue(!original.isCancelled)
                if (original.isCompleted) failures.add("Duplicate cancellation completed the original before its actual IO runnable")
                if (session.removeDeviceIntent("device-b") != null) failures.add("Duplicate cancellation released the original target slot")
                f.io.resumeInline()
                f.run(task, this).join()
                val actual = original.await()
                session.awaitSettled()
                if (actual !is NativeLiveboardOutcome.Entered) failures.add("Live original returned NotEntered after only its duplicate was cancelled")
                else {
                    assertEquals(frameA.scope, actual.scope)
                    assertEquals(f.expectedProducer(), actual.result)
                }
                val oneCall = listOf(NativeCall("remove", listOf("device-b")))
                if (f.core.calls.value != oneCall) failures.add("Original did not enter exactly one actual Core remove")
                val retained = session.retainedProducer(intent)
                if (retained == null) failures.add("Original producer truth was not retained")
                else {
                    assertEquals(actual, retained)
                    assertEquals(f.expectedProducer(), retained.result)
                }
                val beforeFresh = f.core.calls.value.size
                f.reset(profile); f.resetFinished(profile)
                f.initialize("duplicate-key-b")
                val frameB = f.proof()
                assertNull(f.factory.open(frameA))
                val fresh = f.session(frameB)
                val freshIntent = assertNotNull(fresh.removeDeviceIntent("device-b"))
                val positive = assertIs<NativeLiveboardOutcome.Entered<*>>(fresh.removeDevice(freshIntent))
                assertEquals(frameB.scope, positive.scope)
                assertEquals(f.expectedProducer(), positive.result)
                assertEquals(oneCall, f.core.calls.value.drop(beforeFresh))
                assertNotNull(fresh.retainedProducer(freshIntent))
                assertEquals(emptyList(), failures, "Original actual runnable/settlement and real Reset/init-B/fresh producer controls reached")
            } finally { f.close() }
        }
    }
}
