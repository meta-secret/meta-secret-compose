package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class NativeReadPublicationTest {
    @Test
    fun testOlderSuccessfulManagerPublicationCannotReplaceNewerSocketSnapshot() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativePublicationTestFixture()
            val publications = mutableListOf<Long>()
            val observer = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                fixture.reads.frames.collect { if (it != null) publications.add(it.attempt) }
            }
            try {
                fixture.initialize(this)
                fixture.core.response.value = NativePublicationTestFixture.localJson("Older device name")
                val olderCall = fixture.startManagerRead(this)
                val olderPublication = fixture.completePhysicalAndHoldPublication(this)

                fixture.core.response.value = NativePublicationTestFixture.localJson("Newer device name")
                val before = fixture.io.dispatchCount
                fixture.socket.refreshAppState()
                assertEquals(before + 1, fixture.io.dispatchCount, "The physical fence must be released before UI publication")
                val newerPublication = fixture.completePhysicalAndHoldPublication(this)
                newerPublication.publish()
                fixture.awaitSocketIdle(expectedOtherRefreshes = 1)
                val current = assertNotNull(fixture.cache.canonicalFrames.value)
                assertEquals(NativeReadOutcome.SUCCESS, current.outcome)
                val currentState = fixture.cache.appState.value

                olderPublication.publish()
                olderCall.await()
                val failures = mutableListOf<String>()
                if (fixture.cache.canonicalFrames.value !== current) failures.add("old success replaced newer canonical frame")
                if (fixture.cache.appState.value !== currentState) failures.add("old success replaced newer legacy snapshot")
                if (publications != listOf(current.attempt)) failures.add("old success was emitted after newer success")
                if (fixture.core.statistics.calls != 2) failures.add("unexpected native read count")
                assertEquals(emptyList(), failures)
            } finally {
                withContext(NonCancellable) {
                    observer.cancel()
                    observer.join()
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun testOlderFailedManagerPublicationCannotWipeNewerRequiredReadProof() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativePublicationTestFixture()
            try {
                fixture.initialize(this)
                fixture.core.response.value = "{\"success\":false,\"error\":\"old read unavailable\"}"
                val olderCall = fixture.startManagerRead(this)
                val olderPublication = fixture.completePhysicalAndHoldPublication(this)
                val required = assertNotNull(fixture.reads.requireFreshRead())

                fixture.core.response.value = NativePublicationTestFixture.localJson("Newer device name")
                val before = fixture.io.dispatchCount
                fixture.socket.refreshAppState()
                assertEquals(before + 1, fixture.io.dispatchCount)
                val newerPublication = fixture.completePhysicalAndHoldPublication(this)
                newerPublication.publish()
                fixture.awaitSocketIdle(expectedOtherRefreshes = 1)
                val current = assertNotNull(fixture.cache.canonicalFrames.value)
                assertEquals(NativeReadOutcome.SUCCESS, current.outcome)
                assertTrue(required in current.coveredRequirements)
                val currentState = fixture.cache.appState.value

                olderPublication.publish()
                olderCall.await()
                val failures = mutableListOf<String>()
                if (fixture.cache.canonicalFrames.value !== current) failures.add("old failure wiped newer canonical proof")
                if (fixture.cache.appState.value !== currentState) failures.add("old failure replaced newer successful data")
                if (required !in fixture.cache.canonicalFrames.value?.coveredRequirements.orEmpty()) failures.add("new requirement lost its proof")
                if (fixture.core.statistics.calls != 2) failures.add("unexpected native read count")
                assertEquals(emptyList(), failures)
            } finally {
                fixture.close()
            }
        }
    }
}
