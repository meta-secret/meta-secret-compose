package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.LocalState
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretJoinResultEvent
import core.metaSecretCore.MetaSecretJoinResultSubscription
import core.metaSecretCore.MetaSecretSocketClient
import core.metaSecretCore.MetaSecretSocketEvent
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketSubscription
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.State
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

/** Acknowledges the actual Socket collector callback, never native completion or proof. */
@OptIn(InternalCoroutinesApi::class, kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
internal class NativeAcknowledgedSocketSignal<T> : SharedFlow<T> {
    private val source = MutableSharedFlow<Pair<Int, T>>()
    val subscriptions get() = source.subscriptionCount
    private val handled = MutableStateFlow(0)
    private val jobs = MutableStateFlow<List<Job>>(emptyList())
    private var sequence = 0
    override val replayCache: List<T> get() = emptyList()
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val job = currentCoroutineContext().job
        jobs.update { it + job }
        return source.collect(object : FlowCollector<Pair<Int, T>> {
            override suspend fun emit(value: Pair<Int, T>) {
                try { collector.emit(value.second) }
                finally { handled.value = value.first }
            }
        })
    }
    suspend fun emitAndAwait(value: T) {
        subscriptions.first { it > 0 }
        val ticket = ++sequence
        source.emit(ticket to value)
        handled.first { it >= ticket }
    }
    suspend fun close() { jobs.value.forEach { it.cancel() }; jobs.value.forEach { it.join() } }
}

/** Transport records only; the actual handler receives every event and owns all reads. */
internal class NativeVisibilitySocketClient : MetaSecretSocketClient {
    override val events = NativeAcknowledgedSocketSignal<MetaSecretSocketEvent>()
    override val joinResultEvents = NativeAcknowledgedSocketSignal<MetaSecretJoinResultEvent>()
    val effects = MutableStateFlow<List<String>>(emptyList())
    override fun configure(subscription: MetaSecretSocketSubscription?) { effects.update { it + "configure:${subscription?.vaultName}" } }
    override fun connect() { effects.update { it + "connect" } }
    override fun disconnect() { effects.update { it + "disconnect" } }
    override fun configureJoinResult(subscription: MetaSecretJoinResultSubscription?) { effects.update { it + "join:${subscription?.vaultName}" } }
    override fun connectJoinResult() { effects.update { it + "join-connect" } }
    override fun disconnectJoinResult() { effects.update { it + "join-disconnect" } }
    suspend fun close() { events.close(); joinResultEvents.close() }
}

/** SAME real ownership/keys/cache; only IO and actual caller continuations are gated. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeSocketVisibilityFixture(queueSocket: Boolean = false) {
    val trace = NativeRegistrationTrace()
    val core = NativeRegistrationCore(trace)
    val keys = NativeRegistrationKeys(trace)
    val io = NativeLifecycleIo()
    val immediate = NativeReentrantDispatcher()
    val caller = NativePublicationDispatcher()
    val client = NativeVisibilitySocketClient()
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val formatter = FakeLogFormatter()
    private val strings = FakeStringProvider()
    private val notifications = FakeNotificationCoordinator()
    private val environment = NativeReadEnvironment(io, immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    private val keyLifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val localReset = NativeLocalReset(initialization, keyLifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads,
        NativeManagerRuntime(keyLifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(client, operations, immediate, if (queueSocket) caller else immediate), reads,
    )
    val local = LocalState("Fixture Vault", core, logger, reads, operations)
    private val queuedSocket = queueSocket
    private val gates = mutableListOf<NativeTransitionGate>()
    private val jobs = mutableListOf<Job>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()
    private var ioPump: Job? = null

    init {
        Dispatchers.setMain(immediate)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@NativeSocketVisibilityFixture.logger }
            single<NotificationCoordinatorInterface> { notifications }
        }) }
    }
    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun hold(port: String): NativeTransitionGate = NativeTransitionGate().also { gates.add(it); core.hold(port, it) }
    suspend fun start(scope: CoroutineScope) {
        ioPump = io.drain(scope)
        if (queuedSocket) {
            val startup = caller.drain(scope)
            try {
                client.events.subscriptions.first { it > 0 }
                client.joinResultEvents.subscriptions.first { it > 0 }
            } finally { startup.cancel(); startup.join() }
        }
        initialize()
    }
    suspend fun initialize(key: String = "visibility-key-a", state: State = core.member()) {
        core.state.value = state
        keys.saveString("master_key", key)
        assertIs<InitResult.Success>(manager.initWithSavedKey())
    }
    suspend fun idle() { cache.synchronization.first { it.activeRefreshes == 0 && !it.loading } }
    suspend fun profile(): ProfileScreenViewModel {
        assertNotNull(manager.getStateModel())
        socket.pauseRefreshesAndAwait()
        val profile = ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "registration-device"
        }, socket, FakeVaultStatsProvider(), localReset).also { profiles.add(it) }
        idle()
        return profile
    }
    fun reset(profile: ProfileScreenViewModel) { profile.handle(ProfileEvents.ResetAllData) }
    suspend fun resetFinished(profile: ProfileScreenViewModel) {
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
    }
    suspend fun freshManagerProof(): NativeReadFrame {
        val target = assertNotNull(reads.requireFreshRead())
        val value = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertSame(value, frame.state)
        assertSame(value, cache.appState.value)
        assertTrue(target in frame.coveredRequirements)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
        return frame
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val callerPump = caller.drain(this)
            val cleanupIo = io.drain(this)
            gates.forEach { it.release.complete(Unit) }
            profiles.forEach { it.viewModelScope.cancel() }
            try {
                jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
                profiles.forEach { it.viewModelScope.coroutineContext.job.join() }
                socket.pauseRefreshesAndAwait()
                socket.onAppBackground()
                client.close()
            } finally {
                callerPump.cancel(); callerPump.join()
                cleanupIo.cancel(); cleanupIo.join()
                ioPump?.cancel(); ioPump?.join()
                stopKoin(); Dispatchers.resetMain()
            }
        }
    }
}
