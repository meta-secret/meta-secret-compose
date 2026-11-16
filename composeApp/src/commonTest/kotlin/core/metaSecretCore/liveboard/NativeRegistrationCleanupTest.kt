package core.metaSecretCore.liveboard

import core.registration.RegistrationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.apiModels.UserDataOutsiderStatus

class NativeRegistrationCleanupTest {
    @Test
    fun testIncompleteAttemptCleanupCannotClearSameIdentitySuccessorAndPreservesExistingGuards() = runBlocking {
        withTimeout(40_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            for (status in listOf(UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED)) {
                for (action in listOf("cleanup", "replace")) {
                    val f = NativeRegistrationTestFixture()
                    try {
                        f.core.exists = true
                        f.initialize(f.core.outsider(status))
                        val expected = if (status == UserDataOutsiderStatus.PENDING) RegistrationStatus.Pending else RegistrationStatus.Declined
                        assertEquals(expected, f.session.resume().status)
                        f.pause()
                        val origin = f.initialization.capture()
                        val profile = f.profile()
                        val old = f.track(async(f.caller) {
                            if (action == "cleanup") f.session.cleanupIncomplete() else f.session.prepare("Different Vault")
                        })
                        val callback = f.caller.take()
                        assertTrue(!old.isCompleted)
                        f.reset(profile)
                        f.awaitReset(profile)
                        // IDs deliberately match A; only the real native owner boundary distinguishes B.
                        val fresh = f.successor(f.core.outsider(status))
                        assertNotSame(origin, fresh.scope?.owner)
                        assertEquals("Fixture Vault", fresh.state?.getCurrentVaultName())
                        assertEquals("registration-device", fresh.state?.getCurrentDeviceId())
                        val before = f.trace.calls.value.size
                        callback.publish()
                        f.track(f.caller.drain(this))
                        val result = old.await()
                        f.pause()
                        val tail = f.trace.calls.value.drop(before)
                        if (tail.any { it.name in setOf("key.clear", "key.save", "core.generate", "core.init", "core.creds", "socket.resume") }) {
                            failures.add("$status/$action: old A changed/forgot/resumed actual B: $tail")
                        }
                        if (result.status in setOf(RegistrationStatus.Empty, RegistrationStatus.Prepared, RegistrationStatus.Ready)) {
                            failures.add("$status/$action: retired A claimed a completed replacement/cleanup")
                        }
                        if (f.cache.canonicalFrames.value !== fresh) failures.add("$status/$action: old A published/cleared B proof")
                        if (f.keys.getString("master_key") != "stored-key-b") failures.add("$status/$action: old A erased B key")
                        reached.add("same-IDs-new-owner/$status/$action")
                    } finally { f.close() }
                }
            }
            for (guard in listOf("member", "name", "device", "missing-key", "unreadable", "false-clear", "retained-key")) {
                val f = NativeRegistrationTestFixture()
                try {
                    f.core.exists = true
                    f.initialize(f.core.outsider(UserDataOutsiderStatus.PENDING))
                    assertEquals(RegistrationStatus.Pending, f.session.resume().status)
                    f.pause()
                    assertTrue(f.session.reset()) // Back is observation-only, not identity revocation.
                    assertEquals(0, f.trace.count("key.clear"))
                    assertEquals(RegistrationStatus.Pending, f.session.resume().status)
                    f.pause()
                    when (guard) {
                        "member" -> f.core.state.value = f.core.member()
                        "name" -> {
                            f.core.user = f.core.user.copy(vaultName = "Other Vault")
                            f.core.state.value = f.core.outsider(UserDataOutsiderStatus.PENDING)
                        }
                        "device" -> {
                            f.core.user = f.core.user.copy(device = f.core.user.device.copy(deviceId = "other-device"))
                            f.core.state.value = f.core.outsider(UserDataOutsiderStatus.PENDING)
                        }
                        "missing-key" -> f.keys.removeKey("master_key")
                        "unreadable" -> f.core.unreadable = true
                        "false-clear" -> f.keys.clearReturns = false
                        "retained-key" -> f.keys.retainOnClear = true
                    }
                    val result = f.session.cleanupIncomplete()
                    if (guard == "member") {
                        assertEquals(RegistrationStatus.Ready, result.status)
                        assertEquals(0, f.trace.count("key.clear"))
                    } else {
                        assertEquals(RegistrationStatus.Failed, result.status)
                        assertEquals(if (guard in listOf("false-clear", "retained-key")) 1 else 0, f.trace.count("key.clear"))
                    }
                    if (guard != "missing-key") assertEquals("stored-key-a", f.keys.getString("master_key"))
                    reached.add("guard-$guard")
                } finally { f.close() }
            }
            for (status in listOf(UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED)) {
                val f = NativeRegistrationTestFixture()
                try {
                    f.core.exists = true
                    f.initialize(f.core.outsider(status))
                    f.session.resume()
                    f.pause()
                    assertEquals(RegistrationStatus.Empty, f.session.cleanupIncomplete().status)
                    assertEquals(1, f.trace.count("key.clear"))
                    assertTrue(!f.keys.containsKey("master_key"))
                    assertSame(null, f.cache.canonicalFrames.value)
                    reached.add("actual-live-cleanup-$status")
                } finally { f.close() }
            }
            assertEquals(13, reached.size)
            assertEquals(emptyList(), failures, "All stale-owner and unchanged cleanup policies reached: $reached")
        }
    }

