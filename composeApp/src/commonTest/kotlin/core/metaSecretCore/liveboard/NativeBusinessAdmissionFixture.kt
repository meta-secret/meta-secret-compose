package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.NoopMetaSecretSocketClient
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.ClaimObject
import models.apiModels.ClaimStatusInfo
import models.apiModels.ClientStatus
import models.apiModels.DeviceData
import models.apiModels.DistClaimId
import models.apiModels.DistributionType
import models.apiModels.JsonConfig
import models.apiModels.OpenBox
import models.apiModels.PassId
import models.apiModels.SearchClaimMessage
import models.apiModels.SearchClaimModel
import models.apiModels.SsClaims
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.VaultFullInfo
import models.appInternalModels.SecretModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent
import ui.scenes.profilescreen.ProfileScreenViewModel

internal data class NativeBusinessAction(
    val name: String,
    val arguments: List<String>,
    val invoke: suspend (MetaSecretAppManager) -> Any?,
)

internal fun nativeBusinessActions(): List<NativeBusinessAction> {
    val user = UserData(
        DeviceData("device-b", "Other device", "Android", keys = OpenBox("d", "t")), "parser-vault",
    )
    val userJson = JsonConfig.json.encodeToString(UserData.serializer(), user)
    return listOf(
        NativeBusinessAction("update", listOf(userJson, "approve")) { it.updateMember(user, "approve") },
        NativeBusinessAction("delete", listOf("opaque-pass")) { it.deleteSecret("opaque-pass") },
        NativeBusinessAction("decideDelete", listOf("{\"requestId\":\"exact-uuid\",\"accepted\":false}")) {
            it.decideDeleteSecret("exact-uuid", false)
        },
        NativeBusinessAction("remove", listOf("device-b")) { it.removeMember("device-b") },
        NativeBusinessAction("approveRemove", listOf("device-b")) { it.approveRemoveMember("device-b") },
        NativeBusinessAction("declineRemove", listOf("device-b")) { it.declineRemoveMember("device-b") },
        NativeBusinessAction("split", listOf("secret-a", "fixture-value")) { it.splitSecret(SecretModel("secret-a", "fixture-value")) },
        NativeBusinessAction("find", listOf("secret-a")) { it.findClaim("secret-a") },
        NativeBusinessAction("recover", listOf("secret-a")) { it.recover(SecretModel("secret-a", null)) },
        NativeBusinessAction("accept", listOf("claim-a")) { it.acceptRecover("claim-a") },
        NativeBusinessAction("decline", listOf("claim-a")) { it.declineRecover("claim-a") },
        NativeBusinessAction("completion", listOf("claim-a")) { it.sendDeclineCompletion("claim-a") },
        NativeBusinessAction("showRecovered", listOf("claim-a")) { it.showRecovered(SecretModel("secret-a", null, "claim-a")) },
        NativeBusinessAction("showLocal", listOf("secret-a")) { it.showRecovered(SecretModel("secret-a", null)) },
    )
}

/** Controls only actual synchronous Core boundaries, never native ownership or proof. */
internal class NativeBusinessCore : MetaSecretCoreInterface by FakeMetaSecretCore() {
    val calls = MutableStateFlow<List<NativeCall>>(emptyList())
    val inits = MutableStateFlow(0)
    val reads = MutableStateFlow(0)
    var responseOverrides = emptyMap<String, String>()
    var claimId = "claim-a"
    var secretName = "secret-a"
    var gatedPort: String? = null
    var gate: NativeTransitionGate? = null
    private val common = "{\"success\":true}"
    private val revealed = "{\"success\":true,\"message\":{\"secret\":\"fixture-value\"}}"

