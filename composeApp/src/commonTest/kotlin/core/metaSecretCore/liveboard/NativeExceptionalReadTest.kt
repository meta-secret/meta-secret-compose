package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeExceptionalReadTest {
    @Test
    fun testOldThrownSocketReadCannotRevokeNewerManagerProofOrShowStaleError() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeLifecycleTestFixture()
            val committed = CompletableDeferred<NativeReadFrame>()
            val observer = launch(fixture.immediate, CoroutineStart.UNDISPATCHED) {
                fixture.reads.frames.collect { frame ->
                    if (frame?.outcome == NativeReadOutcome.SUCCESS) {
                        fixture.core.record("newer-committed")
                        committed.complete(frame)
                    }
                }
            }
            try {
                fixture.initialize(this)
                fixture.core.planRead(failure = IllegalStateException("old entered read failed"))
                fixture.socket.refreshAppState()
                val oldWorker = fixture.io.runNext(this).also { fixture.track(it) }
                val oldEntry = fixture.core.nextEntry("read")
                val required = assertNotNull(fixture.reads.requireFreshRead())
                val newerCall = fixture.startRead(this)

                // On the real Mutex unlock, resume B through its real caller before A's catch.
                fixture.io.resumeInline()
                oldEntry.release.complete(Unit)
                oldWorker.join()
                val newerState = assertNotNull(newerCall.await())
                fixture.awaitSocketIdle()
                assertTrue(committed.isCompleted, "The actual newer commit must be observed, rather than awaited by a deadline")
                val newer = committed.await()
                val trace = fixture.core.trace.value
                val commitIndex = trace.indexOf("newer-committed")
                val catchIndex = trace.indexOf("socket-catch")
                assertTrue(commitIndex >= 0 && (catchIndex < 0 || commitIndex < catchIndex),
                    "Harness must commit actual Manager B before Socket A handles its old throw: $trace")
                assertEquals(NativeReadOutcome.SUCCESS, newer.outcome)
                assertTrue(required in newer.coveredRequirements)

                val failures = mutableListOf<String>()
                if (fixture.cache.canonicalFrames.value !== newer) failures.add("old throw revoked newer canonical proof")
                if (fixture.cache.appState.value !== newerState) failures.add("Socket catch replaced newer successful data")
                if (fixture.notifications.errorMessages.isNotEmpty()) failures.add("Socket catch showed stale read error")
                if (!required.completion.isCompleted) failures.add("newer required read did not complete")
                if (fixture.core.counts.value.calls != 2 || fixture.core.counts.value.peak != 1) failures.add("unexpected actual native read execution")
                assertEquals(emptyList(), failures)
            } finally {
                observer.cancel()
                observer.join()
                fixture.close()
            }
        }
    }
}
