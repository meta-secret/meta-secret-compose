package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.KeyChainInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretJoinResultEvent
import core.metaSecretCore.MetaSecretSocketClient
import core.metaSecretCore.MetaSecretSocketEvent
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.MetaSecretSocketSubscription
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.profilescreen.ProfileScreenViewModel

/** Dispatches the real continuation inline, including nested Mutex unlock callbacks. */
internal class NativeReentrantDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

internal class NativeLifecycleIo : CoroutineDispatcher() {
    private val queued = Channel<Runnable>(Channel.UNLIMITED)
    private val inline = MutableStateFlow(false)
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (inline.value) block.run() else check(queued.trySend(block).isSuccess)
    }
    fun resumeInline() { inline.value = true }
    suspend fun runNext(scope: CoroutineScope): Job {
        val block = queued.receive()
        return scope.launch(Dispatchers.IO) { block.run() }
    }
    fun drain(scope: CoroutineScope): Job = scope.launch {
        for (block in queued) launch(Dispatchers.IO) { block.run() }
    }
}

internal class NativeLifecycleInvocation(
    val kind: String,
    val response: String,
    val failure: Exception? = null,
    val held: Boolean = true,
) {
    val release = CompletableDeferred<Unit>()
    val returned = CompletableDeferred<Unit>()
}

internal class NativeLifecycleCore : MetaSecretCoreInterface by FakeMetaSecretCore() {
    private val reads = Channel<NativeLifecycleInvocation>(Channel.UNLIMITED)
    private val entries = Channel<NativeLifecycleInvocation>(Channel.UNLIMITED)
    private val released = CompletableDeferred<Unit>()
    val counts = MutableStateFlow(NativeReadCounts())
    val initCalls = MutableStateFlow(0)
    val authCalls = MutableStateFlow(0)
    val trace = MutableStateFlow<List<String>>(emptyList())
    val response = LiveboardNativeFixtures.member()
    var holdAuthorization = false

    fun planRead(response: String = this.response, failure: Exception? = null) {
        check(reads.trySend(NativeLifecycleInvocation("read", response, failure)).isSuccess)
    }
    fun record(event: String) { trace.update { it + event } }
    override fun initAppManager(masterKey: String): String {
        initCalls.update { it + 1 }
        record("init")
        return "ok"
    }
    override fun getAppState(): String {
        counts.update { it.copy(calls = it.calls + 1) }
        return invoke(reads.tryReceive().getOrNull() ?: NativeLifecycleInvocation("read", response, held = false))
    }
    override fun stateEventsAuthToken(vaultName: String): String {
        authCalls.update { it + 1 }
        return invoke(NativeLifecycleInvocation("auth", "actual-subscription-token", held = holdAuthorization))
    }
    private fun invoke(call: NativeLifecycleInvocation): String = runBlocking {
        counts.updateAndGet { it.copy(active = it.active + 1, peak = maxOf(it.peak, it.active + 1)) }
        entries.send(call)
        record("${call.kind}-entered")
        try {
            if (call.held) select { call.release.onAwait { }; released.onAwait { } }
            call.failure?.let { record("read-thrown"); throw it }
            call.response
        } finally {
            counts.update { it.copy(active = it.active - 1) }
            call.returned.complete(Unit)
        }
    }
    suspend fun nextEntry(kind: String): NativeLifecycleInvocation {
        while (true) {
            val entry = entries.receive()
            if (entry.kind == kind) return entry
        }
    }
    fun releaseAll() { released.complete(Unit) }
}

internal class NativeLifecycleKeys(private val core: NativeLifecycleCore) : KeyChainInterface {
    private val delegate = FakeKeyChain(mutableMapOf("master_key" to "lifecycle-key"))
    val clears = MutableStateFlow<List<Int>>(emptyList())
    override suspend fun saveString(key: String, value: String) = delegate.saveString(key, value)
    override suspend fun getString(key: String) = delegate.getString(key)
    override suspend fun containsKey(key: String) = delegate.containsKey(key)
    override suspend fun removeKey(key: String) = delegate.removeKey(key)
    override suspend fun clearAll(isCleanDB: Boolean): Boolean {
        clears.update { it + core.counts.value.active }
        core.record("keys-cleared")
        return delegate.clearAll(isCleanDB)
    }
}

