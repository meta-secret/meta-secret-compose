package ui.scenes.signinscreen

import core.AppStateCacheProviderInterface
import core.BackupCoordinatorInterface
import core.BiometricAuthenticatorInterface
import core.DebugLoggerInterface
import core.GoogleEmailAuthResult
import core.GoogleEmailRequesterInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
import models.appInternalModels.EmailProvider
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeBiometricAuthenticator
import testutils.FakeMetaSecretCore
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeScreenMetricsProvider
import testutils.FakeStringProvider

class SignInScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var testLogger: CapturingDebugLogger

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        testLogger = CapturingDebugLogger()
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
    fun `google selection requests email and logs selected account`() = runTest(dispatcher) {
        val requester = FakeGoogleEmailRequester(
            result = GoogleEmailAuthResult.Success("alice@example.com")
        )
        val viewModel = createViewModel(requester)

        viewModel.handle(SignInViewEvents.SelectEmailProvider(EmailProvider.GOOGLE))
        advanceUntilIdle()

        assertEquals(1, requester.calls)
        assertTrue(
            testLogger.entries.any { entry ->
                entry.message == LogTag.SignInVM.Message.GoogleAuthSuccess &&
                    entry.extra == "alice@example.com" &&
                    entry.success == true
            }
        )
    }

    private fun createViewModel(
        googleEmailRequester: GoogleEmailRequesterInterface,
    ): SignInScreenViewModel {
        return SignInScreenViewModel(
            screenMetricsProvider = FakeScreenMetricsProvider(),
            metaSecretAppManager = FakeSignInAppManager(),
            metaSecretCore = FakeMetaSecretCore(),
            metaSecretStateResolver = FakeSignInStateResolver(),
            keyChainManager = FakeKeyChain(),
            keyValueStorage = FakeKeyValueStorage(),
            socketHandler = FakeSocketHandler(),
            biometricAuthenticator = FakeBiometricAuthenticator(),
            googleEmailRequester = googleEmailRequester,
            stringProvider = FakeStringProvider(),
        )
    }
}

private class FakeGoogleEmailRequester(
    private val result: GoogleEmailAuthResult,
) : GoogleEmailRequesterInterface {
    var calls = 0

    override suspend fun requestGoogleEmail(): GoogleEmailAuthResult {
        calls += 1
        return result
    }
}

private class CapturingDebugLogger : DebugLoggerInterface {
    data class Entry(
        val message: LogTag.Message<*>,
        val extra: String?,
        val success: Boolean?,
    )

    val entries = mutableListOf<Entry>()

    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
        entries += Entry(message = message, extra = extra, success = success)
    }

    override fun setLoggerVisibility() = Unit
    override fun testInfo() = Unit
    override fun setBackupDbExists(exists: Boolean) = Unit
    override fun setAppManagerCreated(created: Boolean) = Unit
    override fun setMasterKeyGenerated(generated: Boolean) = Unit
    override fun setVaultState(state: String?) = Unit
    override fun setDeviceId(deviceId: String?) = Unit
    override fun setClaimsStats(joinRequestsCount: Int, pendingClaimsCount: Int, sentClaimsCount: Int, deliveredClaimsCount: Int) = Unit
    override fun setOuterLoggerVisibility(isVisible: Boolean) = Unit
}

private class FakeSignInAppManager : MetaSecretAppManagerInterface {
    override suspend fun initWithSavedKey(): InitResult = InitResult.Error("no master key")
    override suspend fun checkAuth(): AuthState = AuthState.NOT_YET_COMPLETED
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

private class FakeSignInStateResolver : MetaSecretStateResolverInterface {
    override suspend fun prepareSignUp(vaultName: String) = core.metaSecretCore.PrepareSignUpResult(null, null)
    override suspend fun continueSignUp() = core.metaSecretCore.AppStateResult(appState = null, error = null)
    override fun clearPreparedSignUp() = Unit
}

private class FakeSocketHandler : MetaSecretSocketHandlerInterface {
    private val state = MutableStateFlow(SocketActionModel.NONE)
    private val actions = MutableSharedFlow<SocketActionModel>()

    override val socketActionType: StateFlow<SocketActionModel> = state
    override val socketActions: SharedFlow<SocketActionModel> = actions

    override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) = Unit
    override fun pausePolling() = Unit
    override fun resumePolling() = Unit
    override fun setProcessingSecretName(secretName: String) = Unit
    override fun resetReadyToRecoverDedup(claimId: String?) = Unit
}