    private fun claim(status: ClientStatus) = ClaimObject(
        distClaimId = DistClaimId(claimId, PassId("opaque-pass", secretName)),
        distributionType = DistributionType.RECOVER, id = claimId, receivers = listOf("device-b"),
        sender = "device-a", status = ClaimStatusInfo(emptyMap()), vaultName = "parser-vault", clientStatus = status,
    )
    private fun state(): String {
        val base = AppStateModel.fromJson(LiveboardNativeFixtures.member(null), FakeDebugLogger())
        val member = (base.getVaultFullInfo() as VaultFullInfo.Member).member
        val updated = base.copy(message = base.message!!.copy(
            state = State.Vault(VaultFullInfo.Member(member.copy(ssClaims = SsClaims(mapOf(claimId to claim(ClientStatus.PENDING)))))),
        ))
        return JsonConfig.json.encodeToString(AppStateModel.serializer(), updated)
    }
    private fun search() = JsonConfig.json.encodeToString(
        SearchClaimModel.serializer(), SearchClaimModel(true, SearchClaimMessage(claim(ClientStatus.ACCEPTED))),
    )
    private fun call(name: String, response: String, vararg arguments: String): String = runBlocking {
        calls.update { it + NativeCall(name, arguments.toList()) }
        if (gatedPort == name) gate?.also { gate = null }?.hold()
        responseOverrides[name] ?: response
    }
    override fun initAppManager(masterKey: String): String { inits.update { it + 1 }; return "ok" }
    override fun getAppState(): String { reads.update { it + 1 }; return state() }
    override fun updateMembership(candidate: UserData, actionUpdate: String) = call("update", common,
        JsonConfig.json.encodeToString(UserData.serializer(), candidate), actionUpdate)
    override fun deleteSecret(secretId: String) = call("delete", common, secretId)
    override fun decideDeleteSecret(requestJson: String) = call("decideDelete", common, requestJson)
    override fun removeMember(targetDeviceId: String) = call("remove", common, targetDeviceId)
    override fun approveRemoveMember(targetDeviceId: String) = call("approveRemove", common, targetDeviceId)
    override fun declineRemoveMember(targetDeviceId: String) = call("declineRemove", common, targetDeviceId)
    override fun splitSecret(secretName: String, secret: String) = call("split", common, secretName, secret)
    override fun findClaim(secretId: String) = call("find", search(), secretId)
    override fun recover(secretId: String) = call("recover", common, secretId)
    override fun acceptRecover(claimId: String) = call("accept", state(), claimId)
    override fun declineRecover(claimId: String) = call("decline", state(), claimId)
    override fun sendDeclineCompletion(claimId: String) = call("completion", common, claimId)
    override fun showRecovered(claimId: String) = call("showRecovered", revealed, claimId)
    override fun showLocalSecret(secretId: String) = call("showLocal", revealed, secretId)
}

/** Immediate test key IO is nonblocking; admitted clear effects cannot hide in a queue. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeBusinessAdmissionFixture(io: CoroutineDispatcher? = null) {
    val immediate = NativeReentrantDispatcher()
    val caller = NativePublicationDispatcher()
    val core = NativeBusinessCore()
    val keys = NativeTransitionKeys()
    val notifications = FakeNotificationCoordinator()
    val resetEntered = CompletableDeferred<Unit>()
    private val logger = object : DebugLoggerInterface by FakeDebugLogger() {
        override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
            if (message == LogTag.ProfileVM.Message.ResetAllData) resetEntered.complete(Unit)
        }
    }
    private val storage = FakeKeyValueStorage()
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    private val environment = NativeReadEnvironment(io ?: immediate, immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    private val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads, NativeManagerRuntime(lifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, immediate, Dispatchers.IO), reads,
    )
    private val jobs = mutableListOf<Job>()
    private val gates = mutableListOf<NativeTransitionGate>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()

    init {
        Dispatchers.setMain(immediate)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@NativeBusinessAdmissionFixture.logger }
            single<NotificationCoordinatorInterface> { notifications }
        }) }
    }
    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun gate() = NativeTransitionGate().also { gates.add(it) }
    fun hold(port: String) = gate().also { core.gatedPort = port; core.gate = it }
    suspend fun initialize() { assertIs<InitResult.Success>(manager.initWithSavedKey()) }
    suspend fun profile(): ProfileScreenViewModel {
        val profile = ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "device-a"
        }, socket, FakeVaultStatsProvider(), reset).also { profiles.add(it) }
        awaitSocketIdle()
        return profile
    }
    fun requestReset(profile: ProfileScreenViewModel) { profile.handle(ProfileEvents.ResetAllData) }
    suspend fun awaitReset(profile: ProfileScreenViewModel) {
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
        assertEquals(1, keys.clears.value)
    }
    suspend fun awaitSocketIdle() { cache.synchronization.first { it.activeRefreshes == 0 && !it.loading } }
    suspend fun freshSuccessor(): NativeReadFrame {
        keys.saveString("master_key", "successor-key")
        initialize()
        val target = assertNotNull(reads.requireFreshRead())
        val state = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertSame(state, frame.state)
        assertSame(state, cache.appState.value)
        assertTrue(target in frame.coveredRequirements)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
        return frame
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val drain = caller.drain(this)
            gates.forEach { it.release.complete(Unit) }
            profiles.forEach { it.viewModelScope.cancel() }
            try {
                jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
                socket.pauseRefreshesAndAwait()
                socket.onAppBackground()
            } finally {
                drain.cancel(); drain.join()
                stopKoin(); Dispatchers.resetMain()
            }
        }
    }
}
