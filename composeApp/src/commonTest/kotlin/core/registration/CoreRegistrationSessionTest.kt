package core.registration

import core.errors.ErrorMapper
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretStateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.encodeToString
import models.apiModels.*
import testutils.*

@OptIn(ExperimentalCoroutinesApi::class)
class CoreRegistrationSessionTest {
    @Test
    fun testPrepareNeverSubmitsAndDuplicateRequestUsesSamePendingAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            assertEquals(RegistrationStatus.Prepared, env.session.prepare("Family Vault").status)
            assertEquals(0, env.core.submissions)
            assertEquals(RegistrationStatus.Pending, env.session.submit(true).status)
            assertEquals(RegistrationStatus.Pending, env.session.submit(true).status)
            assertEquals(1, env.core.submissions)
            assertEquals("Family Vault", env.core.user.vaultName)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testUnknownQrVaultCannotCreateThroughJoin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture().apply { core.exists = false }
        try {
            env.session.prepare("New Vault")
            assertEquals(RegistrationStatus.Failed, env.session.submit(true).status)
            assertEquals(0, env.core.submissions)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testAcceptedMemberCannotBeErasedByBack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.session.prepare("Family Vault")
            env.session.submit(true)
            env.core.value = env.core.member()
            val clears = env.keys.clearAllCalls
            assertFalse(env.session.reset())
            assertEquals(clears, env.keys.clearAllCalls)
            assertTrue(env.keys.containsKey("master_key"))
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testResumeDeclinedDoesNotResubmit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.keys.saveString("master_key", "stored-device-key")
            env.core.value = env.core.outsider(UserDataOutsiderStatus.DECLINED)
            val restored = env.session.resume()
            assertEquals(RegistrationStatus.Declined, restored.status)
            assertEquals(0, env.core.submissions)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testUnreadableExistingKeyIsNeverDeletedForDifferentName() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.keys.saveString("master_key", "stored-device-key")
            env.core.failInitialization = true
            assertEquals(RegistrationStatus.Failed, env.session.prepare("Different Vault").status)
            assertTrue(env.keys.containsKey("master_key"))
            assertEquals(0, env.keys.clearAllCalls)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testNonDestructiveResetPreservesIdentityAndRequestCanResume() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.session.prepare("Family Vault")
            env.session.submit(true)
            val clears = env.keys.clearAllCalls
            val key = env.keys.getString("master_key")
            assertTrue(env.session.reset())
            assertEquals(RegistrationStatus.Prepared, env.session.prepare("Family Vault").status)
            assertEquals(RegistrationStatus.Pending, env.session.submit(true).status)
            assertEquals(key, env.keys.getString("master_key"))
            assertEquals(clears, env.keys.clearAllCalls)
            assertEquals(1, env.core.submissions)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testPreparedRelaunchRebuildsOnlyClientWrapper() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.keys.saveString("master_key", "stored-device-key")
            env.core.value = env.core.outsider(UserDataOutsiderStatus.NON_MEMBER)
            assertEquals(RegistrationStatus.Prepared, env.session.resume().status)
            assertEquals(RegistrationStatus.Prepared, env.session.prepare("Family Vault").status)
            assertEquals(RegistrationStatus.Pending, env.session.submit(true).status)
            assertEquals(0, env.core.credentialGenerations)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testPreparationFailurePreservesNameAndCanRestartFromCoreLocalState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.core.failCredentials = true
            val failed = env.session.prepare("Family Vault")
            assertEquals(RegistrationStatus.Failed, failed.status)
            assertEquals("Family Vault", failed.email)
            assertTrue(env.session.reset())
            env.core.failCredentials = false
            assertEquals(RegistrationStatus.Prepared, env.session.prepare("Family Vault").status)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testQrDataIsPlainNameAndNeverExecutablePayload() {
        assertEquals("Family Vault", registrationQrName("  Family Vault  "))
        assertEquals(null, registrationQrName("https://example.org"))
        assertEquals(null, registrationQrName("{\"token\":\"anything\"}"))
        assertEquals(null, registrationQrName("Family\nVault"))
    }

    @Test
    fun testPendingExplicitCleanupWipesBeforeRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            env.restorePending()
            assertTrue(env.keys.containsKey("master_key"))
            assertEquals(0, env.keys.clearAllCalls)
            assertEquals(RegistrationStatus.Empty, env.session.cleanupIncomplete().status)
            assertFalse(env.keys.containsKey("master_key"))
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupWipesOnlyAfterBackAndAllowsDifferentVault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    assertTrue(env.keys.containsKey("master_key"))
                    assertEquals(0, env.keys.clearAllCalls)
                    env.keys.saveString("pending_vault_email", "Family Vault")
                    env.keys.saveString("pending_email_provider", "MANUAL")
                    assertEquals(RegistrationStatus.Empty, env.session.cleanupIncomplete().status)
                    assertEquals(true, env.keys.clearAllIsCleanDb)
                    for (key in listOf("master_key", "pending_vault_email", "pending_email_provider")) assertFalse(env.keys.containsKey(key))
                    assertEquals(null, env.storage.cachedDeviceId)
                    assertEquals(null, env.storage.cachedVaultName)
                    assertEquals(null, env.cache.appState.value)
                    assertEquals(RegistrationStatus.Prepared, env.session.prepare("Other Vault").status)
                    assertEquals(RegistrationStatus.Pending, env.session.submit(true).status)
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupPreservesMemberFoundAfterRefreshPause() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    env.socket.onPause = { env.core.value = env.core.member() }
                    assertEquals(RegistrationStatus.Ready, env.session.cleanupIncomplete().status)
                    assertEquals(0, env.keys.clearAllCalls)
                    assertTrue(env.keys.containsKey("master_key"))
                    assertEquals(1, env.socket.resumes)
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupUnknownOrWrongIdentityNeverDeletes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) for (mismatch in listOf("unknown", "vault", "device", "unreadable")) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    val initializations = env.core.initializations
                    when (mismatch) {
                        "unknown" -> env.core.unreadableState = true
                        "vault" -> env.core.user = env.core.user.copy(vaultName = "Different Vault")
                        "device" -> env.core.user = env.core.user.copy(device = env.core.user.device.copy(deviceId = "different-device"))
                        "unreadable" -> env.keys.unreadable = true
                    }
                    if (mismatch in listOf("vault", "device")) env.core.value = env.core.outsider(initial)
                    assertEquals(RegistrationStatus.Failed, env.session.cleanupIncomplete().status, mismatch)
                    assertEquals(0, env.keys.clearAllCalls, mismatch)
                    assertEquals(initializations, env.core.initializations, "Cleanup must not initialize unreadable keys")
                    assertTrue(env.keys.containsKey("master_key"), mismatch)
                    assertEquals(1, env.socket.resumes)
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupFalseOrRetainedCredentialIsNotSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) for (retain in listOf(false, true)) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    env.keys.clearReturns = retain
                    env.keys.retainOnClear = retain
                    assertEquals(RegistrationStatus.Failed, env.session.cleanupIncomplete().status)
                    assertTrue(env.keys.containsKey("master_key"))
                    assertEquals(1, env.socket.resumes)
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testIncompleteCleanupUsesFreshPendingOrDeclinedAndPreservesPrepared() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) for (fresh in incompleteStates + UserDataOutsiderStatus.NON_MEMBER) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    env.core.value = env.core.outsider(fresh)
                    val expected = if (fresh == UserDataOutsiderStatus.NON_MEMBER) RegistrationStatus.Prepared else RegistrationStatus.Empty
                    assertEquals(expected, env.session.cleanupIncomplete().status)
                    assertEquals(if (fresh == UserDataOutsiderStatus.NON_MEMBER) 0 else 1, env.keys.clearAllCalls)
                    assertEquals(fresh == UserDataOutsiderStatus.NON_MEMBER, env.keys.containsKey("master_key"))
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun testMissingAttemptCannotClaimVerifiedCleanup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val env = RegistrationSessionFixture()
        try {
            assertEquals(RegistrationStatus.Failed, env.session.cleanupIncomplete().status)
            assertEquals(0, env.keys.clearAllCalls)
            assertEquals(0, env.core.initializations)
        } finally { env.session.stop(); Dispatchers.resetMain() }
    }

    @Test
    fun testLateOldSignalCannotResurrectWipedAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (initial in incompleteStates) {
                val env = RegistrationSessionFixture()
                try {
                    env.restoreIncomplete(initial)
                    assertEquals(RegistrationStatus.Empty, env.session.cleanupIncomplete().status)
                    env.core.value = env.core.member()
                    env.socket.socketActions.emit(models.appInternalModels.SocketActionModel.JOIN_REQUEST_ACCEPTED)
                    runCurrent()
                    assertEquals(RegistrationStatus.Empty, env.session.resume().status)
                    assertEquals(0, env.core.submissions)
                } finally { env.session.stop() }
            }
        } finally { Dispatchers.resetMain() }
    }

    private val incompleteStates = listOf(UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED)
}
