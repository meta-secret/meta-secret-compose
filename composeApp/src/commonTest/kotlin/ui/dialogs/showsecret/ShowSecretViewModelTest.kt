package ui.dialogs.showsecret

import core.DebugLoggerInterface
import core.NotificationCoordinatorInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.ClientStatus
import models.apiModels.CommonResponseModel
import models.apiModels.DistributionType
import models.apiModels.SecretApiModel
import models.apiModels.UserData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeDebugLogger
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ShowSecretViewModelTest {
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

    // findClaim() hops onto the real Dispatchers.IO internally, which StandardTestDispatcher's
    // advanceUntilIdle() cannot track. Tests that exercise that path run on a real dispatcher
    // (Dispatchers.Unconfined) and poll for the resulting state instead.
    private suspend fun awaitCondition(timeoutMs: Long = 8000, intervalMs: Long = 20, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(intervalMs)
        }
    }

    @Test
    fun `parseSecretValue returns seed phrase for 12 words`() {
        val value = (1..12).joinToString(" ") { "word$it" }

        val result = parseSecretValue(value)

        assertEquals(SecretValueType.SEED_PHRASE, result.type)
        assertEquals(12, result.count)
        assertEquals(12, result.words.size)
    }

    @Test
    fun `parseSecretValue returns seed phrase for 24 words with extra spaces`() {
        val value = (1..24).joinToString("   ") { "word$it" }

        val result = parseSecretValue("  $value  ")

        assertEquals(SecretValueType.SEED_PHRASE, result.type)
        assertEquals(24, result.count)
        assertEquals(24, result.words.size)
    }

    @Test
    fun `parseSecretValue returns password for non seed value`() {
        val result = parseSecretValue("my-super-secret-password")

        assertEquals(SecretValueType.PASSWORD, result.type)
        assertEquals(null, result.count)
        assertEquals(0, result.words.size)
    }

    @Test
    fun `two devices use local copy and do not request recovery`() = runTest(dispatcher) {
        val appManager = FakeShowSecretAppManager(
            showRecoveredResult = "s3cr3t",
        )
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 2),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        advanceUntilIdle()

        assertEquals(0, appManager.recoverCalls)
        assertEquals(1, socketHandler.pausePollingCalls)
        assertEquals(1, socketHandler.resumePollingCalls)
    }

    @Test
    fun `no existing claim creates a new recovery claim`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appManager = FakeShowSecretAppManager(findClaimResult = null)
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 3),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        awaitCondition { appManager.recoverCalls > 0 }

        assertEquals(1, appManager.recoverCalls)
        assertEquals(0, appManager.showRecoveredCalls)
    }

    @Test
    fun `pending claim shows the waiting notification and does not create a new claim`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appManager = FakeShowSecretAppManager(
            findClaimResult = ClaimModel(
                claimId = "claim1",
                sender = "senderDevice",
                distributionType = DistributionType.RECOVER,
                receivers = listOf("receiverDevice"),
                clientStatus = ClientStatus.PENDING,
            )
        )
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 3),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        awaitCondition { socketHandler.resumePollingCalls > 0 }

        assertEquals(0, appManager.recoverCalls)
        assertEquals(0, appManager.showRecoveredCalls)
        assertEquals(true, viewModel.isLoading.value)
        assertEquals(1, socketHandler.resumePollingCalls)
    }

    @Test
    fun `accepted claim resumes polling without dispatching showRecovered synchronously`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appManager = FakeShowSecretAppManager(
            findClaimResult = ClaimModel(
                claimId = "claim1",
                sender = "senderDevice",
                distributionType = DistributionType.RECOVER,
                receivers = listOf("receiverDevice"),
                clientStatus = ClientStatus.ACCEPTED,
            )
        )
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 3),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        awaitCondition { socketHandler.resumePollingCalls > 0 }

        assertEquals(0, appManager.recoverCalls)
        assertEquals(0, appManager.showRecoveredCalls)
        assertEquals(1, socketHandler.resumePollingCalls)
    }

    @Test
    fun `declined claim resumes polling without dispatching a new claim`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appManager = FakeShowSecretAppManager(
            findClaimResult = ClaimModel(
                claimId = "claim1",
                sender = "senderDevice",
                distributionType = DistributionType.RECOVER,
                receivers = listOf("receiverDevice"),
                clientStatus = ClientStatus.DECLINED,
            )
        )
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 3),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        awaitCondition { socketHandler.resumePollingCalls > 0 }

        assertEquals(0, appManager.recoverCalls)
        assertEquals(1, socketHandler.resumePollingCalls)
    }

    @Test
    fun `RECOVER_DECLINED for the current secret clears the loader`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appManager = FakeShowSecretAppManager(
            findClaimResult = ClaimModel(
                claimId = "claim1",
                sender = "senderDevice",
                distributionType = DistributionType.RECOVER,
                receivers = listOf("receiverDevice"),
                clientStatus = ClientStatus.PENDING,
            )
        )
        val socketHandler = FakeShowSecretSocketHandler()
        val viewModel = ShowSecretViewModel(
            metaSecretAppManager = appManager,
            vaultStatsProvider = FakeShowSecretVaultStatsProvider(devicesCount = 3),
            socketHandler = socketHandler,
            stringProvider = FakeStringProvider(),
        )

        viewModel.handle(ShowSecretEvents.ShowSecret("Secret1"))
        awaitCondition { viewModel.isLoading.value }
        assertEquals(true, viewModel.isLoading.value)

        socketHandler.emitActionType(SocketActionModel.RECOVER_DECLINED("Secret1"))
        awaitCondition { !viewModel.isLoading.value }

        assertEquals(false, viewModel.isLoading.value)
    }
}

