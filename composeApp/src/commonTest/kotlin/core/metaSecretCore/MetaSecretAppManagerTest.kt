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
    fun `initWithSavedKey retries key read when key exists but first read misses`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(
            values = mutableMapOf("master_key" to "mk"),
            scriptedReads = mutableMapOf("master_key" to ArrayDeque(listOf(null, "mk"))),
        )
        val manager = createManager(keyChain = keyChain)

        val result = manager.initWithSavedKey()

        val success = assertIs<InitResult.Success>(result)
        assertEquals("ok", success.result)
        assertEquals(0, keyChain.clearAllCalls)
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

    @Test
    fun `initWithSavedKey does not reinit when the same key is already initialized`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk1"))
        val core = FakeMetaSecretCore()
        val manager = createManager(core = core, keyChain = keyChain)

        val first = manager.initWithSavedKey()
        val second = manager.initWithSavedKey()

        assertIs<InitResult.Success>(first)
        val secondSuccess = assertIs<InitResult.Success>(second)
        assertEquals("Already initialized", secondSuccess.result)
        assertEquals(1, core.initAppManagerCalls)
        assertEquals("mk1", core.lastInitMasterKey)
    }

    @Test
    fun `initWithSavedKey reinitializes when master key changes`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk1"))
        val core = FakeMetaSecretCore()
        val manager = createManager(core = core, keyChain = keyChain)

        val first = manager.initWithSavedKey()
        keyChain.saveString("master_key", "mk2")
        val second = manager.initWithSavedKey()

        assertIs<InitResult.Success>(first)
        assertIs<InitResult.Success>(second)
        assertEquals(2, core.initAppManagerCalls)
        assertEquals("mk2", core.lastInitMasterKey)
    }

    @Test
    fun `initWithSavedKey resets initialized state when key is missing`() = kotlinx.coroutines.test.runTest {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk1"))
        val core = FakeMetaSecretCore()
        val manager = createManager(core = core, keyChain = keyChain)

        manager.initWithSavedKey()
        keyChain.clearAll(isCleanDB = true)
        val missing = manager.initWithSavedKey()
        keyChain.saveString("master_key", "mk2")
        val reinit = manager.initWithSavedKey()

        val missingError = assertIs<InitResult.Error>(missing)
        assertEquals("No master key found", missingError.message)
        assertIs<InitResult.Success>(reinit)
        assertEquals(2, core.initAppManagerCalls)
        assertEquals("mk2", core.lastInitMasterKey)
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