    @Test
    fun testPendingIdentitySaveDrainsBeforeResetAndAmbiguousSubmissionStillReconcilesOnce() = runBlocking {
        withTimeout(25_000) {
            val failures = mutableListOf<String>()
            val reached = mutableListOf<String>()
            val held = NativeRegistrationTestFixture()
            try {
                assertEquals(RegistrationStatus.Prepared, held.session.prepare("Fixture Vault").status)
                val profile = held.profile()
                val save = held.gate()
                held.keys.pendingSaveGate = save
                val submit = held.track(async(Dispatchers.IO) { held.session.submit(false) })
                save.entered.await()
                val before = held.trace.count("key.clear")
                held.reset(profile)
                if (held.trace.count("key.clear") != before) failures.add("Reset cleared while actual pending-identity save was entered")
                assertTrue(!save.exited.isCompleted)
                save.release.complete(Unit)
                submit.await()
                held.awaitReset(profile)
                val fresh = held.successor()
                assertEquals("stored-key-b", held.keys.getString("master_key"))
                if (held.keys.containsKey("pending_vault_email")) failures.add("Settled old pending identity survived Reset into B")
                assertSame(fresh, held.cache.canonicalFrames.value)
                reached.add("entered-pending-save/reset/actual-successor")
            } finally { held.close() }

            for (ambiguous in listOf(false, true)) {
                val f = NativeRegistrationTestFixture()
                try {
                    assertEquals(RegistrationStatus.Prepared, f.session.prepare("Fixture Vault").status)
                    assertEquals(0, f.trace.count("core.signUp"))
                    f.core.ambiguousSubmit = ambiguous
                    assertEquals(RegistrationStatus.Ready, f.session.submit(false).status)
                    assertEquals(1, f.trace.count("core.signUp"))
                    val events = f.trace.calls.value
                    val persisted = events.indexOfFirst { it.name == "key.save" && it.arguments == listOf("pending_vault_email", "Fixture Vault") }
                    val submitted = events.indexOfFirst { it.name == "core.signUp" }
                    assertTrue(persisted >= 0 && submitted > persisted)
                    assertEquals(RegistrationStatus.Ready, f.session.submit(false).status)
                    assertEquals(1, f.trace.count("core.signUp"), "Canonical Ready never blindly resubmits")
                    reached.add("explicit-submit/ambiguous=$ambiguous/reconcile-once")
                } finally { f.close() }
            }
            assertEquals(3, reached.size)
            assertEquals(emptyList(), failures, "All pending-key and exactly-once reconciliation controls reached: $reached")
        }
    }
}
