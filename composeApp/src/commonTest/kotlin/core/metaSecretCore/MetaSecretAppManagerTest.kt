package core.metaSecretCore

import core.errors.ErrorMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import testutils.FakeAppStateCacheProvider
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

class MetaSecretAppManagerTest {

    @Test
    fun `initWithSavedKey returns error and clears storage when key is missing`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain()
        val notificationCoordinator = FakeNotificationCoordinator()
        val manager = createManager(
            keyChain = keyChain,
            notificationCoordinator = notificationCoordinator,
        )

        val result = manager.initWithSavedKey()

        val error = assertIs<InitResult.Error>(result)
        assertEquals("No master key found", error.message)
        assertEquals(1, keyChain.clearAllCalls)
        assertEquals(true, keyChain.clearAllIsCleanDb)
        assertEquals(0, notificationCoordinator.errorMessages.size)
    }

    @Test
    fun `initWithSavedKey returns success when app manager initializes`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk"))
        val manager = createManager(keyChain = keyChain)

        val result = manager.initWithSavedKey()

        val success = assertIs<InitResult.Success>(result)
        assertEquals("ok", success.result)
    }

    @Test
    fun `initWithSavedKey maps init exception to user-facing notification`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk"))
        val core = FakeMetaSecretCore().apply {
            initAppManagerResult = Result.failure(IllegalStateException("boom"))
        }
        val notificationCoordinator = FakeNotificationCoordinator()
        val manager = createManager(
            core = core,
            keyChain = keyChain,
            notificationCoordinator = notificationCoordinator,
        )

        val result = manager.initWithSavedKey()

        val error = assertIs<InitResult.Error>(result)
        assertEquals("boom", error.message)
        assertEquals(listOf("validation error"), notificationCoordinator.errorMessages)
    }

    @Test
    fun `checkAuth returns NOT_YET_COMPLETED when initialization fails`() = kotlinx.coroutines.test.runTest {
        val manager = createManager(keyChain = FakeKeyChain())

        val state = manager.checkAuth()

        assertEquals(AuthState.NOT_YET_COMPLETED, state)
    }

    private fun createManager(
        core: FakeMetaSecretCore = FakeMetaSecretCore(),
        keyChain: FakeKeyChain = FakeKeyChain(),
        notificationCoordinator: FakeNotificationCoordinator = FakeNotificationCoordinator(),
    ): MetaSecretAppManager {
        return MetaSecretAppManager(
            metaSecretCore = core,
            keyChainInterface = keyChain,
            keyValueStorage = FakeKeyValueStorage(),
            logger = FakeDebugLogger(),
            notificationCoordinator = notificationCoordinator,
            errorMapper = ErrorMapper(FakeStringProvider()),
            logFormatter = FakeLogFormatter(),
            appStateCacheProvider = FakeAppStateCacheProvider(),
        )
    }
}
