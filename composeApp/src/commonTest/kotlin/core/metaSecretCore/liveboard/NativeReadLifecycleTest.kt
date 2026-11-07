package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel

class NativeReadLifecycleTest {
    @Test
    fun testEqualCanonicalReadsKeepOneCoherentSnapshotWithoutDuplicateLegacyEmissions() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeReadOwnerFixture()
            val calls = mutableListOf<Job>()
            val legacyValues = mutableListOf<AppStateModel?>()
            val canonicalIdentity = mutableListOf<Boolean>()
            val publishedAttempts = mutableListOf<Long>()
            val failures = mutableListOf<String>()
            fun observe(label: String, passed: Boolean) { if (!passed) failures.add(label) }
            val legacyObserver = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                fixture.cache.appState.collect { legacyValues.add(it) }
            }
            val canonicalObserver = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                fixture.cache.canonicalFrames.collect { frame ->
                    if (frame != null) {
                        publishedAttempts.add(frame.attempt)
                        canonicalIdentity.add(frame.state === fixture.cache.appState.value)
                    }
                }
            }
            try {
                fixture.initialize(this)
                val firstCall = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(firstCall)
                val first = fixture.completeNextRead(this)
                val firstResult = firstCall.await()
                val firstState = assertNotNull(first.state)
                observe("first actual read succeeded", first.outcome == NativeReadOutcome.SUCCESS)
                observe("first Manager result is canonical snapshot", first.state === firstResult)

                val genericEqual = firstState.copy()
                fixture.cache.updateCache(genericEqual)
                observe("generic update revoked current proof", fixture.cache.canonicalFrames.value == null)
                observe("generic value adopts latest equal instance", fixture.cache.appState.value === genericEqual)
                observe("equal generic update did not emit legacy data", legacyValues.size == 2)

                val secondCall = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(secondCall)
                val second = fixture.completeNextRead(this)
                val secondResult = secondCall.await()
                observe("second actual read succeeded", second.outcome == NativeReadOutcome.SUCCESS)
                observe("equal payload has a freshly parsed snapshot", second.state == first.state && second.state !== first.state)
                observe("second Manager result is canonical snapshot", second.state === secondResult)
                observe("second canonical value is same cache instance", second.state === fixture.cache.appState.value)
                observe("both canonical observers saw coherent instances", canonicalIdentity == listOf(true, true))
                observe("both physical frames were observed", publishedAttempts == listOf(first.attempt, second.attempt))
                observe("equal actual read did not emit duplicate legacy data", legacyValues.size == 2)

                fixture.cache.clearCache()
                observe("clear revoked current proof", fixture.cache.canonicalFrames.value == null)
                observe("clear removed legacy value", fixture.cache.appState.value == null)
                observe("legacy stream has only initial/value/clear", legacyValues == listOf(null, firstState, null))
                observe("exactly two actual native reads", fixture.core.statistics.calls == 2)
                assertEquals(emptyList(), failures, "Collected all identity, emission and revocation guards")
            } finally {
                legacyObserver.cancel()
                canonicalObserver.cancel()
                legacyObserver.join()
                canonicalObserver.join()
                fixture.close(this, calls)
            }
        }
    }

    @Test
    fun testFailedReadAndLegacyCacheCannotCertifyRequiredRead() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeReadOwnerFixture()
            val calls = mutableListOf<Job>()
            try {
                fixture.initialize(this)
                val initial = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(initial)
                val successful = fixture.completeNextRead(this)
                initial.await()
                val cached = assertNotNull(successful.state)
                assertTrue(cached.success)

                val required = assertNotNull(fixture.reads.requireFreshRead())
                fixture.core.response.value = "{\"success\":false,\"error\":\"offline\"}"
                val failing = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(failing)
                val failed = fixture.completeNextRead(this)
                failing.await()
                assertEquals(NativeReadOutcome.FAILED, failed.outcome)
                assertFalse(required in failed.coveredRequirements)

                // Retaining/re-publishing legacy data and ending its spinner are not native completion proof.
                fixture.cache.updateCache(cached)
                fixture.cache.beginSynchronization()
                fixture.cache.finishSynchronization(true)
                assertSame(cached, fixture.cache.appState.value)
                assertNull(fixture.cache.canonicalFrames.value)
                assertEquals(2, fixture.core.statistics.calls)

                fixture.core.response.value = NativeGatedCore.localStateJson()
                val current = async(start = CoroutineStart.UNDISPATCHED) { fixture.manager.getStateModel() }
                calls.add(current)
                val fresh = fixture.completeNextRead(this)
                current.await()
                assertEquals(NativeReadOutcome.SUCCESS, fresh.outcome)
                assertTrue(required in fresh.coveredRequirements)
                assertEquals(3, fixture.core.statistics.calls)
            } finally {
                fixture.close(this, calls)
            }
        }
    }
}
