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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class NativeRegistrationFirstResumeTest {
    @Test
    fun testFirstResumeAcceptsOnlyItsExactSuccessfulStartupInitializationLineage() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (mode in listOf("ordinary", "same-key")) {
                val f = NativeRegistrationTestFixture() // The real session is constructed in uninitialized A.
                try {
                    val origin = f.initialization.capture()
                    f.initialize(f.core.member()) // Actual saved-key initialization creates B.
                    val initialized = assertNotNull(f.initialization.currentScope())
                    assertNotSame(origin, initialized.owner)
                    if (mode == "same-key") assertIs<InitResult.Success>(f.manager.initWithSavedKey())
                    assertEquals(1, f.trace.count("core.init"))
                    val result = f.session.resume()
                    if (result.status != RegistrationStatus.Ready) failures.add("$mode: ordinary startup first resume lost its own successful initialization")
                    val after = assertNotNull(f.initialization.currentScope())
                    assertSame(initialized.owner, after.owner)
                    assertSame(initialized.epoch, after.epoch)
                    assertEquals("stored-key-a", f.keys.getString("master_key"))
                    assertFreshCurrentSessionReady(f)
                    assertEquals(1, f.trace.count("core.init"), "Both legitimate resumes reuse the actual saved-key native epoch")
                    reached.add("$mode/A-created/actual-init-B/first-resume/fresh-current")
                } finally { f.close() }
            }
            for (mode in listOf("reset", "changed-key", "failed-native-init", "missing-key-init")) {
                val f = NativeRegistrationTestFixture()
                try {
                    val origin = f.initialization.capture()
                    f.initialize(f.core.member())
                    val first = assertNotNull(f.initialization.currentScope())
                    when (mode) {
                        "reset" -> {
                            val profile = f.profile()
                            f.reset(profile)
                            f.awaitReset(profile)
                            f.successor()
                        }
                        "changed-key" -> f.initialize(f.core.member(), "changed-key-c")
                        "failed-native-init" -> {
                            f.keys.saveString("master_key", "broken-key-c")
                            val native = f.hold("init")
                            val failed = f.track(async(Dispatchers.IO) { f.manager.initWithSavedKey() })
                            native.entered.await()
                            native.release.completeExceptionally(IllegalStateException("Synthetic entered native init failure"))
                            assertIs<InitResult.Error>(failed.await())
                            assertTrue(native.exited.isCompleted)
                            assertEquals(null, f.initialization.currentScope())
                            f.successor()
                        }
                        "missing-key-init" -> {
                            f.keys.removeKey("master_key")
                            assertIs<InitResult.Error>(f.manager.initWithSavedKey())
                            assertEquals(null, f.initialization.currentScope())
                            f.successor()
                        }
                    }
                    val current = assertNotNull(f.initialization.currentScope())
                    assertNotSame(origin, current.owner)
                    assertNotSame(first.owner, current.owner)
                    val key = f.keys.getString("master_key")
                    val before = f.trace.calls.value.size
                    val result = f.session.resume()
                    assertTrue(result.status != RegistrationStatus.Ready, "$mode must never let an old A adopt an unrelated successor")
                    assertEquals(emptyList(), f.trace.calls.value.drop(before), "Rejected old resume must perform no native/key/resume work")
                    val after = assertNotNull(f.initialization.currentScope())
                    assertSame(current.owner, after.owner)
                    assertSame(current.epoch, after.epoch)
                    assertEquals(key, f.keys.getString("master_key"))
                    assertFreshCurrentSessionReady(f)
                    reached.add("$mode/invalidation/old-A-rejected/fresh-current")
                } finally { f.close() }
            }
            assertEquals(6, reached.size)
            assertEquals(emptyList(), failures, "All exact startup and invalidation/live controls reached: $reached")
        }
    }

    private suspend fun assertFreshCurrentSessionReady(f: NativeRegistrationTestFixture) {
        val fresh = CoreRegistrationSession(
            RegistrationCorePorts(f.core, f.manager, f.resolver, f.socket),
            RegistrationStoragePorts(f.keys, f.storage, f.cache), f.registrationLifecycle,
        )
        try {
            assertEquals(RegistrationStatus.Ready, fresh.resume().status)
            assertEquals("Fixture Vault", f.core.user.vaultName)
        } finally { fresh.stop() }
    }
}
