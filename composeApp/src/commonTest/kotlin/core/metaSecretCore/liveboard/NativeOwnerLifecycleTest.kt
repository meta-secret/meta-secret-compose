package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent

@OptIn(ExperimentalCoroutinesApi::class)
class NativeOwnerLifecycleTest {
    @Test
    fun testFirstRequiredReadCallbackResetCannotDeliverOldSuccessToSecondTarget() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeLifecycleTestFixture()
            try {
                fixture.initialize(this)
                val profile = fixture.primeProfile(this)
                val targets = List(2) { assertNotNull(fixture.reads.requireFreshRead()) }
                val replies = MutableStateFlow<List<Pair<NativeReadFrame, Boolean>>>(emptyList())
                val resetStarted = MutableStateFlow(false)
                val waiters = targets.map { target ->
                    launch(fixture.immediate, CoroutineStart.UNDISPATCHED) {
                        val frame = runCatching { target.completion.await() }.getOrNull() ?: return@launch
                        val afterReset = resetStarted.value
                        replies.update { it + (frame to afterReset) }
                        if (resetStarted.compareAndSet(false, true)) {
                            profile.handle(ProfileEvents.ResetAllData)
                            assertTrue(fixture.resetEntered.isCompleted, "Actual Profile callback must enter inline")
                            assertEquals(1, fixture.keys.clears.value.size, "The callback must actually clear keys before the next reply")
                        }
                    }.also { fixture.track(it) }
                }
                val read = fixture.startRead(this)
                fixture.io.resumeInline()
                fixture.io.runNext(this).also { fixture.track(it) }.join()
                read.await()

                val failures = mutableListOf<String>()
                if (!resetStarted.value || !fixture.resetEntered.isCompleted) failures.add("real completion callback did not Reset Profile")
                if (fixture.keys.clears.value.size != 1) failures.add("callback did not clear real shared keys exactly once")
                if (replies.value.none { !it.second && targets.all(it.first.coveredRequirements::contains) }) failures.add("actual read did not cover both targets")
                if (targets.any { !it.completion.isCompleted }) failures.add("Reset left a required completion pending")
                if (replies.value.size != 2) failures.add("both real completion callbacks must receive a terminal frame")
                if (replies.value.count { !it.second && it.first.outcome == NativeReadOutcome.SUCCESS } != 1) failures.add("first callback did not receive exactly one committed success")
                if (replies.value.count { it.second && it.first.outcome == NativeReadOutcome.RETIRED && it.first.state == null } != 1) failures.add("second callback did not receive payload-free retirement after Reset")
                if (replies.value.any { it.second && it.first.outcome == NativeReadOutcome.SUCCESS }) failures.add("second target received pre-Reset SUCCESS after owner retirement")
                if (fixture.cache.canonicalFrames.value != null) failures.add("Reset left canonical proof")
                waiters.forEach { it.cancel(); it.join() }
                assertEquals(emptyList(), failures)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun testProfileResetDrainsEnteredReadAndActualSocketAuthorizationBeforeClearingKeys() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeLifecycleTestFixture()
            try {
                fixture.initialize(this)
                val profile = fixture.primeProfile(this)
                val subscription = assertNotNull(fixture.client.subscription.value)
                val primeFrame = assertNotNull(fixture.cache.canonicalFrames.value)
                fixture.core.planRead()
                val read = fixture.startRead(this)
                val readWorker = fixture.io.runNext(this).also { fixture.track(it) }
                val readEntry = fixture.core.nextEntry("read")
                // Prime's already completed read is also in the entry queue; select held call.
                val heldRead = if (readEntry.held) readEntry else fixture.core.nextEntry("read")
                fixture.core.holdAuthorization = true
                val ioPump = fixture.io.drain(this).also { fixture.track(it) }
                val authorization = async(Dispatchers.IO) {
                    runCatching { subscription.authorizationTokenProvider() }
                }.also { fixture.track(it) }
                val authEntry = fixture.core.nextEntry("auth")
                assertEquals(2, fixture.core.counts.value.active, "Both real synchronous FFI calls must be entered")
                profile.handle(ProfileEvents.ResetAllData)
                assertTrue(fixture.resetEntered.isCompleted, "Actual Reset entered before inspecting pre-release effects")
                val clearedBeforeEitherRelease = fixture.keys.clears.value.isNotEmpty()

                heldRead.release.complete(Unit)
                readWorker.join()
                read.await()
                assertTrue(authEntry.returned.isCompleted.not(), "Authorization FFI must still be held")
                val clearedWhileAuthorizationHeld = fixture.keys.clears.value.isNotEmpty()
                val heldReadFrame = fixture.cache.canonicalFrames.value
                val staleFramePublished = heldReadFrame != null && heldReadFrame !== primeFrame
                authEntry.release.complete(Unit)
                // Retirement may reject the returned token; this test requires the entered FFI to drain.
                authorization.await()
                fixture.keys.clears.first { it.isNotEmpty() }
                profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
                ioPump.cancel()
                ioPump.join()

                val failures = mutableListOf<String>()
                if (clearedBeforeEitherRelease) failures.add("Reset cleared keys while read and token FFI were entered")
                if (clearedWhileAuthorizationHeld) failures.add("Reset drained only read and ignored entered authorization FFI")
                if (fixture.keys.clears.value != listOf(0)) failures.add("key cleanup ran with an active native lease or more than once")
                if (staleFramePublished) failures.add("retired entered read published after Reset began")
                if (fixture.cache.canonicalFrames.value != null) failures.add("Reset cleanup left canonical proof")
                if (fixture.core.initCalls.value != 1) failures.add("Reset reinitialized entered native owner")
                if (fixture.core.authCalls.value != 1 || fixture.core.counts.value.active != 0) failures.add("actual authorization/read did not drain")
                assertEquals(emptyList(), failures)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun testQueuedOldProfileResetCannotClearUninitializedSuccessorGeneration() = runBlocking {
        withTimeout(10_000) {
            val scheduler = TestCoroutineScheduler()
            val fixture = NativeLifecycleTestFixture(StandardTestDispatcher(scheduler))
            try {
                val old = fixture.initialization.capture()
                val profile = fixture.profile()
                profile.handle(ProfileEvents.ResetAllData)
                fixture.initialization.invalidate()
                val successor = fixture.initialization.capture()
                assertNotSame(old, successor)
                fixture.keys.saveString("master_key", "uninitialized-successor-key")
                val successorState = AppStateModel(success = true)
                fixture.cache.updateCache(successorState)
                scheduler.runCurrent()
                assertTrue(fixture.resetEntered.isCompleted, "The old actual Profile callback must execute after generation B exists")

                val failures = mutableListOf<String>()
                if (fixture.keys.clears.value.isNotEmpty()) failures.add("old uninitialized callback cleared successor keys")
                if (fixture.keys.getString("master_key") != "uninitialized-successor-key") failures.add("successor master key was lost")
                if (fixture.initialization.capture() !== successor) failures.add("old cleanup retired successor owner token")
                if (fixture.cache.appState.value !== successorState) failures.add("old cleanup erased successor presentation")
                if (profile.navigationEvent.value != ProfileNavigationEvent.Idle) failures.add("old cleanup navigated successor to registration")
                if (fixture.core.initCalls.value != 0) failures.add("uninitialized cleanup invoked native init")
                assertEquals(emptyList(), failures)
            } finally {
                fixture.close()
                scheduler.runCurrent()
            }
        }
    }

    @Test
    fun testCanonicalObserverCanResetProfileBeforeReadReturnsWithoutSelfDrain() = runBlocking {
        withTimeout(10_000) {
            val fixture = NativeLifecycleTestFixture()
            var observer: kotlinx.coroutines.Job? = null
            try {
                fixture.initialize(this)
                val profile = fixture.primeProfile(this)
                val prior = assertNotNull(fixture.cache.canonicalFrames.value).attempt
                val readReturned = MutableStateFlow(false)
                val observed = CompletableDeferred<Pair<Boolean, Int>>()
                observer = launch(fixture.immediate, CoroutineStart.UNDISPATCHED) {
                    fixture.reads.frames.collect { frame ->
                        if (frame != null && frame.attempt > prior) {
                            profile.handle(ProfileEvents.ResetAllData)
                            observed.complete(readReturned.value to fixture.keys.clears.value.size)
                        }
                    }
                }.also { fixture.track(it) }
                val read = async(fixture.immediate, CoroutineStart.UNDISPATCHED) {
                    fixture.manager.getStateModel().also { readReturned.value = true }
                }.also { fixture.track(it) }
                fixture.io.resumeInline()
                fixture.io.runNext(this).also { fixture.track(it) }.join()
                val observation = observed.await()
                read.await()
                assertTrue(fixture.resetEntered.isCompleted)
                assertEquals(false to 1, observation, "Real observer Reset must finish key cleanup before original caller returns")
                assertEquals(listOf(0), fixture.keys.clears.value, "No physical/native lease may remain across cache callbacks")
                assertEquals(ProfileNavigationEvent.NavigateToSignIn, profile.navigationEvent.value)
                assertEquals(null, fixture.cache.canonicalFrames.value)
                assertEquals(0, fixture.core.counts.value.active)
            } finally {
                observer?.cancel()
                observer?.join()
                fixture.close()
            }
        }
    }
}
