package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.VaultStatsProviderInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolver
import core.metaSecretCore.NoopMetaSecretSocketClient
import core.registration.CoreRegistrationSession
import core.registration.RegistrationCorePorts
import core.registration.RegistrationStoragePorts
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import models.apiModels.State
import models.appInternalModels.SocketActionModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeAlertCoordinator
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeNotificationCoordinator
import testutils.FakeScreenMetricsProvider
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.mainscreen.MainNavigationEvent
import ui.scenes.mainscreen.MainScreenDependencies
import ui.scenes.mainscreen.MainScreenViewModel
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent
import ui.scenes.profilescreen.ProfileScreenViewModel

/** The signal drives real consumers; all pause/background work delegates to the real handler. */
internal class NativeRegistrationSocket(
    private val actual: MetaSecretSocketHandler,
    private val trace: NativeRegistrationTrace,
) : MetaSecretSocketHandlerInterface by actual {
    override val socketActions = NativeRegistrationSignals()
    override suspend fun pauseRefreshesAndAwait() { actual.pauseRefreshesAndAwait() }
    override fun resumeRefreshes() { trace.add("socket.resume"); actual.resumeRefreshes() }
    override fun onAppBackground() { trace.add("socket.background"); actual.onAppBackground() }
}

/** Acknowledges the actual consumer callback return, including an ignored duplicate event. */
@OptIn(InternalCoroutinesApi::class, kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
internal class NativeRegistrationSignals : SharedFlow<SocketActionModel> {
    private val source = MutableSharedFlow<Pair<Int, SocketActionModel>>(extraBufferCapacity = 8)
    private var sequence = 0
    val collectorCount = MutableStateFlow(0)
    val subscriptionCount = source.subscriptionCount
    val handled = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val jobs = MutableStateFlow<List<Job>>(emptyList())
    override val replayCache: List<SocketActionModel> get() = emptyList()
    override suspend fun collect(collector: FlowCollector<SocketActionModel>): Nothing {
        val id = collectorCount.updateAndGet { it + 1 }
        val collectingJob = currentCoroutineContext().job
        jobs.update { it + collectingJob }
        return source.collect(object : FlowCollector<Pair<Int, SocketActionModel>> {
            override suspend fun emit(value: Pair<Int, SocketActionModel>) {
                try { collector.emit(value.second) }
                finally { handled.update { it + (id to value.first) } }
            }
        })
    }
    suspend fun emit(action: SocketActionModel): Int {
        val id = ++sequence
        source.emit(id to action)
        return id
    }
    suspend fun awaitHandled(collector: Int, signal: Int) { handled.first { (it[collector] ?: 0) >= signal } }
    suspend fun close() { jobs.value.forEach { it.cancel() }; jobs.value.forEach { it.join() } }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeRegistrationTestFixture {
    val trace = NativeRegistrationTrace()
    val core = NativeRegistrationCore(trace)
    val keys = NativeRegistrationKeys(trace)
    val immediate = NativeReentrantDispatcher()
    val caller = NativePublicationDispatcher()
    val mainUi = NativePublicationDispatcher()
    val mainCompleted = MutableStateFlow(0)
    val notifications = FakeNotificationCoordinator()
    val storage = FakeKeyValueStorage()
    private val logger = object : DebugLoggerInterface by FakeDebugLogger() {
        override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
            if (message == LogTag.MainVM.Message.CurrentDeviceRemovalReset && extra?.startsWith("completed") == true) {
                mainCompleted.value += 1
            }
        }
    }
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    private val environment = NativeReadEnvironment(immediate, immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    private val keyLifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    val localReset = NativeLocalReset(initialization, keyLifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads,
        NativeManagerRuntime(keyLifecycle, operations),
    )
    private val actualSocket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, immediate, Dispatchers.IO), reads,
    )
    val socket = NativeRegistrationSocket(actualSocket, trace)
    val resolver = MetaSecretStateResolver(core, logger, reads, operations)
    val registrationLifecycle = NativeRegistrationLifecycle(keyLifecycle)
    init {
        Dispatchers.setMain(immediate)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@NativeRegistrationTestFixture.logger }
            single<NotificationCoordinatorInterface> { notifications }
        }) }
    }
    val session = CoreRegistrationSession(
        RegistrationCorePorts(core, manager, resolver, socket), RegistrationStoragePorts(keys, storage, cache), registrationLifecycle,
    )
    private val profiles = mutableListOf<ProfileScreenViewModel>()
    private val gates = mutableListOf<NativeTransitionGate>()
    private val jobs = mutableListOf<Job>()
    private var mainCollector = 0

    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun gate() = NativeTransitionGate().also { gates.add(it) }
    fun hold(port: String) = gate().also { core.hold(port, it) }
    suspend fun initialize(state: State = State.Local(core.user.device), key: String = "stored-key-a") {
        core.state.value = state
        keys.saveString("master_key", key)
        assertIs<InitResult.Success>(manager.initWithSavedKey())
    }
    suspend fun pause() { socket.pauseRefreshesAndAwait() }
    suspend fun profile(): ProfileScreenViewModel {
        val vm = ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "registration-device"
        }, socket, FakeVaultStatsProvider(), localReset).also { profiles.add(it) }
        pause()
        return vm
    }
    fun reset(profile: ProfileScreenViewModel) { profile.handle(ProfileEvents.ResetAllData) }
    suspend fun awaitReset(profile: ProfileScreenViewModel) {
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
    }
    suspend fun successor(state: State = core.member()): NativeReadFrame {
        initialize(state, "stored-key-b")
        val target = assertNotNull(reads.requireFreshRead())
        val value = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertTrue(target in frame.coveredRequirements)
        assertSame(value, frame.state)
        assertSame(value, cache.appState.value)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
        return frame
    }
    suspend fun main(): MainScreenViewModel {
        val before = socket.socketActions.collectorCount.value
        val subscriptions = socket.socketActions.subscriptionCount.value
        val stats = object : VaultStatsProviderInterface by FakeVaultStatsProvider() {
            override val joinRequestsCount = MutableStateFlow<Int?>(0)
        }
        val vm = MainScreenViewModel(socket, manager, FakeBiometricAuthenticator(), FakeScreenMetricsProvider(),
            MainScreenDependencies(stats, FakeAlertCoordinator(), localReset))
        mainCollector = socket.socketActions.collectorCount.first { it > before }
        socket.socketActions.subscriptionCount.first { it > subscriptions }
        vm.joinRequestsCount.first { it == 0 }
        pause()
        return vm
    }
    suspend fun remove(main: MainScreenViewModel): Int {
        assertNotNull(initialization.currentScope(), "Main drain barrier starts from actual initialized A")
        val signal = socket.socketActions.emit(SocketActionModel.CURRENT_DEVICE_REMOVED)
        // A positive real barrier: correct admission closes, or old cleanup actually completes.
        while (initialization.currentScope() != null && main.navigationEvent.value != MainNavigationEvent.NavigateToSignIn) yield()
        return signal
    }
    suspend fun finishMainSignal(signal: Int) { socket.socketActions.awaitHandled(mainCollector, signal) }
    fun queueMainUi() { Dispatchers.setMain(mainUi) }
    fun immediateMainUi() { Dispatchers.setMain(immediate) }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            immediateMainUi()
            val callerDrain = caller.drain(this)
            val mainDrain = mainUi.drain(this)
            gates.forEach { it.release.complete(Unit) }
            profiles.forEach { it.viewModelScope.cancel() }
            session.stop()
            try {
                jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
                actualSocket.pauseRefreshesAndAwait()
                actualSocket.onAppBackground()
                socket.socketActions.close()
            } finally {
                callerDrain.cancel(); mainDrain.cancel()
                callerDrain.join(); mainDrain.join()
                stopKoin(); Dispatchers.resetMain()
            }
        }
    }
}
