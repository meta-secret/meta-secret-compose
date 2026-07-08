package ui.scenes.mainscreen

import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.RecoveryRequestAlertState
import core.metaSecretCore.AuthState
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.DistributionType
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
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeVaultStatsProvider

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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

    private suspend fun awaitCondition(timeoutMs: Long = 8000, intervalMs: Long = 20, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(intervalMs)
        }
    }

    @Test
    fun `READY_TO_RECOVER shows the alert when the secret exists locally`() = runBlocking {
        val socketHandler = FakeMainScreenSocketHandler()
        val appManager = FakeMainScreenAppManager(
            secretsInVault = listOf(SecretApiModel(id = "s1", name = "secret1"))
        )
        val alertCoordinator = FakeAlertCoordinator()
        MainScreenViewModel(
            socketHandler = socketHandler,
            metaSecretAppManager = appManager,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            alertCoordinator = alertCoordinator,
        )

        val restoreData = RestoreData("claim1", "secret1")
        socketHandler.emitActionType(SocketActionModel.READY_TO_RECOVER(restoreData))

        awaitCondition { alertCoordinator.showRecoveryRequestCalls.isNotEmpty() }
        assertEquals(restoreData, alertCoordinator.showRecoveryRequestCalls.first())
    }

    @Test
    fun `READY_TO_RECOVER is ignored when the secret does not exist locally`() = runBlocking {
        val socketHandler = FakeMainScreenSocketHandler()
        val appManager = FakeMainScreenAppManager(secretsInVault = emptyList())
        val alertCoordinator = FakeAlertCoordinator()
        MainScreenViewModel(
            socketHandler = socketHandler,
            metaSecretAppManager = appManager,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            alertCoordinator = alertCoordinator,
        )

        socketHandler.emitActionType(SocketActionModel.READY_TO_RECOVER(RestoreData("claim1", "secret1")))
        delay(300)

        assertTrue(alertCoordinator.showRecoveryRequestCalls.isEmpty())
        assertEquals(RecoveryRequestAlertState.Hidden, alertCoordinator.recoveryRequestAlert.value)
    }

    @Test
    fun `DISMISS_RECOVERY_REQUEST dismisses the alert for that claim`() = runBlocking {
        val socketHandler = FakeMainScreenSocketHandler()
        val appManager = FakeMainScreenAppManager()
        val alertCoordinator = FakeAlertCoordinator()
        MainScreenViewModel(
            socketHandler = socketHandler,
            metaSecretAppManager = appManager,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            alertCoordinator = alertCoordinator,
        )

        socketHandler.emitAction(SocketActionModel.DISMISS_RECOVERY_REQUEST("claim1"))

        awaitCondition { alertCoordinator.dismissRecoveryRequestForClaimCalls.isNotEmpty() }
        assertEquals("claim1", alertCoordinator.dismissRecoveryRequestForClaimCalls.first())
    }

    @Test
    fun `RECOVER_ACCEPTED sets the secret to show`() = runBlocking {
        val socketHandler = FakeMainScreenSocketHandler()
        val appManager = FakeMainScreenAppManager()
        val viewModel = MainScreenViewModel(
            socketHandler = socketHandler,
            metaSecretAppManager = appManager,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            alertCoordinator = FakeAlertCoordinator(),
        )

        socketHandler.emitActionType(SocketActionModel.RECOVER_ACCEPTED("claim1", "secret1"))

        val secretId = withTimeout(8000) { viewModel.secretIdToShow.first { it == "secret1" } }
        assertEquals("secret1", secretId)
    }

    @Test
    fun `RECOVER_DECLINED sends decline completion, clears secret and shows notification`() = runBlocking {
        val socketHandler = FakeMainScreenSocketHandler()
        val appManager = FakeMainScreenAppManager(
            findClaimResult = ClaimModel(
                claimId = "claim1",
                sender = "senderDevice",
                distributionType = DistributionType.RECOVER,
                receivers = null,
            )
        )
        val alertCoordinator = FakeAlertCoordinator()
        val viewModel = MainScreenViewModel(
            socketHandler = socketHandler,
            metaSecretAppManager = appManager,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            screenMetricsProvider = FakeScreenMetricsProvider(),
            vaultStatsProvider = FakeVaultStatsProvider(),
            alertCoordinator = alertCoordinator,
        )

        socketHandler.emitActionType(SocketActionModel.RECOVER_DECLINED("secret1"))

        awaitCondition { appManager.sendDeclineCompletionCalls.isNotEmpty() }
        assertEquals("claim1", appManager.sendDeclineCompletionCalls.first())
        awaitCondition { alertCoordinator.showRecoverDeclinedNotificationCalls > 0 }
        assertEquals(null, viewModel.secretIdToShow.value)
    }
}

private class FakeMainScreenSocketHandler : MetaSecretSocketHandlerInterface {
    private val _socketActionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    override val socketActionType: StateFlow<SocketActionModel> = _socketActionType

    private val _socketActions = MutableSharedFlow<SocketActionModel>(extraBufferCapacity = 1)
    override val socketActions: SharedFlow<SocketActionModel> = _socketActions

    override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) = Unit
    override fun pausePolling() = Unit
    override fun resumePolling() = Unit
    override fun setProcessingSecretName(secretName: String) = Unit
    override fun resetReadyToRecoverDedup(claimId: String?) = Unit

    fun emitActionType(action: SocketActionModel) {
        _socketActionType.value = action
    }

    suspend fun emitAction(action: SocketActionModel) {
        _socketActions.emit(action)
    }
}

private class FakeMainScreenAppManager(
    private val secretsInVault: List<SecretApiModel>? = null,
    private val findClaimResult: ClaimModel? = null,
) : MetaSecretAppManagerInterface {
    val sendDeclineCompletionCalls = mutableListOf<String>()

    override suspend fun initWithSavedKey(): InitResult = InitResult.Error("unused")
    override suspend fun checkAuth(): AuthState = AuthState.NOT_YET_COMPLETED
    override suspend fun getStateModel(): AppStateModel? = null
    override suspend fun getVaultFullInfoModel(): VaultFullInfo? = null
    override suspend fun getJoinRequestsCount(): Int? = null
    override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? = null
    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? = null
    override suspend fun getUserDataBy(deviceId: String): UserData? = null
    override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? = null
    override suspend fun findClaim(secretId: String): ClaimModel? = findClaimResult
    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? = null
    override suspend fun acceptRecover(claimId: String?): AppStateModel? = null
    override suspend fun declineRecover(claimId: String?): AppStateModel? = null
    override suspend fun sendDeclineCompletion(claimId: String?) {
        claimId?.let { sendDeclineCompletionCalls += it }
    }
    override suspend fun showRecovered(secretModel: SecretModel): String? = null
    override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<SecretApiModel>? = secretsInVault
}