private class FakeShowSecretAppManager(
    private val showRecoveredResult: String? = null,
    private val findClaimResult: ClaimModel? = null,
) : MetaSecretAppManagerInterface {
    var recoverCalls = 0
    var showRecoveredCalls = 0

    override suspend fun initWithSavedKey() = core.metaSecretCore.InitResult.Error("unused")
    override suspend fun checkAuth() = core.metaSecretCore.AuthState.NOT_YET_COMPLETED
    override suspend fun getStateModel(): AppStateModel? = null
    override suspend fun getVaultFullInfoModel(): VaultFullInfo? = null
    override suspend fun getJoinRequestsCount(): Int? = null
    override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? = null
    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? = null
    override suspend fun getUserDataBy(deviceId: String): UserData? = null
    override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? = null
    override suspend fun findClaim(secretId: String): ClaimModel? = findClaimResult
    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? {
        recoverCalls += 1
        return CommonResponseModel(success = true, message = null, error = null)
    }
    override suspend fun acceptRecover(claimId: String?): AppStateModel? = null
    override suspend fun declineRecover(claimId: String?): AppStateModel? = null
    override suspend fun sendDeclineCompletion(claimId: String?) = Unit
    override suspend fun showRecovered(secretModel: SecretModel): String? {
        showRecoveredCalls += 1
        return showRecoveredResult
    }
    override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<SecretApiModel>? = null
}

private class FakeShowSecretSocketHandler : MetaSecretSocketHandlerInterface {
    private val actionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    private val actions = MutableSharedFlow<SocketActionModel>()
    var followCalls = mutableListOf<Pair<List<SocketRequestModel>?, List<SocketRequestModel>?>>()
    var pausePollingCalls = 0
    var resumePollingCalls = 0

    override val socketActionType: StateFlow<SocketActionModel> = actionType
    override val socketActions: SharedFlow<SocketActionModel> = actions

    override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) {
        followCalls += add to exclude
    }

    override fun pausePolling() {
        pausePollingCalls += 1
    }

    override fun resumePolling() {
        resumePollingCalls += 1
    }

    override fun setProcessingSecretName(secretName: String) = Unit
    override fun resetReadyToRecoverDedup(claimId: String?) = Unit

    fun emitActionType(action: SocketActionModel) {
        actionType.value = action
    }
}

private class FakeShowSecretVaultStatsProvider(
    devicesCount: Int,
) : VaultStatsProviderInterface {
    override val secretsCount: StateFlow<Int> = MutableStateFlow(0)
    override val devicesCount: StateFlow<Int> = MutableStateFlow(devicesCount)
    override val vaultName: StateFlow<String?> = MutableStateFlow("test")
    override val joinRequestsCount: StateFlow<Int?> = MutableStateFlow(0)

    override suspend fun refresh() = Unit
}
