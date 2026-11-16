package core.metaSecretCore.liveboard

import core.metaSecretCore.InitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.appInternalModels.SecretModel

class ManagerNativeAdmissionTest {
    @Test
    fun testAllEnteredManagerNativeCallsDrainBeforeActualProfileResetClearsKeys() = runBlocking {
        withTimeout(20_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (action in nativeBusinessActions()) {
                val f = NativeBusinessAdmissionFixture()
                try {
                    f.initialize()
                    val original = assertNotNull(f.initialization.currentScope())
                    val profile = f.profile()
                    val gate = f.hold(action.name)
                    val operation = f.track(async(Dispatchers.IO) { action.invoke(f.manager) })
                    gate.entered.await()
                    assertEquals(listOf(NativeCall(action.name, action.arguments)), f.core.calls.value)
                    f.requestReset(profile)
                    assertTrue(f.resetEntered.isCompleted, "Real Profile handle must reach Reset")
                    assertNull(f.initialization.currentScope(), "Real transition must close initialized admission")
                    assertTrue(!gate.exited.isCompleted, "The actual native call is still entered")
                    // Fixture key IO is immediate: an admitted clear cannot remain hidden in a queue.
                    if (f.keys.clears.value != 0) failures.add("${action.name}: keys cleared while actual native call was entered")
                    gate.release.complete(Unit)
                    gate.exited.await()
                    val outcome = operation.await()
                    f.awaitReset(profile)
                    if (action.name != "completion" && outcome != null) {
                        failures.add("${action.name}: retired native result escaped after actual Reset")
                    }
                    val fresh = f.freshSuccessor()
                    assertNotSame(original.owner, fresh.scope?.owner)
                    assertEquals(2, f.core.inits.value)
                    assertEquals(listOf(NativeCall(action.name, action.arguments)), f.core.calls.value)
                    assertTrue(f.notifications.errorMessages.isEmpty())
                    reached.add(action.name)
                } finally { f.close() }
            }
            assertEquals(nativeBusinessActions().map { it.name }, reached)
            assertEquals(emptyList(), failures, "All actual native exits, Reset completions and successor proofs reached: $reached")
        }
    }

    @Test
    fun testManagerCallsDuringActualKeyTransitionCannotEnterNativeAndHaveFreshLiveControls() = runBlocking {
        withTimeout(20_000) {
            val f = NativeBusinessAdmissionFixture()
            try {
                f.initialize()
                val original = assertNotNull(f.initialization.currentScope())
                val gate = f.gate()
                f.keys.saveString("master_key", "changed-key")
                f.keys.readGate = gate
                val transition = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) { f.manager.initWithSavedKey() })
                gate.entered.await()
                assertNull(f.initialization.currentScope())
                val failures = mutableListOf<String>()
                val reached = mutableListOf<String>()
                for (action in nativeBusinessActions()) {
                    val before = f.core.calls.value.size
                    val operation = f.track(async(Dispatchers.IO) { action.invoke(f.manager) })
                    operation.await() // Real caller completion, never an absence timeout.
                    if (f.core.calls.value.size != before) failures.add("${action.name}: entered Core during an actual key transition")
                    reached.add(action.name)
                }
                assertTrue(!gate.exited.isCompleted)
                gate.release.complete(Unit)
                gate.exited.await()
                assertIs<InitResult.Success>(transition.await())
                val successor = assertNotNull(f.initialization.currentScope())
                assertNotSame(original.owner, successor.owner)
                val beforeLive = f.core.calls.value.size
                for (action in nativeBusinessActions()) {
                    val before = f.core.calls.value.size
                    val result = action.invoke(f.manager)
                    if (action.name != "completion") assertNotNull(result, "${action.name}: current-owner native positive")
                    assertEquals(before + 1, f.core.calls.value.size)
                    assertEquals(NativeCall(action.name, action.arguments), f.core.calls.value.last())
                }
                assertEquals(beforeLive + 14, f.core.calls.value.size)
                val target = assertNotNull(f.reads.requireFreshRead())
                assertNotNull(f.manager.getStateModel())
                val frame = assertNotNull(f.cache.canonicalFrames.value)
                assertEquals(successor, frame.scope)
                assertTrue(target in frame.coveredRequirements)
                assertTrue(target.completion.isCompleted)
                assertSame(frame, target.completion.await())
                assertEquals(nativeBusinessActions().map { it.name }, reached)
                assertEquals(emptyList(), failures, "All transition-rejected callers and fourteen actual live controls reached: $reached")
            } finally { f.close() }
        }
    }

    @Test
    fun testDelayedManagerErrorAndPlaintextCannotEscapeAfterActualResetAndFreshSuccessor() = runBlocking {
        withTimeout(20_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (stage in listOf("error", "plaintext")) {
                val f = NativeBusinessAdmissionFixture(io = Dispatchers.IO)
                try {
                    f.initialize()
                    val original = assertNotNull(f.initialization.currentScope())
                    val profile = f.profile()
                    val port = if (stage == "error") "split" else "showLocal"
                    if (stage == "error") f.core.responseOverrides = mapOf(port to "not-json")
                    val gate = f.hold(port)
                    val operation = f.track(async(f.caller, CoroutineStart.UNDISPATCHED) {
                        if (stage == "error") f.manager.splitSecret(SecretModel("secret-a", "fixture-value"))
                        else f.manager.showRecovered(SecretModel("secret-a", null))
                    })
                    gate.entered.await()
                    gate.release.complete(Unit)
                    gate.exited.await()
                    // This is the real caller restoration task after native return, not an exit-counter guess.
                    val oldRestoration = f.caller.take()
                    assertTrue(!operation.isCompleted)
                    f.requestReset(profile)
                    assertTrue(f.resetEntered.isCompleted)
                    f.awaitReset(profile)
                    val fresh = f.freshSuccessor()
                    assertNotSame(original.owner, fresh.scope?.owner)
                    val freshState = f.cache.appState.value
                    assertTrue(f.notifications.errorMessages.isEmpty())
                    oldRestoration.publish()
                    f.track(f.caller.drain(this))
                    if (operation.await() != null) failures.add("$stage: old native result escaped into the successor")
                    if (f.notifications.errorMessages.isNotEmpty()) failures.add("$stage: stale parse error notified the successor")
                    if (f.cache.canonicalFrames.value !== fresh || f.cache.appState.value !== freshState) {
                        failures.add("$stage: old continuation changed fresh successor state/proof")
                    }
                    assertEquals(1, f.core.calls.value.size)
                    assertEquals(port, f.core.calls.value.single().name)
                    assertEquals(2, f.core.inits.value)
                    reached.add(stage)
                } finally { f.close() }
            }
            assertEquals(listOf("error", "plaintext"), reached)
            assertEquals(emptyList(), failures, "Both real queued old-caller restorations and fresh successor proofs reached: $reached")
        }
    }
}
