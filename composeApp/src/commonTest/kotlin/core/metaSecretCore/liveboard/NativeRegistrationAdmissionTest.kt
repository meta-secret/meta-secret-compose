package core.metaSecretCore.liveboard

import core.metaSecretCore.AppStateResult
import core.metaSecretCore.MemberState
import core.metaSecretCore.PrepareSignUpResult
import core.metaSecretCore.VaultAvailability
import core.registration.RegistrationStatus
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

class NativeRegistrationAdmissionTest {
    @Test
    fun testKeylessBootstrapDrainsBeforeResetAndOldQueuedPrepareCannotTouchSuccessor() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            val held = NativeRegistrationTestFixture()
            try {
                val native = held.hold("generate")
                // The actual blocking fake FFI always has its own worker, including after IO injection.
                val prepare = held.track(async(Dispatchers.IO) { held.session.prepare("Fixture Vault") })
                native.entered.await()
                val currentReservationOwner = held.initialization.capture()
                val profile = held.profile()
                assertSame(currentReservationOwner, held.initialization.capture())
                val before = held.trace.count("key.clear")
                held.reset(profile)
                if (held.trace.count("key.clear") != before) failures.add("Reset cleared keys while real key generation remained entered")
                assertTrue(!native.exited.isCompleted)
                native.release.complete(Unit)
                prepare.await()
                held.awaitReset(profile)
                val fresh = held.successor()
                assertNotNull(fresh.scope)
                assertEquals("stored-key-b", held.keys.getString("master_key"))
                reached.add("entered-generate/reset/fresh-successor")
            } finally { held.close() }

            val queued = NativeRegistrationTestFixture()
            try {
                val origin = queued.initialization.capture() // A exists even before a native init.
                val profile = queued.profile()
                val old = queued.track(async(queued.caller) { queued.session.prepare("Old Vault") })
                val callback = queued.caller.take()
                assertTrue(!old.isCompleted)
                queued.reset(profile)
                queued.awaitReset(profile)
                val fresh = queued.successor()
                assertNotSame(origin, fresh.scope?.owner)
                val before = queued.trace.calls.value.size
                callback.publish()
                queued.track(queued.caller.drain(this))
                val result = old.await()
                val tail = queued.trace.calls.value.drop(before)
                if (tail.any { it.name in setOf("core.generate", "core.init", "key.save", "key.clear", "socket.resume") }) {
                    failures.add("Old uninitialized A callback changed or resumed B: $tail")
                }
                if (result.status == RegistrationStatus.Ready || queued.cache.canonicalFrames.value !== fresh) {
                    failures.add("Old A callback consumed/published B readiness")
                }
                assertEquals("stored-key-b", queued.keys.getString("master_key"))
                reached.add("queued-uninitialized-A/actual-B/old-callback")
            } finally { queued.close() }

            val live = NativeRegistrationTestFixture()
            try {
                assertEquals(RegistrationStatus.Prepared, live.session.prepare("Fixture Vault").status)
                assertEquals(1, live.trace.count("core.generate"))
                assertEquals(1, live.trace.count("core.init"))
                assertEquals(1, live.trace.count("core.creds"))
                assertEquals(0, live.trace.count("core.signUp"))
                assertEquals("generated-device-key", live.keys.getString("master_key"))
                assertEquals(RegistrationStatus.Ready, live.session.submit(false).status)
                assertEquals(1, live.trace.count("core.signUp"))
                reached.add("real-generate/save/init/creds/explicit-submit")
            } finally { live.close() }
            assertEquals(3, reached.size)
            assertEquals(emptyList(), failures, "All bootstrap/retired-callback/live controls reached: $reached")
        }
    }

    @Test
    fun testResolverMutationsDrainAndPreparedWrappersNeverSubmitUnderSuccessor() = runBlocking {
        withTimeout(30_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (port in listOf("creds", "signUp")) {
                val f = NativeRegistrationTestFixture()
                try {
                    f.initialize()
                    if (port == "signUp") assertEquals(VaultAvailability.AVAILABLE, f.resolver.prepareSignUp("Fixture Vault").availability)
                    val profile = f.profile()
                    val native = f.hold(port)
                    val operation = f.track(async(Dispatchers.IO) {
                        if (port == "creds") f.resolver.prepareSignUp("Fixture Vault") else f.resolver.continueSignUp()
                    })
                    native.entered.await()
                    val clears = f.trace.count("key.clear")
                    f.reset(profile)
                    if (f.trace.count("key.clear") != clears) failures.add("$port: real native mutation was not drained before Reset")
                    assertTrue(!native.exited.isCompleted)
                    native.release.complete(Unit)
                    val returned = operation.await()
                    f.awaitReset(profile)
                    when (returned) {
                        is PrepareSignUpResult -> if (returned.availability != null) failures.add("$port: retired credentials returned usable availability")
                        is AppStateResult -> if (returned.appState != null) failures.add("$port: retired submission returned usable state")
                    }
                    val fresh = f.successor()
                    assertSame(fresh, f.cache.canonicalFrames.value)
                    reached.add("held-$port/reset/successor")
                } finally { f.close() }
            }
            for (wrapper in listOf("prepared", "restored")) {
                val f = NativeRegistrationTestFixture()
                try {
                    f.initialize()
                    assertEquals(VaultAvailability.AVAILABLE, f.resolver.prepareSignUp("Fixture Vault").availability)
                    if (wrapper == "restored") {
                        f.resolver.clearPreparedSignUp()
                        assertTrue(f.resolver.restorePreparedSignUp())
                    }
                    val profile = f.profile()
                    f.reset(profile)
                    f.awaitReset(profile)
                    f.core.local()
                    val fresh = f.successor(f.core.state.value)
                    val submissions = f.trace.count("core.signUp")
                    val old = f.resolver.continueSignUp()
                    if (f.trace.count("core.signUp") != submissions || old.appState != null) {
                        failures.add("$wrapper A wrapper submitted under actual B")
                    }
                    if (f.cache.canonicalFrames.value !== fresh) failures.add("$wrapper A wrapper changed B proof")
                    f.core.local()
                    assertEquals(VaultAvailability.AVAILABLE, f.resolver.prepareSignUp("Fresh Vault").availability)
                    val before = f.trace.count("core.signUp")
                    assertIs<MemberState>(f.resolver.continueSignUp().appState)
                    assertEquals(before + 1, f.trace.count("core.signUp"))
                    assertEquals("Fresh Vault", f.core.user.vaultName)
                    reached.add("$wrapper-A/rejected/fresh-B-submit")
                } finally { f.close() }
            }
            assertEquals(4, reached.size)
            assertEquals(emptyList(), failures, "Both native drains and both wrapper/live controls reached: $reached")
        }
    }
}
