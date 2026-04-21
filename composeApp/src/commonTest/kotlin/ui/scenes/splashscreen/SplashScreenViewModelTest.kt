package ui.scenes.splashscreen

import core.DebugLoggerInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.SecretApiModel
import models.apiModels.UserData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeBackupCoordinator
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeScreenMetricsProvider
import testutils.FakeVaultStatsProvider

class SplashScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var testLogger: FakeDebugLogger

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        testLogger = FakeDebugLogger()
        startKoin {
            modules(module { single<DebugLoggerInterface> { testLogger } })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `navigates to onboarding when onboarding is incomplete`() = runTest(dispatcher) {
        val keyStorage = FakeKeyValueStorage().apply { isOnboardingCompleted = false }
        val backup = FakeBackupCoordinator(hasDb = true)
        val viewModel = createViewModel(
            keyValueStorage = keyStorage,
            appManager = FakeAppManager(authState = AuthState.COMPLETED),
            backupCoordinator = backup,
        )

        viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
        advanceUntilIdle()

        assertEquals(SplashNavigationEvent.NavigateToOnboarding, viewModel.navigationEvent.value)
        assertEquals(1, backup.restoreCalls)
        assertEquals(true, testLogger.backupDbExists)
    }

    @Test
    fun `navigates to main when onboarding complete and auth complete`() = runTest(dispatcher) {
        val keyStorage = FakeKeyValueStorage().apply { isOnboardingCompleted = true }
        val vaultStats = FakeVaultStatsProvider()
        val viewModel = createViewModel(
            keyValueStorage = keyStorage,
            appManager = FakeAppManager(authState = AuthState.COMPLETED),
            backupCoordinator = FakeBackupCoordinator(hasDb = false),
            vaultStatsProvider = vaultStats,
        )

        viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
        advanceUntilIdle()

        assertEquals(SplashNavigationEvent.NavigateToMain, viewModel.navigationEvent.value)
        assertEquals(1, vaultStats.refreshCalls)
    }

    @Test
    fun `navigates to sign up when onboarding complete and auth incomplete`() = runTest(dispatcher) {
        val keyStorage = FakeKeyValueStorage().apply { isOnboardingCompleted = true }
        val viewModel = createViewModel(
            keyValueStorage = keyStorage,
            appManager = FakeAppManager(authState = AuthState.NOT_YET_COMPLETED),
            backupCoordinator = FakeBackupCoordinator(hasDb = false),
        )

        viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
        advanceUntilIdle()

        assertEquals(SplashNavigationEvent.NavigateToSignUp, viewModel.navigationEvent.value)
    }

    private fun createViewModel(
        keyValueStorage: FakeKeyValueStorage,
        appManager: MetaSecretAppManagerInterface,
        backupCoordinator: FakeBackupCoordinator,
        vaultStatsProvider: FakeVaultStatsProvider = FakeVaultStatsProvider(),
    ): SplashScreenViewModel {
        return SplashScreenViewModel(
            keyValueStorage = keyValueStorage,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            metaSecretAppManager = appManager,
            keyChain = FakeKeyChain(),
            backupCoordinatorInterface = backupCoordinator,
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = vaultStatsProvider,
        )
    }
}

private class FakeAppManager(
    private val authState: AuthState,
) : MetaSecretAppManagerInterface {
    override suspend fun initWithSavedKey(): InitResult = InitResult.Success("ok")
    override suspend fun checkAuth(): AuthState = authState
    override suspend fun getStateModel(): AppStateModel? = null
    override suspend fun getVaultFullInfoModel(): VaultFullInfo? = null
    override suspend fun getJoinRequestsCount(): Int? = null
    override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? = null
    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? = null
    override suspend fun getUserDataBy(deviceId: String): UserData? = null
    override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? = null
    override suspend fun findClaim(secretId: String): ClaimModel? = null
    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? = null
    override suspend fun acceptRecover(claimId: String?): AppStateModel? = null
    override suspend fun declineRecover(claimId: String?): AppStateModel? = null
    override suspend fun sendDeclineCompletion(claimId: String?) = Unit
    override suspend fun showRecovered(secretModel: SecretModel): String? = null
    override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<SecretApiModel>? = null
}
