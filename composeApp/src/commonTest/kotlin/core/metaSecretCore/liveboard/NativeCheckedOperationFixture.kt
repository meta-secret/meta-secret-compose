package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.NoopMetaSecretSocketClient
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent
import ui.scenes.profilescreen.ProfileScreenViewModel

internal data class NativeCheckedRoute(
    val name: String,
    val arguments: List<String>,
    val raw: String,
    val invoke: suspend (NativeLiveboardOperations, NativeReadScope) -> NativeLiveboardOutcome<*>,
) {
    fun expected(): NativeResult<*> = if (name in listOf("create", "recover", "join", "decideRemove")) {
        LiveboardReceiptDecoder.decodeChecked(raw)
    } else LiveboardReceiptDecoder.decodeProducer(raw)
}

internal fun nativeCheckedRoutes(): List<NativeCheckedRoute> {
    val decision = """ { "accepted":false, "requestId":"${LiveboardNativeFixtures.requestId}" } """
    val original = LiveboardNativeFixtures.producerValue(
        decision = LiveboardProducerDecision.Attempted(LiveboardNativeFixtures.sourceValue("9007199254740994"), "unconfirmed"),
    )
    val reference = LiveboardProducerReference.fromReceipt(original)
    fun checked(kind: String) = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = kind))
    fun producer(kind: String = "removeDevice") = LiveboardNativeFixtures.envelope(
        LiveboardNativeFixtures.producer(kind = kind),
    )
    val continued = LiveboardNativeFixtures.envelope(
        LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision()),
    )
    return listOf(
        NativeCheckedRoute("create", listOf("Same name", "fixture-value\nsecond line"), checked("create")) { p, s ->
            p.clusterDistributionChecked(s, "Same name", "fixture-value\nsecond line")
        },
        NativeCheckedRoute("recover", listOf("pass-b"), checked("recover")) { p, s -> p.recoverChecked(s, "pass-b") },
        NativeCheckedRoute("join", listOf(decision), checked("join")) { p, s -> p.decideJoinRequest(s, decision) },
        NativeCheckedRoute("decideRemove", listOf(decision), checked("removeDevice")) { p, s -> p.decideRemoveMember(s, decision) },
        NativeCheckedRoute("delete", listOf("pass-b"), producer("deleteSecret")) { p, s -> p.deleteSecretChecked(s, "pass-b") },
        NativeCheckedRoute("remove", listOf("device-b"), producer()) { p, s -> p.removeMemberChecked(s, "device-b") },
        NativeCheckedRoute("reconcile", listOf(reference.toJson()), continued) { p, s -> p.reconcileProducerReceipt(s, reference) },
        NativeCheckedRoute("continue", listOf(reference.toJson()), continued) { p, s -> p.continueLocalRemoveChecked(s, reference) },
    )
}

/** Taken tasks stay held while unrelated real key IO runs; cleanup releases every owned task. */
internal class NativeCheckedIo : CoroutineDispatcher() {
    private val queued = Channel<NativeHeldPublication>(Channel.UNLIMITED)
    private val owned = MutableStateFlow<List<NativeHeldPublication>>(emptyList())
    private val inline = MutableStateFlow(true)
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (inline.value) block.run() else {
            val task = NativeHeldPublication(block)
            owned.update { it + task }
            check(queued.trySend(task).isSuccess)
        }
    }
    fun queue() { inline.value = false }
    fun resumeInline() { inline.value = true }
    suspend fun take(): NativeHeldPublication = queued.receive()
    fun releaseAll() { owned.value.forEach { it.publish() } }
}

/** Only actual synchronous Core entry/return is controlled; no ownership or proof is fabricated. */
internal class NativeCheckedCore(trace: NativeRegistrationTrace) : MetaSecretCoreInterface by NativeRegistrationCore(trace) {
    val calls = MutableStateFlow<List<NativeCall>>(emptyList())
    var response = ""
    var gate: NativeTransitionGate? = null
    private fun call(name: String, vararg arguments: String): String = runBlocking {
        calls.update { it + NativeCall(name, arguments.toList()) }
        gate?.also { gate = null }?.hold()
        response
    }
    override fun getAppState(): String = LiveboardNativeFixtures.member()
    override fun clusterDistributionChecked(name: String, value: String) = call("create", name, value)
    override fun recoverChecked(secretId: String) = call("recover", secretId)
    override fun decideJoinRequest(requestJson: String) = call("join", requestJson)
    override fun decideRemoveMember(requestJson: String) = call("decideRemove", requestJson)
    override fun deleteSecretChecked(secretId: String) = call("delete", secretId)
    override fun removeMemberChecked(targetDeviceId: String) = call("remove", targetDeviceId)
    override fun reconcileProducerReceipt(reference: String) = call("reconcile", reference)
    override fun continueLocalRemoveChecked(reference: String) = call("continue", reference)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeCheckedOperationFixture {
    val io = NativeCheckedIo()
    val main = NativeReentrantDispatcher()
    val trace = NativeRegistrationTrace()
    val core = NativeCheckedCore(trace)
    val keys = NativeRegistrationKeys(trace)
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val formatter = FakeLogFormatter()
    private val strings = FakeStringProvider()
    private val notifications = FakeNotificationCoordinator()
    private val environment = NativeReadEnvironment(io, main)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    private val keysLifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val localReset = NativeLocalReset(initialization, keysLifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger), NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache),
        reads, NativeManagerRuntime(keysLifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, main, main), reads,
    )
    val checked = NativeLiveboardOperations(LiveboardCoreAdapter(core), operations, environment)
    private val jobs = mutableListOf<Job>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()
    private val gates = mutableListOf<NativeTransitionGate>()
    init {
        Dispatchers.setMain(main)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@NativeCheckedOperationFixture.logger }
            single<NotificationCoordinatorInterface> { this@NativeCheckedOperationFixture.notifications }
        }) }
    }
    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun run(task: NativeHeldPublication, scope: CoroutineScope): Job = track(scope.launch(Dispatchers.IO) { task.publish() })
    fun hold() = NativeTransitionGate().also { gates.add(it); core.gate = it }
    suspend fun initialize(key: String) {
        keys.saveString("master_key", key)
        assertIs<InitResult.Success>(manager.initWithSavedKey())
    }
    suspend fun profile(): ProfileScreenViewModel {
        assertNotNull(manager.getStateModel())
        socket.pauseRefreshesAndAwait()
        return ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "device-a"
        }, socket, FakeVaultStatsProvider(), localReset).also { profiles.add(it) }
    }
    fun reset(profile: ProfileScreenViewModel) { profile.handle(ProfileEvents.ResetAllData) }
    suspend fun resetFinished(profile: ProfileScreenViewModel) {
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
        assertEquals(1, trace.count("key.clear"))
    }
    suspend fun freshProof(): NativeReadFrame {
        val target = assertNotNull(reads.requireFreshRead())
        val state = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertSame(state, frame.state)
        assertTrue(target in frame.coveredRequirements)
        assertSame(frame, target.completion.await())
        return frame
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            io.resumeInline()
            gates.forEach { it.release.complete(Unit) }
            io.releaseAll()
            profiles.forEach { it.viewModelScope.cancel() }
            jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
            profiles.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
            socket.pauseRefreshesAndAwait(); socket.onAppBackground()
            stopKoin(); Dispatchers.resetMain()
        }
    }
}
