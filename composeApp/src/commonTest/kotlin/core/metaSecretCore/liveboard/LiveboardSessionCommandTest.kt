package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LiveboardSessionCommandTest {
    @Test
    fun testAdmittedProducerTruthSurvivesViewCancellationAndNeverResubmitsIntoAnotherView() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (variant in listOf("same-owner", "retired-after-ui-queue", "native-failure")) {
                lifetimeVariant(this, variant, failures, reached)
            }
            assertEquals(3, reached.size)
            assertEquals(emptyList(), failures, "All lifetime variants and fresh native B controls reached: $reached")
        }
    }

    private suspend fun lifetimeVariant(scope: CoroutineScope, variant: String, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardSessionCommandFixture()
        try {
            f.initialize("session-key-a")
            val profile = f.profile()
            val frameA = f.proof()
            val ownerA = assertNotNull(frameA.scope)
            val session = f.session(frameA)
            val intent = assertNotNull(session.removeDeviceIntent("device-b"))
            val fault = if (variant == "native-failure") IllegalStateException("Synthetic private native failure") else null
            val native = f.hold(fault)
            f.io.queue()
            val view = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { session.removeDevice(intent) })
            val worker = f.run(f.io.take(), scope)
            native.entered.await()
            f.io.resumeInline()
            var sessionB: LiveboardSession? = null
            if (variant == "retired-after-ui-queue") {
                // Queue UI only AFTER the real worker has entered Core, not its startup.
                f.ui.queue()
                native.release.complete(Unit); native.exited.await()
                val oldUi = f.ui.take()
                worker.join()
                f.reset(profile); f.resetFinished(profile)
                f.initialize("session-key-b")
                val frameB = f.proof()
                assertNotSame(ownerA.owner, assertNotNull(frameB.scope).owner)
                assertNull(f.factory.open(frameA))
                sessionB = f.session(frameB)
                assertNotSame(session, sessionB)
                view.cancel()
                f.ui.resumeInline(); oldUi.publish(); f.ui.releaseAll()
            } else {
                view.cancel()
                assertTrue(!native.exited.isCompleted)
                native.release.complete(Unit); native.exited.await(); worker.join()
            }
            view.join(); session.awaitSettled()
            assertTrue(view.isCancelled)
            val expected = if (fault == null) f.expectedProducer() else NativeResult.Uncertain
            val retained = session.retainedProducer(intent)
            if (retained == null) failures.add("$variant: actual admitted producer truth was lost with the cancelled view")
            else {
                assertEquals(ownerA, retained.scope)
                assertEquals(expected, retained.result)
                val beforeRepeat = f.core.calls.value.size
                assertEquals(retained, session.removeDevice(intent))
                assertEquals(beforeRepeat, f.core.calls.value.size, "Repeating the same issued identity must not resubmit")
            }
            assertEquals(if (fault == null) 0 else 1, f.core.thrown.value)
            assertEquals(listOf(NativeCall("remove", listOf("device-b"))), f.core.calls.value)
            if (sessionB == null) {
                val newView = f.session(f.proof())
                assertSame(session, newView, "Fresh same-key physical proof must reuse the keyed Session")
                if (newView.removeDeviceIntent("device-b") != null) failures.add("$variant: a new view can resubmit an unresolved admitted target")
                assertEquals(1, f.core.calls.value.size)
                f.reset(profile); f.resetFinished(profile)
                f.initialize("session-key-b")
                sessionB = f.session(f.proof())
            } else {
                if (session.presentation.value != null) failures.add("$variant: old completion published after actual owner B")
                assertNull(sessionB.presentation.value)
            }
            val currentB = assertNotNull(f.initialization.currentScope())
            val liveB = assertNotNull(sessionB)
            val freshIntent = assertNotNull(liveB.removeDeviceIntent("device-b"))
            val fresh = assertIs<NativeLiveboardOutcome.Entered<*>>(liveB.removeDevice(freshIntent))
            assertEquals(currentB, fresh.scope)
            assertEquals(f.expectedProducer(), fresh.result)
            assertEquals(2, f.core.calls.value.size)
            assertNotNull(liveB.retainedProducer(freshIntent))
            reached.add("$variant/actual-entry/cancelled-view/actual-settlement/fresh-B")
        } finally { f.close() }
    }

    @Test
    fun testQueuedViewCancellationOwnsNoNativeLeaseAndCannotClearANewerIntentOrSuccessor() = runBlocking {
        withTimeout(30_000) {
            val f = LiveboardSessionCommandFixture()
            val failures = mutableListOf<String>()
            try {
                f.initialize("session-key-a")
                val profile = f.profile()
                val original = f.proof()
                f.cache.updateCache(assertNotNull(original.state))
                assertNull(f.cache.canonicalFrames.value)
                assertNull(f.factory.open(original), "Cached Member data without current physical proof cannot construct a live Session")
                val frameA = f.proof()
                val session = f.session(frameA)
                assertNull(session.removeDeviceIntent("device-a"))
                assertNull(session.removeDeviceIntent("unknown-device"))
                val oldIntent = assertNotNull(session.removeDeviceIntent("device-b"))
                f.io.queue()
                val view = f.track(async(f.main, CoroutineStart.UNDISPATCHED) { session.removeDevice(oldIntent) })
                val queued = f.io.take()
                assertTrue(!view.isCompleted && !view.isCancelled)
                view.cancel(); f.io.resumeInline()
                assertTrue(view.isCancelled)
                assertTrue(f.core.calls.value.isEmpty())
                val sameView = f.session(f.proof())
                assertSame(session, sameView)
                val newerIntent = sameView.removeDeviceIntent("device-b")
                var newerTruth: NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>? = null
                if (newerIntent == null) failures.add("Cancelled queued intent still owns its target before its IO runnable executes")
                else {
                    val actual = assertIs<NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>>(sameView.removeDevice(newerIntent))
                    assertEquals(frameA.scope, actual.scope)
                    assertEquals(f.expectedProducer(), actual.result)
                    newerTruth = actual
                }
                val countBeforeReset = f.core.calls.value.size
                // Completion here proves the cancelled queued work owns no native lease.
                f.reset(profile); f.resetFinished(profile)
                f.initialize("session-key-b")
                val frameB = f.proof()
                assertNull(f.factory.open(frameA))
                val sessionB = f.session(frameB)
                f.run(queued, this).join(); view.join(); session.awaitSettled()
                assertEquals(countBeforeReset, f.core.calls.value.size)
                if (newerIntent != null) assertEquals(newerTruth, session.retainedProducer(newerIntent), "Old cancellation cleanup cannot clear the newer admitted slot")
                assertNull(sessionB.presentation.value)
                assertSame(sessionB, f.session(frameB), "Old cleanup cannot evict the factory's current B slot")
                val fresh = assertNotNull(sessionB.removeDeviceIntent("device-b"))
                val result = assertIs<NativeLiveboardOutcome.Entered<*>>(sessionB.removeDevice(fresh))
                assertEquals(frameB.scope, result.scope)
                assertEquals(f.expectedProducer(), result.result)
                assertEquals(countBeforeReset + 1, f.core.calls.value.size)
                assertEquals(emptyList(), failures, "Proof, actual queued cancellation, exact old-slot cleanup and fresh B controls reached")
            } finally { f.close() }
        }
    }

    @Test
    fun testRawShowAndBothRecoveryDecisionsDrainAndKeepTheirFrozenOpaqueClaim() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (route in sessionRawRoutes()) for (queued in listOf(false, true)) {
                rawVariant(this, route, queued, failures, reached)
            }
            assertEquals(6, reached.size)
            assertEquals(emptyList(), failures, "All raw3 held and live queued origins plus fresh exact-Claim controls reached: $reached")
        }
    }

    private suspend fun rawVariant(scope: CoroutineScope, route: SessionRawRoute, queued: Boolean, failures: MutableList<String>, reached: MutableList<String>) {
        val f = LiveboardSessionCommandFixture()
        try {
            f.initialize("session-key-a")
            val profile = f.profile()
            val frameA = f.proof()
            val ownerA = assertNotNull(frameA.scope)
            val session = f.session(frameA)
            val probe = assertNotNull(session.removeDeviceIntent("device-b"))
            val claim = LiveboardNativeFixtures.claimId
            val native = if (!queued) f.hold() else null
            f.io.queue()
            val old = f.track(scope.async(f.main, CoroutineStart.UNDISPATCHED) { route.invoke(f.checked, ownerA, claim) })
            val task = f.io.take()
            if (native != null) {
                val worker = f.run(task, scope)
                native.entered.await(); f.io.resumeInline()
                f.reset(profile)
                assertNull(f.initialization.currentScope())
                if (f.trace.count("key.clear") != 0) failures.add("${route.name}: Reset cleared keys before raw Core exit")
                assertTrue(!native.exited.isCompleted)
                native.release.complete(Unit); native.exited.await(); worker.join()
                val actual = assertIs<NativeInvocation.Value<*>>(old.await())
                assertEquals(ownerA, actual.scope)
                assertEquals(f.core.rawValue(route.name, claim), actual.value)
                f.resetFinished(profile)
            } else {
                assertTrue(!old.isCompleted && !old.isCancelled)
                f.io.resumeInline(); f.reset(profile); f.resetFinished(profile)
            }
            f.initialize("session-key-b")
            val frameB = f.proof()
            val ownerB = assertNotNull(frameB.scope)
            assertNotSame(ownerA.owner, ownerB.owner)
            val current = f.session(frameB)
            if (queued) {
                f.run(task, scope).join()
                if (old.await() !is NativeInvocation.Retired) failures.add("${route.name}: live queued A adopted actual B")
                if (f.core.calls.value.isNotEmpty()) failures.add("${route.name}: queued A entered successor raw Core")
            }
            val count = f.core.calls.value.size
            val fresh = assertIs<NativeInvocation.Value<*>>(route.invoke(f.checked, ownerB, claim))
            assertEquals(ownerB, fresh.scope)
            assertEquals(f.core.rawValue(route.name, claim), fresh.value)
            assertEquals(listOf(NativeCall(route.name, listOf(claim))), f.core.calls.value.drop(count))
            assertTrue(f.core.forbidden.value.isEmpty())
            assertNull(session.retainedProducer(probe), "Transient raw data cannot become a producer journal entry")
            assertNull(current.presentation.value)
            reached.add("${route.name}/${if (queued) "live-queued" else "entered"}/actual-Reset-init-B/fresh-Claim")
        } finally { f.close() }
    }
}
