package ui.liveboard

import core.*
import core.errors.ErrorMapper
import core.metaSecretCore.*
import core.metaSecretCore.liveboard.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import models.apiModels.*
import models.appInternalModels.SocketRequestModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.*

internal class RootPlatform : LiveboardPlatformPort {
    internal class Attachment(val owner: Any, val back: () -> Boolean) : LiveboardRootAttachment {
        var active = true
        override fun isCurrent() = active
        override fun close() { active = false }
    }
    val attachments = mutableListOf<Attachment>()
    private val clipboard = Channel<CompletableDeferred<String?>>(Channel.UNLIMITED)
    val copies = mutableListOf<String>()
    var recoverObservationOn = false
    val recoverObservationRecords = MutableStateFlow<List<LiveboardRecoverObservationRecord>>(emptyList())
    val recoverObservationAttempts = MutableStateFlow(0)
    override val recoverObservationEnabled: Boolean get() = recoverObservationOn
    override fun emitRecoverObservation(record: LiveboardRecoverObservationRecord) {
        recoverObservationAttempts.update { it + 1 }
        observationFailure?.let { throw it }
        recoverObservationRecords.update { it + record }
    }
    var observationEnabled = false
    var observationFailure: Throwable? = null
    val observationRecords = MutableStateFlow<List<LiveboardDecisionObservationRecord>>(emptyList())
    val observationAttempts = MutableStateFlow(0)
    override val decisionObservationEnabled: Boolean get() = observationEnabled
    override fun emitDecisionObservation(record: LiveboardDecisionObservationRecord) {
        observationAttempts.update { it + 1 }
        observationFailure?.let { throw it }
        observationRecords.update { it + record }
    }
    val showObservationRecords = MutableStateFlow<List<LiveboardShowObservationRecord>>(emptyList())
    val allShowObservationRecords = MutableStateFlow<List<LiveboardShowObservationRecord>>(emptyList())
    val postDeliveryObservationRecords = MutableStateFlow<List<LiveboardShowObservationRecord>>(emptyList())
    val unrecognizedShowObservationRecords = MutableStateFlow<List<LiveboardShowObservationRecord>>(emptyList())
    val showObservationAttempts = MutableStateFlow(0)
    override fun emitShowObservation(record: LiveboardShowObservationRecord) {
        showObservationAttempts.update { it + 1 }
        observationFailure?.let { throw it }
        allShowObservationRecords.update { it + record }
        when (record.variant) {
            ShowObservationVariant.SHOW_V1 -> showObservationRecords.update { it + record }
            ShowObservationVariant.POST_DELIVERY_V1 -> postDeliveryObservationRecords.update { it + record }
            ShowObservationVariant.CONTINUITY_V1 -> Unit
            ShowObservationVariant.UNRECOGNIZED -> unrecognizedShowObservationRecords.update { it + record }
        }
    }
    override fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment {
        attachments.lastOrNull()?.active = false
        return Attachment(owner, onBack).also { attachments.add(it) }
    }
    override suspend fun readClipboard(): String? = CompletableDeferred<String?>().let {
        clipboard.send(it); it.await()
    }
    override fun copyText(value: String) { copies.add(value) }
    suspend fun takePaste() = clipboard.receive()
    fun clear() { copies.clear(); while (true) clipboard.tryReceive().getOrNull()?.complete(null) ?: break }
}

internal data class RootFollow(val add: List<SocketRequestModel>, val exclude: List<SocketRequestModel>)