internal class NativeLifecycleClient : MetaSecretSocketClient {
    override val events = MutableSharedFlow<MetaSecretSocketEvent>()
    override val joinResultEvents = MutableSharedFlow<MetaSecretJoinResultEvent>()
    val subscription = MutableStateFlow<MetaSecretSocketSubscription?>(null)
    override fun configure(subscription: MetaSecretSocketSubscription?) { this.subscription.value = subscription }
    override fun connect() = Unit
    override fun disconnect() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeLifecycleTestFixture(main: CoroutineDispatcher = NativeReentrantDispatcher()) {
    val immediate = NativeReentrantDispatcher()
    val io = NativeLifecycleIo()
    val core = NativeLifecycleCore()
    val keys = NativeLifecycleKeys(core)
    val resetEntered = CompletableDeferred<Unit>()
    val socketCatchEntered = CompletableDeferred<Unit>()
    private val logger = object : DebugLoggerInterface by FakeDebugLogger() {
        override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
            if (message == LogTag.ProfileVM.Message.ResetAllData) {
                core.record("reset-entered")
                resetEntered.complete(Unit)
            }
            if (message == LogTag.SocketHandler.Message.ErrorGettingState) {
                core.record("socket-catch")
                socketCatchEntered.complete(Unit)
            }
        }
    }
    val notifications = FakeNotificationCoordinator()
    private val storage = FakeKeyValueStorage()
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    private val composition = LegacyNativeReadComposition(core, cache, logger, formatter, NativeReadEnvironment(io, immediate))
    val initialization = composition.initialization
    val reads = composition.reads
    val manager = composition.manager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache),
    )
    private val localReset = NativeLocalReset(initialization, composition.keyLifecycle(keys), cache)
    val client = NativeLifecycleClient()
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)),
        NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(client, composition.operations, immediate, immediate),
        reads,
    )
    private val jobs = mutableListOf<Job>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()

    init {
        Dispatchers.setMain(main)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@NativeLifecycleTestFixture.logger }
            single<NotificationCoordinatorInterface> { notifications }
        }) }
    }
    fun track(job: Job): Job = job.also { jobs.add(it) }
    suspend fun initialize(scope: CoroutineScope) {
        val call = scope.async(immediate, CoroutineStart.UNDISPATCHED) { manager.initWithSavedKey() }
        track(call)
        val pump = io.drain(scope)
        try {
            assertIs<InitResult.Success>(call.await())
        } finally {
            pump.cancel()
            pump.join()
        }
    }
    fun startRead(scope: CoroutineScope): Deferred<AppStateModel?> =
        scope.async(immediate, CoroutineStart.UNDISPATCHED) { manager.getStateModel() }.also { track(it) }
    fun profile(): ProfileScreenViewModel = ProfileScreenViewModel(
        object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = LiveboardNativeFixtures.currentDeviceId
        }, socket, FakeVaultStatsProvider(), localReset,
    ).also { profiles.add(it) }
    suspend fun primeProfile(scope: CoroutineScope): ProfileScreenViewModel {
        val profile = profile()
        io.runNext(scope).also { track(it) }.join()
        awaitSocketIdle()
        return profile
    }
    suspend fun awaitSocketIdle() {
        cache.synchronization.first { it.activeRefreshes == 0 && !it.loading }
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val drain = io.drain(this)
            core.releaseAll()
            profiles.forEach { it.viewModelScope.cancel() }
            try {
                jobs.forEach { it.cancel() }
                jobs.forEach { it.join() }
                socket.pauseRefreshesAndAwait()
                socket.onAppBackground()
            } finally {
                drain.cancel()
                drain.join()
                stopKoin()
                Dispatchers.resetMain()
            }
        }
    }
}
