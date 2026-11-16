package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class NativeRequiredRefreshCompletionTest {
    @Test
    fun testRequiredCompletionSurvivesCancelledAwaiterAndConflatedOrdinaryRead() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativePublicationTestFixture()
            val scheduler = TestCoroutineScheduler()
            val observedFrames = mutableListOf<NativeReadFrame?>()
            val collector = launch(StandardTestDispatcher(scheduler)) {
                fixture.reads.frames.collect { observedFrames.add(it) }
            }
            scheduler.runCurrent()
            try {
                fixture.initialize(this)
                val required = assertNotNull(fixture.reads.requireFreshRead())
                val completion = required.completion
                var viewCallbacks = 0
                val cancelledView = launch(start = CoroutineStart.UNDISPATCHED) {
                    completion.await()
                    viewCallbacks += 1
                }
                cancelledView.cancel()
                cancelledView.join()
                val independentOfView = !completion.isCancelled

                fixture.core.response.value = NativePublicationTestFixture.localJson("Covered read")
                fixture.socket.refreshAppState()
                val coveredPublication = fixture.completePhysicalAndHoldPublication(this)
                val completedBeforePublication = completion.isCompleted
                coveredPublication.publish()
                fixture.awaitSocketIdle()
                val covered = assertNotNull(fixture.cache.canonicalFrames.value)
                assertEquals(NativeReadOutcome.SUCCESS, covered.outcome)
                assertTrue(required in covered.coveredRequirements)
                val completedAfterCoveredCommit = completion.isCompleted

                fixture.core.response.value = NativePublicationTestFixture.localJson("Later ordinary read")
                fixture.socket.refreshAppState()
                fixture.completePhysicalAndHoldPublication(this).publish()
                fixture.awaitSocketIdle()
                val current = assertNotNull(fixture.cache.canonicalFrames.value)
                assertTrue(current.attempt > covered.attempt)
                assertTrue(current.coveredRequirements.isEmpty())
                scheduler.runCurrent()

                // The compiling starter stays uncompleted: report semantic failure, never await a deadline.
                val reply = if (completion.isCompleted && !completion.isCancelled) {
                    runCatching { completion.await() }.getOrNull()
                } else null
                val failures = mutableListOf<String>()
                if (!independentOfView) failures.add("view cancellation cancelled owner completion")
                if (completedBeforePublication) failures.add("completion preceded canonical commit")
                if (!completedAfterCoveredCommit) failures.add("matching canonical success did not complete requirement")
                if (required.completion !== completion) failures.add("completion identity changed")
                if (reply !== covered) failures.add("late waiter lost exact committed frame")
                if (reply?.scope != required.scope || reply?.scope != current.scope) failures.add("completion scope mismatched")
                if (required !in reply?.coveredRequirements.orEmpty()) failures.add("completion lost matching requirement")
                if (observedFrames.mapNotNull { it?.attempt } != listOf(current.attempt)) failures.add("test did not conflate covered frame")
                if (fixture.cache.canonicalFrames.value !== current) failures.add("late await changed current canonical state")
                if (viewCallbacks != 0) failures.add("cancelled view received a callback")
                if (fixture.core.statistics.calls != 2 || fixture.core.statistics.peak != 1) failures.add("unexpected physical read execution")
                assertEquals(emptyList(), failures)
            } finally {
                withContext(NonCancellable) {
                    collector.cancel()
                    scheduler.runCurrent()
                    collector.join()
                    fixture.close()
                }
            }
        }
    }
}
