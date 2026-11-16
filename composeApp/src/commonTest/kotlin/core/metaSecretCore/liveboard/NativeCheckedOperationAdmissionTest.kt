package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeCheckedOperationAdmissionTest {
    @Test
    fun testAllCheckedEntriesDrainBeforeActualProfileResetAndRetainTheirEnteredTruth() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (route in nativeCheckedRoutes()) {
                val f = NativeCheckedOperationFixture()
                try {
                    f.initialize("checked-key-a")
                    val origin = assertNotNull(f.initialization.currentScope())
                    val profile = f.profile()
                    f.core.response = route.raw
                    val native = f.hold()
                    f.io.queue()
                    val old = f.track(async(start = CoroutineStart.UNDISPATCHED) { route.invoke(f.checked, origin) })
                    val worker = f.run(f.io.take(), this)
                    native.entered.await()
                    // Subsequent real key IO can execute inline; the controller is outside held Core.
                    f.io.resumeInline()
                    f.reset(profile)
                    assertNull(f.initialization.currentScope(), "Actual Profile Reset must close admission before the drain oracle")
                    if (f.trace.count("key.clear") != 0) failures.add("${route.name}: actual Reset cleared keys before entered checked Core exited")
                    assertTrue(!native.exited.isCompleted)
                    native.release.complete(Unit)
                    native.exited.await(); worker.join()
                    val outcome = assertIs<NativeLiveboardOutcome.Entered<*>>(old.await())
                    assertEquals(origin, outcome.scope, "Actual admitted A truth must remain stamped A, even after Reset")
                    assertEquals(route.expected(), outcome.result, "Never relabel actual entered producer truth as not-entered")
                    f.resetFinished(profile)
                    f.initialize("checked-key-b")
                    val successor = assertNotNull(f.initialization.currentScope())
                    assertNotSame(origin.owner, successor.owner)
                    val proof = f.freshProof()
                    val fresh = assertIs<NativeLiveboardOutcome.Entered<*>>(route.invoke(f.checked, successor))
                    assertEquals(successor, fresh.scope)
                    assertEquals(route.expected(), fresh.result)
                    assertSame(proof, f.cache.canonicalFrames.value, "Checked service must not replace successor canonical state")
                    assertEquals(List(2) { NativeCall(route.name, route.arguments) }, f.core.calls.value)
                    assertEquals(2, f.trace.count("core.init"))
                    reached.add("${route.name}/actual-entry/actual-Reset/retained-A/fresh-B")
                } finally { f.close() }
            }
            assertEquals(8, reached.size)
            assertEquals(emptyList(), failures, "All eight entered routes and fresh positive tails reached: $reached")
        }
    }

    @Test
    fun testQueuedCheckedOriginsCannotAdoptSuccessorAndFreshRoutesKeepExactArgumentsAndReceipts() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (route in nativeCheckedRoutes()) {
                val f = NativeCheckedOperationFixture()
                try {
                    f.initialize("checked-key-a")
                    val origin = assertNotNull(f.initialization.currentScope())
                    val profile = f.profile()
                    f.core.response = route.raw
                    f.io.queue()
                    val old = f.track(async(start = CoroutineStart.UNDISPATCHED) { route.invoke(f.checked, origin) })
                    val queued = f.io.take()
                    assertTrue(!old.isCompleted && !old.isCancelled)
                    assertTrue(f.core.calls.value.isEmpty())
                    // The taken A runnable stays held; real Reset/init B are deliberately allowed.
                    f.io.resumeInline()
                    f.reset(profile); f.resetFinished(profile)
                    f.initialize("checked-key-b")
                    val successor = assertNotNull(f.initialization.currentScope())
                    assertNotSame(origin.owner, successor.owner)
                    val proof = f.freshProof()
                    assertTrue(!old.isCompleted && !old.isCancelled)
                    f.run(queued, this).join()
                    val result = old.await()
                    if (result !is NativeLiveboardOutcome.NotEntered) failures.add("${route.name}: live old queued origin entered instead of retiring: $result")
                    else assertEquals(origin, result.scope)
                    if (f.core.calls.value.isNotEmpty()) failures.add("${route.name}: old A performed successor Core work: ${f.core.calls.value}")
                    assertSame(proof, f.cache.canonicalFrames.value)
                    assertEquals(successor, f.initialization.currentScope())
                    val beforeFresh = f.core.calls.value.size
                    val fresh = assertIs<NativeLiveboardOutcome.Entered<*>>(route.invoke(f.checked, successor))
                    assertEquals(successor, fresh.scope)
                    assertEquals(route.expected(), fresh.result)
                    assertEquals(listOf(NativeCall(route.name, route.arguments)), f.core.calls.value.drop(beforeFresh))
                    assertSame(proof, f.cache.canonicalFrames.value)
                    invalidInputsDoNotEnter(f, origin)
                    assertEquals("checked-key-b", f.keys.getString("master_key"))
                    reached.add("${route.name}/live-queued-A/actual-Reset-init-B/old-runnable/fresh-B/invalid-input")
                } finally { f.close() }
            }
            assertEquals(8, reached.size)
            assertEquals(emptyList(), failures, "All eight queued-origin and exact fresh controls reached: $reached")
        }
    }

    private suspend fun invalidInputsDoNotEnter(f: NativeCheckedOperationFixture, old: NativeReadScope) {
        val before = f.core.calls.value.toList()
        val bad = """{"requestId":"${LiveboardNativeFixtures.requestId}","accepted":"false"}"""
        for (outcome in listOf(f.checked.decideJoinRequest(old, bad), f.checked.decideRemoveMember(old, bad))) {
            val rejected = assertIs<NativeLiveboardOutcome.NotEntered>(outcome)
            assertEquals(old, rejected.scope)
            assertEquals(NativeFailure.InvalidDecisionInput, rejected.code)
        }
        val deletion = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue(kind = LiveboardKind.DeleteSecret))
        val rejected = assertIs<NativeLiveboardOutcome.NotEntered>(f.checked.continueLocalRemoveChecked(old, deletion))
        assertEquals(NativeFailure.InvalidProducerReference, rejected.code)
        assertEquals(before, f.core.calls.value, "Pure validation must precede native admission, including for retired inputs")
    }
}
