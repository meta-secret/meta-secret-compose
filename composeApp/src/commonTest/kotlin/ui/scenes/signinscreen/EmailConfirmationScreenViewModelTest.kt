package ui.scenes.signinscreen

import core.BiometricAuthenticatorInterface
import core.DebugLoggerInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.metaSecretCore.AppStateResult
import core.metaSecretCore.InitResult
import core.metaSecretCore.MemberState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.metaSecretCore.OutsiderState
import core.metaSecretCore.PrepareSignUpResult
import core.metaSecretCore.VaultAvailability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeStringProvider

class EmailConfirmationScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var notificationCoordinator: FakeNotificationCoordinator

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        notificationCoordinator = FakeNotificationCoordinator()
        startKoin {
            modules(
                module {
                    single<DebugLoggerInterface> { FakeDebugLogger() }
                    single<NotificationCoordinatorInterface> { notificationCoordinator }
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
    fun `continue clicked on available vault generates master key and signs up`() = runTest(dispatcher) {
        val keyChain = FakeKeyChain()
        val keyValueStorage = FakeKeyValueStorage()
        val socketHandler = TestSocketHandler()
        val appManager = TestAppManager(initResult = InitResult.Success("ok"))
        val stateResolver = TestStateResolver(
            prepareResult = PrepareSignUpResult(VaultAvailability.AVAILABLE, null),
            continueResult = AppStateResult(MemberState(), null)
        )
        val core = TestMetaSecretCore()

        val viewModel = createViewModel(
            keyChain = keyChain,
            keyValueStorage = keyValueStorage,
            socketHandler = socketHandler,
            appManager = appManager,
            stateResolver = stateResolver,
            core = core,
            vaultName = "alice@example.com"
        )
        advanceUntilIdle()

        viewModel.handle(EmailConfirmationViewEvents.ContinueClicked)
        advanceUntilIdle()

        assertEquals(1, core.generateMasterKeyCalls)
        assertEquals("master-key", keyChain.getString("master_key"))
        assertEquals(1, stateResolver.prepareCalls)
        assertEquals(1, stateResolver.continueCalls)
        assertEquals(EmailConfirmationNavigationEvent.MainScreen, viewModel.navigationEvent.value)
        assertFalse(viewModel.showJoinDecision.value)
        assertFalse(viewModel.showJoinPending.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `continue clicked on occupied vault shows join decision`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            keyChain = FakeKeyChain(),
            keyValueStorage = FakeKeyValueStorage(),
            socketHandler = TestSocketHandler(),
            appManager = TestAppManager(initResult = InitResult.Success("ok")),
            stateResolver = TestStateResolver(
                prepareResult = PrepareSignUpResult(VaultAvailability.EXISTS, null)
            ),
            core = TestMetaSecretCore(),
            vaultName = "alice@example.com"
        )
        advanceUntilIdle()

        viewModel.handle(EmailConfirmationViewEvents.ContinueClicked)
        advanceUntilIdle()

        assertTrue(viewModel.showJoinDecision.value)
        assertFalse(viewModel.showJoinPending.value)
        assertFalse(viewModel.isLoading.value)
        assertTrue(notificationCoordinator.successMessages.isNotEmpty())
    }

    @Test
    fun `join accepted after pending state navigates to main`() = runTest(dispatcher) {
        val socketHandler = TestSocketHandler()
        val viewModel = createViewModel(
            keyChain = FakeKeyChain(),
            keyValueStorage = FakeKeyValueStorage(),
            socketHandler = socketHandler,
            appManager = TestAppManager(initResult = InitResult.Success("ok")),
            stateResolver = TestStateResolver(
                prepareResult = PrepareSignUpResult(VaultAvailability.AVAILABLE, null),
                continueResult = AppStateResult(OutsiderState(), null)
            ),
            core = TestMetaSecretCore(),
            vaultName = "alice@example.com"
        )
        advanceUntilIdle()

        viewModel.handle(EmailConfirmationViewEvents.ContinueClicked)
        advanceUntilIdle()
        assertTrue(viewModel.showJoinPending.value)

        socketHandler.emit(SocketActionModel.JOIN_REQUEST_ACCEPTED)
        advanceUntilIdle()

        assertEquals(EmailConfirmationNavigationEvent.MainScreen, viewModel.navigationEvent.value)
    }

    @Test
    fun `start over clears join state and returns to sign in`() = runTest(dispatcher) {
        val keyChain = FakeKeyChain()
        val keyValueStorage = FakeKeyValueStorage().apply {
            cachedDeviceId = "device-1"
            cachedVaultName = "vault-1"
        }
        val socketHandler = TestSocketHandler()
        val stateResolver = TestStateResolver(
            prepareResult = PrepareSignUpResult(VaultAvailability.EXISTS, null)
        )
        val viewModel = createViewModel(
            keyChain = keyChain,
            keyValueStorage = keyValueStorage,
            socketHandler = socketHandler,
            appManager = TestAppManager(initResult = InitResult.Success("ok")),
            stateResolver = stateResolver,
            core = TestMetaSecretCore(),
            vaultName = "alice@example.com"
        )
        advanceUntilIdle()

        viewModel.handle(EmailConfirmationViewEvents.ContinueClicked)
        advanceUntilIdle()
        viewModel.handle(EmailConfirmationViewEvents.StartOver)
        advanceUntilIdle()

        assertEquals(1, stateResolver.clearCalls)
        assertEquals(1, keyChain.clearAllCalls)
        assertEquals(null, keyValueStorage.cachedDeviceId)
        assertEquals(null, keyValueStorage.cachedVaultName)
        assertEquals(EmailConfirmationNavigationEvent.BackToSignIn, viewModel.navigationEvent.value)
        assertTrue(socketHandler.lastExclude?.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE) == true)
    }

    private fun createViewModel(
        keyChain: KeyChainInterface,
        keyValueStorage: KeyValueStorageInterface,
        socketHandler: TestSocketHandler,
        appManager: MetaSecretAppManagerInterface,
        stateResolver: MetaSecretStateResolverInterface,
        core: MetaSecretCoreInterface,
        vaultName: String,
    ): EmailConfirmationScreenViewModel {
        return EmailConfirmationScreenViewModel(
            screenMetricsProvider = FakeScreenMetricsProvider(),
            metaSecretAppManager = appManager,
            metaSecretCore = core,
            metaSecretStateResolver = stateResolver,
            keyChainManager = keyChain,
            keyValueStorage = keyValueStorage,
            socketHandler = socketHandler,
            biometricAuthenticator = FakeBiometricAuthenticator(),
            stringProvider = FakeStringProvider(),
            vaultName = vaultName,
        )
    }
}

private class TestStateResolver(
    private val prepareResult: PrepareSignUpResult,
    private val continueResult: AppStateResult = AppStateResult(MemberState(), null),
) : MetaSecretStateResolverInterface {
    var prepareCalls = 0
    var continueCalls = 0
    var clearCalls = 0

    override suspend fun prepareSignUp(vaultName: String): PrepareSignUpResult {
        prepareCalls += 1
        return prepareResult
    }

    override suspend fun continueSignUp(): AppStateResult {
        continueCalls += 1
        return continueResult
    }

    override fun clearPreparedSignUp() {
        clearCalls += 1
    }
}

private class TestSocketHandler : MetaSecretSocketHandlerInterface {
    private val state = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
    private val actions = MutableSharedFlow<SocketActionModel>()

    var lastAdd: List<SocketRequestModel>? = null
    var lastExclude: List<SocketRequestModel>? = null

    override val socketActionType: StateFlow<SocketActionModel> = state
    override val socketActions: SharedFlow<SocketActionModel> = actions

    override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) {
        lastAdd = add
        lastExclude = exclude
    }

    override fun pausePolling() = Unit
    override fun resumePolling() = Unit
    override fun setProcessingSecretName(secretName: String) = Unit
    override fun resetReadyToRecoverDedup(claimId: String?) = Unit

    fun emit(action: SocketActionModel) {
        state.value = action
    }
}

