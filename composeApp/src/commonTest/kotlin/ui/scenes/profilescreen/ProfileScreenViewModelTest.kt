package ui.scenes.profilescreen

import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.NotificationCoordinatorInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeAppStateCacheProvider
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeNotificationCoordinator
import testutils.FakeVaultStatsProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(
                module {
                    single<DebugLoggerInterface> { FakeDebugLogger() }
                    single<NotificationCoordinatorInterface> { FakeNotificationCoordinator() }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `reset all data clears keychain and requests sign in navigation`() = runTest(dispatcher) {
        val keyChain = FakeKeyChain(mutableMapOf("master_key" to "mk"))
        val viewModel = createViewModel(keyChain = keyChain)

        viewModel.handle(ProfileEvents.ResetAllData)
        advanceUntilIdle()

        assertEquals(1, keyChain.clearAllCalls)
        assertEquals(true, keyChain.clearAllIsCleanDb)
        assertEquals(ProfileNavigationEvent.NavigateToSignIn, viewModel.navigationEvent.value)
    }

    private fun createViewModel(
        keyChain: FakeKeyChain,
    ): ProfileScreenViewModel {
        return ProfileScreenViewModel(
            deviceInfoProvider = FakeDeviceInfoProvider(),
            socketHandler = FakeSocketHandler(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            keyChainManager = keyChain,
            appStateCacheProvider = FakeAppStateCacheProvider(),
        )
    }
}

private class FakeSocketHandler : MetaSecretSocketHandlerInterface {
    override val socketActionType: StateFlow<SocketActionModel> = MutableStateFlow(SocketActionModel.NONE)
    override val socketActions: SharedFlow<SocketActionModel> = MutableSharedFlow()
    override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) = Unit
    override fun pausePolling() = Unit
    override fun resumePolling() = Unit
    override fun setProcessingSecretName(secretName: String) = Unit
    override fun resetReadyToRecoverDedup(claimId: String?) = Unit
}

private class FakeDeviceInfoProvider : DeviceInfoProviderInterface {
    override fun getAppVersion(): String = "1.0.0"
    override fun getDeviceMake(): String = "Android"
    override fun getDeviceId(): String = "device-1"
}
