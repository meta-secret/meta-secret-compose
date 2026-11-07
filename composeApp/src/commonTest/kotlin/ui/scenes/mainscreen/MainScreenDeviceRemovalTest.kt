package ui.scenes.mainscreen

import core.AppStateCacheProviderInterface
import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.KeyChainInterface
import core.metaSecretCore.liveboard.LegacyNativeReadComposition
import core.metaSecretCore.liveboard.NativeLocalReset
import core.metaSecretCore.liveboard.NativeReadEnvironment
import testutils.FakeMetaSecretCore
import core.metaSecretCore.AuthState
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.liveboard.NativeReadRequirement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import testutils.FakeAlertCoordinator
import testutils.FakeAppStateCacheProvider
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeVaultStatsProvider

class MainScreenDeviceRemovalTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `current device removal clears local session once and navigates to sign in`() = runTest {
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
            val socketHandler = RemovalSocketHandlerFake()
            val keyChain = FakeKeyChain(mutableMapOf("master_key" to "local-test-key"))
            val appStateCache = FakeAppStateCacheProvider().apply {
                updateCache(AppStateModel(success = true))
            }
            val alerts = FakeAlertCoordinator().apply {
                showJoinRequest("candidate")
                showRecoveryRequest(RestoreData(claimId = "claim", secretId = "secret"))
            }
            val viewModel = MainScreenViewModel(
                socketHandler = socketHandler,
                metaSecretAppManager = RemovalAppManagerFake,
                biometricAuthenticator = FakeBiometricAuthenticator(),
                screenMetricsProvider = FakeScreenMetricsProvider(),
                dependencies = MainScreenDependencies(
                    FakeVaultStatsProvider(), alerts, removalReset(keyChain, appStateCache),
                ),
            )
            advanceUntilIdle()

            withContext(Dispatchers.Default) {
                withTimeout(2_000) { socketHandler.awaitSubscription() }
                socketHandler.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
                socketHandler.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
                withTimeout(2_000) {
                    while (viewModel.navigationEvent.value != MainNavigationEvent.NavigateToSignIn) {
                        testScheduler.runCurrent()
                        yield()
                    }
                }
            }
            advanceUntilIdle()

            assertEquals(1, keyChain.clearAllCalls)
            assertEquals(true, keyChain.clearAllIsCleanDb)
            assertEquals(null, appStateCache.appState.value)
            assertIs<core.JoinRequestAlertState.Hidden>(alerts.joinRequestAlert.value)
            assertIs<core.RecoveryRequestAlertState.Hidden>(alerts.recoveryRequestAlert.value)
            assertEquals(1, socketHandler.onAppBackgroundCalls)
            assertEquals(MainNavigationEvent.NavigateToSignIn, viewModel.navigationEvent.value)
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private fun removalReset(keys: KeyChainInterface, cache: AppStateCacheProviderInterface): NativeLocalReset {
        val composition = LegacyNativeReadComposition(
            FakeMetaSecretCore(), cache, FakeDebugLogger(),
            environment = NativeReadEnvironment(Dispatchers.Unconfined, Dispatchers.Unconfined),
        )
        return NativeLocalReset(composition.initialization, composition.keyLifecycle(keys), cache)
    }

    private class RemovalSocketHandlerFake : MetaSecretSocketHandlerInterface {
        override val socketActionType: StateFlow<SocketActionModel> =
            MutableStateFlow(SocketActionModel.NONE)
        private val mutableActions = MutableSharedFlow<SocketActionModel>(extraBufferCapacity = 8)
        override val socketActions: SharedFlow<SocketActionModel> = mutableActions
        override val pendingRecoveryRequests: StateFlow<List<RestoreData>> =
            MutableStateFlow(emptyList())
        var onAppBackgroundCalls = 0

        suspend fun awaitSubscription() {
            mutableActions.subscriptionCount.first { it > 0 }
        }

        fun emit(action: SocketActionModel) {
            mutableActions.tryEmit(action)
        }

        override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) = Unit
        override fun pauseRefreshes() = Unit
        override fun resumeRefreshes() = Unit
        override fun onAppLaunch() = Unit
        override fun onAppForeground() = Unit
        override fun onAppBackground() {
            onAppBackgroundCalls += 1
        }
        override fun refreshAppState() = Unit
        override fun refreshAppState(required: NativeReadRequirement) = refreshAppState()
        override fun setProcessingSecretName(secretName: String) = Unit
        override fun resetReadyToRecoverDedup(claimId: String?) = Unit
        override fun resetSocketActionType(expected: SocketActionModel?) = Unit
    }

    private data object RemovalAppManagerFake : MetaSecretAppManagerInterface {
        override suspend fun initWithSavedKey(): InitResult = error("Not used")
        override suspend fun checkAuth(): AuthState = error("Not used")
        override suspend fun getStateModel(): AppStateModel? = error("Not used")
        override suspend fun getVaultFullInfoModel(): VaultFullInfo? = error("Not used")
        override suspend fun getJoinRequestsCount(): Int? = error("Not used")
        override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? = error("Not used")
        override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? = error("Not used")
        override suspend fun removeMember(targetDeviceId: String): CommonResponseModel? = error("Not used")
        override suspend fun approveRemoveMember(targetDeviceId: String): CommonResponseModel? = error("Not used")
        override suspend fun declineRemoveMember(targetDeviceId: String): CommonResponseModel? = error("Not used")
        override suspend fun getUserDataBy(deviceId: String): UserData? = error("Not used")
        override suspend fun deleteSecret(secretId: String): CommonResponseModel? = null
        override suspend fun decideDeleteSecret(requestId: String, accepted: Boolean): CommonResponseModel? = null
        override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? = error("Not used")
        override suspend fun findClaim(secretId: String): ClaimModel? = null
        override suspend fun recover(secretModel: SecretModel): CommonResponseModel? = error("Not used")
        override suspend fun acceptRecover(claimId: String?): AppStateModel? = error("Not used")
        override suspend fun declineRecover(claimId: String?): AppStateModel? = error("Not used")
        override suspend fun sendDeclineCompletion(claimId: String?) = Unit
        override suspend fun showRecovered(secretModel: SecretModel): String? = error("Not used")
        override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<SecretApiModel>? = error("Not used")
    }
}
