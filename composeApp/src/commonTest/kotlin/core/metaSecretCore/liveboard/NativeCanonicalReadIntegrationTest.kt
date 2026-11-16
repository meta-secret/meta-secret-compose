package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeCanonicalReadIntegrationTest {
    @Test
    fun testManagerSocketAndLocalStateShareOnePhysicalRead() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeReadOwnerFixture()
            val calls = mutableListOf<Job>()
            try {
                fixture.initialize(this)
                val manager = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(manager)
                calls.add(fixture.io.runNext(this))
                fixture.core.nextEntry()

                // Unconfined/UNDISPATCHED callers reach either the shared fence or recorded IO enqueue.
                fixture.socket.refreshAppState()
                calls.add(async(start = CoroutineStart.UNDISPATCHED) { fixture.localState.new() })
                val surplus = fixture.io.runCurrentlyQueued(this)
                calls.addAll(surplus)
                repeat(surplus.size) { fixture.core.nextEntry() }

                assertEquals(1, fixture.core.statistics.peak, "Three real callers must not overlap native reads")
                assertSame(fixture.cache.canonicalFrames, fixture.reads.frames)
            } finally {
                fixture.close(this, calls)
            }
        }
    }

    @Test
    fun testRequiredReadCreatedDuringManagerFlightNeedsLaterPhysicalRead() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeReadOwnerFixture()
            val calls = mutableListOf<Job>()
            try {
                fixture.initialize(this)
                val oldRead = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(oldRead)
                calls.add(fixture.io.runNext(this))
                val held = fixture.core.nextEntry()
                val required = assertNotNull(fixture.reads.requireFreshRead())
                fixture.socket.refreshAppState()
                held.release.complete(Unit)
                oldRead.await()

                val oldFrame = assertNotNull(fixture.cache.canonicalFrames.value)
                assertFalse(required in oldFrame.coveredRequirements, "Old FFI already entered before this requirement")
                val later = fixture.completeNextRead(this)
                assertTrue(later.attempt > oldFrame.attempt)
                assertEquals(NativeReadOutcome.SUCCESS, later.outcome)
                assertTrue(required in later.coveredRequirements, "Only the subsequent physical read covers the new requirement")
                assertSame(later.state, fixture.cache.appState.value)
                assertEquals(1, fixture.core.statistics.peak)
            } finally {
                fixture.close(this, calls)
            }
        }
    }
}
