package ui.scenes.devicesscreen

import androidx.lifecycle.viewModelScope
import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.MetaSecretAppManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeAlertCoordinator
import testutils.FakeAppStateCacheProvider
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeSecretSocketHandler
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesVaultQrTest {
    @Test
    fun testLoadingAndBlankVaultCannotOpenQr() = withModel { model, name ->
        for (missing in listOf(null, "", " \t ")) {
            name.value = missing
            runCurrent()
            model.handle(DeviceViewEvents.OpenAddDevice)
            assertNull(model.addDeviceQrName.value)
        }
    }

    @Test
    fun testQrUsesCanonicalNameInsteadOfCachedLoginName() = withModel { model, name ->
        name.value = "Person@Example.org"
        runCurrent()
        model.handle(DeviceViewEvents.OpenAddDevice)
        assertEquals("Person@Example.org", model.addDeviceQrName.value)
    }

    @Test
    fun testQrPreservesRelayNonEmailAndExactCanonicalContent() = withModel { model, name ->
        for (canonical in listOf("relay@privaterelay.appleid.com", "Family Vault", " MiXeD Vault ")) {
            name.value = canonical
            runCurrent()
            model.handle(DeviceViewEvents.OpenAddDevice)
            assertEquals(canonical, model.addDeviceQrName.value)
            model.handle(DeviceViewEvents.CloseAddDevice)
        }
    }

    @Test
    fun testCanonicalNameChangeClosesQrAndRequiresExplicitReopen() = withModel { model, name ->
        name.value = "first@example.org"
        runCurrent()
        model.handle(DeviceViewEvents.OpenAddDevice)
        name.value = "second@example.org"
        runCurrent()
        assertNull(model.addDeviceQrName.value)
        model.handle(DeviceViewEvents.OpenAddDevice)
        assertEquals("second@example.org", model.addDeviceQrName.value)
    }

    @Test
    fun testDisappearingNameCannotReopenAnOldDialog() = withModel { model, name ->
        name.value = "family@example.org"
        runCurrent()
        model.handle(DeviceViewEvents.OpenAddDevice)
        name.value = null
        runCurrent()
        assertNull(model.addDeviceQrName.value)
        name.value = "family@example.org"
        runCurrent()
        assertNull(model.addDeviceQrName.value)
    }

    @Test
    fun testCloseKeepsDialogClosedUntilAnotherUserAction() = withModel { model, name ->
        name.value = "family@example.org"
        runCurrent()
        model.handle(DeviceViewEvents.OpenAddDevice)
        model.handle(DeviceViewEvents.CloseAddDevice)
        runCurrent()
        assertNull(model.addDeviceQrName.value)
        model.handle(DeviceViewEvents.OpenAddDevice)
        assertEquals("family@example.org", model.addDeviceQrName.value)
    }

    private fun withModel(
        block: suspend TestScope.(DevicesScreenViewModel, MutableStateFlow<String?>) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val logger = FakeDebugLogger()
        val notifications = FakeNotificationCoordinator()
        startKoin {
            modules(module {
                single<DebugLoggerInterface> { logger }
                single<NotificationCoordinatorInterface> { notifications }
            })
        }
        val storage = FakeKeyValueStorage().apply { cachedVaultName = "wrong-cached@example.org" }
        val stats = FakeVaultStatsProvider()
        val manager = MetaSecretAppManager(
            FakeMetaSecretCore(), FakeKeyChain(), storage, logger, notifications,
            ErrorMapper(FakeStringProvider()), FakeLogFormatter(), FakeAppStateCacheProvider(),
        )
        val model = DevicesScreenViewModel(
            FakeScreenMetricsProvider(), FakeSecretSocketHandler(), manager, storage,
            stats, FakeAlertCoordinator(), FakeBiometricAuthenticator(), FakeStringProvider(),
        )
        try {
            runCurrent()
            block(model, stats.vaultName as MutableStateFlow<String?>)
        } finally {
            model.viewModelScope.cancel()
            stopKoin()
            Dispatchers.resetMain()
        }
    }
}
