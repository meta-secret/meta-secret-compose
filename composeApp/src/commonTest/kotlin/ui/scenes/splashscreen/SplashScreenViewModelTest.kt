package ui.scenes.splashscreen

import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.SecretApiModel
import models.apiModels.UserData
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import models.appInternalModels.ClaimModel
import models.appInternalModels.RestoreData
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider

class SplashScreenViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `duplicate biometric success checks auth and navigates only once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(
                module {
                    single<DebugLoggerInterface> { FakeDebugLogger() }
                    single<NotificationCoordinatorInterface> { FakeNotificationCoordinator() }
                }
            )
        }

        try {
            val appManager = SplashAppManagerFake()
            val socketHandler = SplashSocketHandlerFake()
            val vaultStatsProvider = FakeVaultStatsProvider()
            val keyValueStorage = FakeKeyValueStorage().apply {
                isOnboardingCompleted = true
            }
            val viewModel = SplashScreenViewModel(
                keyValueStorage = keyValueStorage,
                biometricAuthenticator = FakeBiometricAuthenticator(),
                metaSecretAppManager = appManager,
                screenMetricsProvider = FakeScreenMetricsProvider(),
                vaultStatsProvider = vaultStatsProvider,
                stringProvider = FakeStringProvider(),
                keyChainManager = FakeKeyChain(),
                socketHandler = socketHandler,
            )

            viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
            viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
            advanceUntilIdle()

            // A late duplicate after navigation has completed must also be ignored.
            viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
            advanceUntilIdle()

            assertEquals(1, appManager.checkAuthCalls)
            assertEquals(1, socketHandler.onAppForegroundCalls)
            assertEquals(1, vaultStatsProvider.refreshCalls)
            assertEquals(SplashNavigationEvent.NavigateToMain, viewModel.navigationEvent.value)
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private class SplashAppManagerFake : MetaSecretAppManagerInterface {
        var checkAuthCalls = 0

        override suspend fun initWithSavedKey(): InitResult = error("Not used")

        override suspend fun checkAuth(): AuthState {
            checkAuthCalls += 1
            return AuthState.COMPLETED
        }

        override suspend fun getStateModel(): AppStateModel? = null
        override suspend fun getVaultFullInfoModel(): VaultFullInfo? = null
        override suspend fun getJoinRequestsCount(): Int? = null
        override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? = null
        override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? = null
        override suspend fun removeMember(targetDeviceId: String): CommonResponseModel? = null
        override suspend fun approveRemoveMember(targetDeviceId: String): CommonResponseModel? = null
        override suspend fun declineRemoveMember(targetDeviceId: String): CommonResponseModel? = null
        override suspend fun getUserDataBy(deviceId: String): UserData? = null
        override suspend fun deleteSecret(secretId: String): CommonResponseModel? = null
        override suspend fun decideDeleteSecret(requestId: String, accepted: Boolean): CommonResponseModel? = null
        override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? = null
        override suspend fun findClaim(secretId: String): ClaimModel? = null
        override suspend fun recover(secretModel: SecretModel): CommonResponseModel? = null
        override suspend fun acceptRecover(claimId: String?): AppStateModel? = null
        override suspend fun declineRecover(claimId: String?): AppStateModel? = null
        override suspend fun sendDeclineCompletion(claimId: String?) = Unit
        override suspend fun showRecovered(secretModel: SecretModel): String? = null
        override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<SecretApiModel>? = null
    }

    private class SplashSocketHandlerFake : MetaSecretSocketHandlerInterface {
        override val socketActionType: StateFlow<SocketActionModel> =
            MutableStateFlow(SocketActionModel.NONE)
        override val socketActions: SharedFlow<SocketActionModel> = MutableSharedFlow()
        override val pendingRecoveryRequests: StateFlow<List<RestoreData>> =
            MutableStateFlow(emptyList())
        var onAppForegroundCalls = 0

        override fun actionsToFollow(
            add: List<SocketRequestModel>?,
            exclude: List<SocketRequestModel>?,
        ) = Unit

        override fun pauseRefreshes() = Unit
        override fun resumeRefreshes() = Unit
        override fun onAppLaunch() = Unit
        override fun onAppForeground() {
            onAppForegroundCalls += 1
        }

        override fun onAppBackground() = Unit
        override fun refreshAppState() = Unit
        override fun setProcessingSecretName(secretName: String) = Unit
        override fun resetReadyToRecoverDedup(claimId: String?) = Unit
        override fun resetSocketActionType(expected: SocketActionModel?) = Unit
    }
}
