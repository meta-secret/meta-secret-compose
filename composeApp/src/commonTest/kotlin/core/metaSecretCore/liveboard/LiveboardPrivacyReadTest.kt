package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent

class LiveboardPrivacyReadTest {
    @Test
    fun testRealErrorBackgroundAndKeyEntriesConcealBeforeObserversWithoutDroppingPrivateTruth() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            val failures = mutableListOf<String>()
            var current: LiveboardSession? = null
            val beforeDisplay = mutableListOf<Boolean>()
            try {
                f.observeErrors(this) { beforeDisplay.add(f.disclosuresEmpty(assertNotNull(current))) }
                val session = f.start(); current = session
                val intent = assertNotNull(session.removeDeviceIntent("device-b"))
                val original = assertIs<NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>>(session.removeDevice(intent))
                for (iteration in 0..1) {
                    f.disclose(session)
                    session.sensitive.editDraft("Draft $iteration", "private fixture draft $iteration")
                    f.notifications.showError("Repeated fixture error")
                    if (!f.disclosuresEmpty(session)) failures.add("error$iteration: disclosed state survived the explicit synchronous entry")
                    if (session.sensitive.state.value.draft != LiveboardDraft("Draft $iteration", "private fixture draft $iteration")) failures.add("error$iteration: authored draft was lost instead of remaining retryable")
                    if (iteration == 0) f.seenError(session, "Repeated fixture error")
                    else if (session.inbox.state.value.error != "Repeated fixture error") failures.add("Equal error entry was lost through notification conflation")
                    assertEquals(original, session.retainedProducer(intent))
                    f.proof()
                    session.inbox.state.first { it.ready }
                    if (session.inbox.state.value.error != "Repeated fixture error") failures.add("Manual error disappeared after ordinary proof")
                    if (!f.disclosuresEmpty(session)) failures.add("error$iteration: a normal proof restored a disclosed value")
                    if (iteration == 0) {
                        // Keep the real coordinator's display unchanged for the equal-error report.
                        session.inbox.dismissError(); f.proof(); f.settleSocket()
                    }
                }
                assertTrue(beforeDisplay.isNotEmpty())
                if (beforeDisplay.any { !it }) failures.add("A real notification observer saw content before privacy")
                session.inbox.dismissError(); f.notifications.dismiss(); f.proof(); f.settleSocket()
                f.disclose(session); session.sensitive.editDraft("Internal draft", "internal fixture value")
                f.core.failRead = true
                try {
                    val failure = assertFailsWith<IllegalStateException> { f.manager.getStateModel() }
                    assertEquals("Synthetic unreadable feature state", failure.message)
                } finally { f.core.failRead = false }
                if (!f.disclosuresEmpty(session)) failures.add("Actual failed physical read/error publication retained a disclosed value")
                if (session.sensitive.state.value.draft != LiveboardDraft("Internal draft", "internal fixture value")) failures.add("Ordinary synchronization failure erased the authored draft")
                assertTrue(f.reads.frames.value?.outcome != NativeReadOutcome.SUCCESS)
                assertEquals(original, session.retainedProducer(intent))
                f.proof()
                session.inbox.dismissError(); f.proof(); f.settleSocket()
                f.disclose(session); session.sensitive.editDraft("Background draft", "background fixture value")
                f.socket.onAppBackground()
                if (!f.privateEmpty(session)) failures.add("Actual Socket background returned before privacy")
                assertEquals(original, session.retainedProducer(intent))
                f.socket.onAppForeground(); f.proof(); f.settleSocket()
                if (!f.privateEmpty(session)) failures.add("Foreground restored content without deliberate disclosure")
                val profile = f.profile()
                f.disclose(session); session.sensitive.editDraft("Reset draft", "reset fixture value")
                var keyEntryPrivate: Boolean? = null
                f.track(launch(f.main, CoroutineStart.UNDISPATCHED) {
                    f.trace.calls.collect { calls ->
                        if (calls.lastOrNull()?.name == "key.clear") keyEntryPrivate = f.privateEmpty(session)
                    }
                })
                val successor = f.successor(profile); current = successor
                if (keyEntryPrivate != true || !f.privateEmpty(session)) failures.add("Key clear/native-owner transition preceded privacy")
                assertEquals(original, session.retainedProducer(intent))
                f.disclose(successor); successor.sensitive.editDraft("Current B", "current B fixture value")
                session.dispose()
                assertTrue(successor.sensitive.state.value.revealed.isNotEmpty())
                assertNotNull(successor.sensitive.state.value.draft)
                assertEquals(1, f.core.calls.value.count { it.name == "remove" })
                assertEquals(emptyList(), failures, "Equal/internal error, physical failure, background, key boundary and fresh B controls reached")
            } finally { f.close() }
        }
    }

    @Test
    fun testManualErrorRequiresItsOwnPhysicalProofAndCannotReadOrTicketASuccessor() = runBlocking {
        withTimeout(45_000) {
            val f = LiveboardFeatureFixture()
            val failures = mutableListOf<String>()
            try {
                val session = f.start()
                val initial = assertNotNull(f.reads.frames.value)
                val intent = assertNotNull(session.removeDeviceIntent("device-b"))
                val original = assertIs<NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>>(session.removeDevice(intent))
                val order = session.inbox.state.value.queue.map { it.identity }
                val oldNative = f.hold("read")
                f.io.queue()
                val old = f.track(async(f.main, CoroutineStart.UNDISPATCHED) { f.manager.getStateModel() })
                val oldWorker = f.run(f.io.take(), this)
                oldNative.entered.await(); f.io.resumeInline()
                f.notifications.showError("Manual fixture error")
                f.seenError(session, "Manual fixture error")
                if (session.inbox.state.value.ready) failures.add("Error accepted the already-entered older read")
                val freshNative = f.hold("read")
                session.inbox.dismissError()
                f.io.queue()
                oldNative.release.complete(Unit); oldNative.exited.await()
                val freshTask = f.io.take()
                old.await(); oldWorker.join()
                if (session.inbox.state.value.ready) failures.add("Old read certified the newer manual target")
                f.cache.updateCache(assertNotNull(initial.state))
                if (session.inbox.state.value.ready) failures.add("Cache-only data certified the required target")
                f.io.resumeInline()
                val freshWorker = f.run(freshTask, this)
                freshNative.entered.await()
                if (session.inbox.state.value.ready) failures.add("Controls enabled before current physical read completed")
                freshNative.release.complete(Unit); freshNative.exited.await(); freshWorker.join(); f.settleSocket()
                session.inbox.state.first { it.ready }
                assertEquals(order, session.inbox.state.value.queue.map { it.identity })
                assertEquals(original, session.retainedProducer(intent))
                assertTrue(f.privateEmpty(session))
                f.notifications.showError("Pinned fixture error"); f.seenError(session, "Pinned fixture error")
                f.proof(); session.inbox.state.first { it.ready }
                f.notifications.dismiss()
                assertEquals("Pinned fixture error", session.inbox.state.value.error, "Legacy display dismissal cannot clear manual04")
                val failing = f.hold("read")
                f.io.queue(); session.inbox.dismissError()
                val failureWorker = f.run(f.io.take(), this)
                failing.entered.await(); f.io.resumeInline(); f.core.failRead = true
                failing.release.complete(Unit); failing.exited.await(); failureWorker.join(); f.settleSocket()
                f.core.failRead = false
                if (session.inbox.state.value.ready) failures.add("Failed required read certified readiness")
                f.cache.updateCache(assertNotNull(initial.state))
                if (session.inbox.state.value.ready) failures.add("Cached success repaired a failed required read")
                val pinned = session.inbox.state.value.error
                f.proof(); session.inbox.state.first { it.ready }
                assertEquals(pinned, session.inbox.state.value.error)
                f.socket.onAppBackground()
                f.notifications.showError("Hidden fixture error"); f.seenError(session, "Hidden fixture error")
                val hiddenReads = f.core.reads.value
                session.inbox.dismissError(); f.socket.pauseRefreshesAndAwait()
                assertEquals(hiddenReads, f.core.reads.value, "Acknowledged hidden tap must not enter native")
                f.socket.resumeRefreshes()
                f.io.queue(); f.socket.onAppForeground()
                val resumed = f.io.take()
                f.io.resumeInline(); f.run(resumed, this).join(); f.settleSocket()
                session.inbox.state.first { it.ready }
                assertEquals(order, session.inbox.state.value.queue.map { it.identity })
                assertEquals(original, session.retainedProducer(intent))
                assertEquals(1, f.core.calls.value.count { it.name == "remove" })
                val profile = f.profile()
                f.notifications.showError("Observer fixture error"); f.seenError(session, "Observer fixture error")
                var armed = false
                var successor: LiveboardSession? = null
                var readsAtB = -1
                val observer = f.track(launch(f.main, CoroutineStart.UNDISPATCHED) {
                    session.inbox.state.collect { state ->
                        if (armed && state.error == null) {
                            armed = false
                            successor = f.successor(profile)
                            readsAtB = f.core.reads.value
                        }
                    }
                })
                armed = true; session.inbox.dismissError()
                profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
                val liveB = assertNotNull(successor)
                f.socket.pauseRefreshesAndAwait()
                if (f.core.reads.value != readsAtB) failures.add("Old manual tap entered native under successor B")
                val onlyB = assertNotNull(f.reads.requireFreshRead())
                assertNotNull(f.manager.getStateModel())
                val proofB = assertNotNull(f.reads.frames.value)
                if (proofB.coveredRequirements != setOf(onlyB)) failures.add("Old manual tap inserted an unrelated B requirement")
                assertSameScope(onlyB, proofB)
                observer.cancel(); observer.join()
                f.notifications.dismiss(); liveB.inbox.dismissError(); f.proof()
                val fresh = assertNotNull(liveB.removeDeviceIntent("device-b"))
                assertIs<NativeLiveboardOutcome.Entered<*>>(liveB.actions.remove(fresh))
                assertEquals(order, liveB.inbox.state.value.queue.map { it.identity })
                assertEquals(emptyList(), failures, "Actual older/fresh/failure/hidden/resume/reentrant Reset and B controls reached")
            } finally { f.close() }
        }
    }

    private suspend fun assertSameScope(requirement: NativeReadRequirement, frame: NativeReadFrame) {
        assertTrue(requirement.completion.isCompleted)
        assertEquals(frame, requirement.completion.await())
        assertEquals(frame.scope, requirement.scope)
    }
}