private class TestAppManager(
    private val initResult: InitResult,
) : MetaSecretAppManagerInterface {
    var initCalls = 0

    override suspend fun initWithSavedKey(): InitResult {
        initCalls += 1
        return initResult
    }

    override suspend fun checkAuth() = core.metaSecretCore.AuthState.NOT_YET_COMPLETED
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

private class TestMetaSecretCore : MetaSecretCoreInterface {
    var generateMasterKeyCalls = 0

    override fun generateMasterKey(): String {
        generateMasterKeyCalls += 1
        return """{"success":true,"message":"master-key","error":null}"""
    }

    override fun initAppManager(masterKey: String): String = """{"success":true,"message":"ok","error":null}"""
    override fun getAppState(): String = "{}"
    override fun generateUserCreds(vaultName: String): String = "{}"
    override fun signUp(): String = "{}"
    override fun updateMembership(candidate: UserData, actionUpdate: String): String = "{}"
    override fun splitSecret(secretName: String, secret: String): String = "{}"
    override fun findClaim(secretId: String): String = "{}"
    override fun recover(secretId: String): String = "{}"
    override fun acceptRecover(claimId: String): String = "{}"
    override fun declineRecover(claimId: String): String = "{}"
    override fun sendDeclineCompletion(claimId: String): String = "{}"
    override fun showRecovered(secretId: String): String = "{}"
}
