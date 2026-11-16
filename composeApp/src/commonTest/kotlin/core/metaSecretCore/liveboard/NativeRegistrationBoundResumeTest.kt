package core.metaSecretCore.liveboard

import core.metaSecretCore.InitResult
import core.registration.CoreRegistrationSession
import core.registration.RegistrationCorePorts
import core.registration.RegistrationStatus
import core.registration.RegistrationStoragePorts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeRegistrationBoundResumeTest {
    @Test
    fun testFailedOwnPrepareCannotClaimAnExternalInitializationAsItsStartup() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (mode in listOf("same-saved-key", "replaced-saved-key")) {
                val f = NativeRegistrationTestFixture()
                try {
                    val constructorOwner = f.initialization.capture()
                    val native = f.hold("init")
                    val prepare = f.track(async(Dispatchers.IO) { f.session.prepare("Fixture Vault") })
                    native.entered.await()
                    assertEquals("generated-device-key", f.keys.getString("master_key"))
                    native.release.completeExceptionally(IllegalStateException("Synthetic entered prepare init failure"))
                    assertEquals(RegistrationStatus.Failed, prepare.await().status)
                    assertTrue(native.exited.isCompleted)
                    f.pause()
                    val boundOwner = f.initialization.capture()
                    assertNotSame(constructorOwner, boundOwner, "The failed prepare returned its own retired/uninitialized C receipt")
                    assertNull(f.initialization.currentScope())
                    assertEquals(1, f.trace.count("core.generate"))
                    assertEquals(1, f.trace.count("core.init"))
                    assertEquals(0, f.trace.count("core.creds"))
                    val key = if (mode == "same-saved-key") "generated-device-key" else "external-replacement-key"
                    if (mode == "replaced-saved-key") f.keys.saveString("master_key", key)
                    f.core.state.value = f.core.member()
                    assertIs<InitResult.Success>(f.manager.initWithSavedKey())
                    val current = assertNotNull(f.initialization.currentScope())
                    assertNotSame(boundOwner, current.owner, "Actual external saved-key initialization created D")
                    assertEquals(2, f.trace.count("core.init"))
                    val target = assertNotNull(f.reads.requireFreshRead())
                    val value = assertNotNull(f.manager.getStateModel())
                    val frame = assertNotNull(f.cache.canonicalFrames.value)
                    assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
                    assertSame(value, frame.state)
                    assertSame(value, f.cache.appState.value)
                    assertTrue(target in frame.coveredRequirements)
                    assertTrue(target.completion.isCompleted)
                    assertSame(frame, target.completion.await())

                    val before = f.trace.calls.value.size
                    val resumed = f.session.resume()
                    if (resumed.status == RegistrationStatus.Ready) failures.add("$mode: a bound failed attempt adopted unrelated external D")
                    val tail = f.trace.calls.value.drop(before)
                    if (tail.isNotEmpty()) failures.add("$mode: bound old resume performed D native/key/socket work: $tail")
                    val after = assertNotNull(f.initialization.currentScope())
                    assertSame(current.owner, after.owner)
                    assertSame(current.epoch, after.epoch)
                    assertSame(frame, f.cache.canonicalFrames.value)
                    assertEquals(key, f.keys.getString("master_key"))
                    val fresh = CoreRegistrationSession(
                        RegistrationCorePorts(f.core, f.manager, f.resolver, f.socket),
                        RegistrationStoragePorts(f.keys, f.storage, f.cache), f.registrationLifecycle,
                    )
                    try { assertEquals(RegistrationStatus.Ready, fresh.resume().status) }
                    finally { fresh.stop() }
                    assertEquals(2, f.trace.count("core.init"), "Fresh current D reuses its actual native epoch")
                    reached.add("$mode/entered-failed-prepare-C/external-init-D/old-resume/fresh-current")
                } finally { f.close() }
            }
            assertEquals(2, reached.size)
            assertEquals(emptyList(), failures, "Both bound-attempt and fresh-current controls reached: $reached")
        }
    }
}