/** SAME real services; only existing Core/key/OS/dispatch ports are controlled. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class LiveboardRootTestFixture {
    val main = NativeReentrantDispatcher()
    val platform = RootPlatform()
    val trace = NativeRegistrationTrace()
    val core = LiveboardFeatureCore(trace)
    val keys = NativeRegistrationKeys(trace)
    val auth = FeatureBiometric()
    val privacy = LiveboardPrivacyOwner()
    val notifications = NotificationCoordinator(privacy)
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    private val nativeEnvironment = NativeReadEnvironment(Dispatchers.IO, main)
    val cache = AppStateCacheProvider(logger, storage, main)
    val initialization = NativeInitializationOwner(core, nativeEnvironment)
    val operations = NativeOperationPort(initialization, nativeEnvironment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter),
        NativeReadRuntime(initialization, nativeEnvironment, privacy))
    val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, nativeEnvironment, reads)
    val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads,
        NativeManagerRuntime(lifecycle, operations))
    val socket = MetaSecretSocketHandler(NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)),
        NativeSocketPresentation(cache, strings), NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, main, main), reads)
    val follows = MutableStateFlow<List<RootFollow>>(emptyList())
    private val socketPort = object : MetaSecretSocketHandlerInterface by socket {
        override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) {
            follows.update { it + RootFollow(add.orEmpty().toList(), exclude.orEmpty().toList()) }
            socket.actionsToFollow(add, exclude)
        }
    }
    val checked = NativeLiveboardOperations(LiveboardCoreAdapter(core), operations, nativeEnvironment)
    val factory = LiveboardSessionFactory(checked, reads, operations, nativeEnvironment,
        LiveboardClientPorts(privacy, auth, socketPort, notifications, strings))
    private val roots = mutableListOf<LiveboardRootCoordinator>()
    private val sessions = mutableSetOf<LiveboardSession>()
    private val jobs = mutableListOf<Job>()
    private val gates = mutableListOf<NativeTransitionGate>()
    private val controlled = mutableListOf<NativeCheckedIo>()
    private val testUi = mutableListOf<TestDispatcher>()
    private var generation = 0
    init {
        Dispatchers.setMain(main)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@LiveboardRootTestFixture.logger }
            single<NotificationCoordinatorInterface> { this@LiveboardRootTestFixture.notifications }
        }) }
    }
    fun root(ui: CoroutineDispatcher = main, io: CoroutineDispatcher = Dispatchers.IO): LiveboardRootCoordinator {
        if (io is NativeCheckedIo) controlled.add(io)
        if (io is TestDispatcher) testUi.add(io)
        if (ui is NativeCheckedIo) controlled.add(ui)
        if (ui is TestDispatcher) testUi.add(ui)
        val info = object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "fixture"
            override fun getDeviceMake() = "fixture"
            override fun getDeviceId() = "device-a"
        }
        return LiveboardRootCoordinator(LiveboardRootDependencies(factory, reads,
            NativeReadEnvironment(io, ui), LiveboardRootServices(reset, socketPort, info, notifications), platform))
            .also { roots.add(it) }
    }
    fun <T : Job> track(job: T) = job.also { jobs.add(it) }
    fun hold(route: String, failure: Exception? = null) = NativeTransitionGate(failure).also {
        gates.add(it); core.hold(route, it)
    }
    suspend fun start() {
        keys.saveString("master_key", "root-key-${generation++}")
        assertIs<InitResult.Success>(manager.initWithSavedKey()); proof()
    }
    suspend fun proof(): NativeReadFrame {
        val expected = assertNotNull(initialization.currentScope())
        val target = assertNotNull(reads.requireFreshRead(expected))
        manager.getStateModel() // A newer real attempt may win legacy consumption; errors still propagate.
        val proof = target.completion.await()
        assertEquals(expected, proof.scope); assertEquals(NativeReadOutcome.SUCCESS, proof.outcome)
        assertTrue(target in proof.coveredRequirements)
        val current = assertNotNull(reads.frames.value)
        assertEquals(expected, initialization.currentScope()); assertEquals(expected, current.scope)
        assertEquals(NativeReadOutcome.SUCCESS, current.outcome); assertTrue(current.attempt >= proof.attempt)
        return current
    }
    suspend fun content(root: LiveboardRootCoordinator): LiveboardRootContent =
        assertNotNull(root.state.first { it.content != null }.content).also { sessions.add(it.session) }
    suspend fun session(root: LiveboardRootCoordinator): LiveboardSession {
        val session = content(root).session
        idle() // Real mount-triggered synchronization settles before a baseline live action.
        return session
    }
    suspend fun newOwner(): NativeReadFrame {
        assertTrue(assertNotNull(reset.reset(initialization.capture())).value)
        core.fresh(); auth.held = false; start()
        return assertNotNull(reads.frames.value)
    }
    suspend fun idle() { cache.synchronization.first { !it.loading && it.activeRefreshes == 0 } }
    fun clears() = trace.count("key.clear")
    fun currentOwnerFinished() = initialization.isCurrent(initialization.capture())
    fun waitingCreatedSecret() {
        val parsed = AppStateModel.fromJson(core.state.value)
        val metadata = (parsed.getVaultFullInfo() as VaultFullInfo.Member).member.liveboardState!!.jsonObject.toMutableMap()
        metadata["secrets"] = JsonArray((metadata.getValue("secrets") as JsonArray).map { element ->
            JsonObject(element.jsonObject.toMutableMap().apply {
                if (get("secretId") == JsonPrimitive("pass-b")) {
                    put("primaryAction", JsonPrimitive("waiting")); put("readiness", JsonPrimitive("waiting"))
                    put("recoveryClaimId", JsonPrimitive(FeatureIds.claim))
                }
            })
        })
        core.state.value = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
    }
    fun recoverDeclined(wrong: String? = null) {
        val parsed = AppStateModel.fromJson(core.state.value)
        val member = (parsed.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = member.liveboardState!!.jsonObject.toMutableMap()
        metadata["operations"] = JsonArray(listOf(JsonObject(mapOf(
            "kind" to JsonPrimitive("recover"),
            "requestId" to JsonPrimitive(if (wrong == "claim") "wrong-claim" else FeatureIds.claim),
            "requesterId" to JsonPrimitive(if (wrong == "requester") "device-b" else "device-a"),
            "secretId" to JsonPrimitive("pass-b"), "clientStatus" to JsonPrimitive("declined"),
            "eligiblePeerIds" to JsonArray(emptyList()), "canApprove" to JsonPrimitive(false),
            "canDecline" to JsonPrimitive(false), "outcome" to JsonPrimitive("declined"),
            "resolutionSource" to JsonPrimitive("verified"), "readiness" to JsonPrimitive("waiting"),
        ))))
        core.state.value = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        assertNotNull(models.apiModels.liveboard.LiveboardStateDecoder.decode(core.state.value))
    }
    fun removeDeclined(wrong: String? = null) {
        val parsed = AppStateModel.fromJson(core.state.value)
        val metadata = (parsed.getVaultFullInfo() as VaultFullInfo.Member).member.liveboardState!!.jsonObject.toMutableMap()
        metadata["operations"] = JsonArray(listOf(JsonObject(mapOf(
            "kind" to JsonPrimitive("removeDevice"),
            "requestId" to JsonPrimitive(if (wrong == "request") FeatureIds.join else FeatureIds.remove),
            "requesterId" to JsonPrimitive("device-a"),
            "targetDeviceId" to JsonPrimitive(if (wrong == "target") "device-c" else "device-b"),
            "clientStatus" to JsonPrimitive("declined"), "eligiblePeerIds" to JsonArray(emptyList()),
            "canApprove" to JsonPrimitive(false), "canDecline" to JsonPrimitive(false),
            "outcome" to JsonPrimitive("declined"), "resolutionSource" to JsonPrimitive("verified"),
            "readiness" to JsonPrimitive("done"),
        ))))
        core.state.value = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        assertNotNull(models.apiModels.liveboard.LiveboardStateDecoder.decode(core.state.value))
    }
    fun removedState(declined: Boolean): String {
        val device = DeviceData("device-a", "Current phone", "iphone", keys = OpenBox("d", "t"))
        val state = if (declined) State.Vault(VaultFullInfo.Outsider(
            UserDataOutsider(UserData(device, "parser-vault"), UserDataOutsiderStatus.DECLINED))) else State.Local(device)
        return JsonConfig.json.encodeToString(AppStateModel(Message(state), true))
    }
    suspend fun publish(task: NativeHeldPublication, scope: CoroutineScope) =
        track(scope.launch(Dispatchers.IO) { task.publish() }).join()
    suspend fun finishControlled(ui: NativeCheckedIo) {
        ui.resumeInline(); withContext(Dispatchers.IO) { ui.releaseAll() }
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(8_000) {
            gates.forEach { it.release.complete(Unit) }
            controlled.forEach { finishControlled(it) }
            roots.forEach { root -> root.state.value.content?.session?.let { sessions.add(it) }; root.close() }
            testUi.forEach { it.scheduler.runCurrent() }
            jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
            sessions.forEach { it.awaitSettled(); it.dispose() }
            socket.pauseRefreshesAndAwait(); socket.onAppBackground()
            notifications.dismiss(); auth.clear(); platform.clear(); stopKoin(); Dispatchers.resetMain()
        }
    }
}

internal fun rootCheck(errors: MutableList<String>, label: String, assertion: () -> Unit) {
    try { assertion() } catch (failure: AssertionError) { errors.add("$label: ${failure.message}") }
}
