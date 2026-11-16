package core.metaSecretCore.liveboard

import core.AppStateCacheProvider
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.LocalState
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.NoopMetaSecretSocketClient
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import models.apiModels.AppStateModel
import models.apiModels.DeviceData
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.State
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

/** Explicitly queued IO lets a held real FFI call coexist with deterministic callers. */
internal class NativeReadIoDispatcher : CoroutineDispatcher() {
    private val queued = Channel<Runnable>(Channel.UNLIMITED)
    private val enqueued = MutableStateFlow(0)
    val dispatchCount: Int get() = enqueued.value

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        enqueued.update { it + 1 }
        check(queued.trySend(block).isSuccess)
    }

    suspend fun runNext(scope: CoroutineScope): Job {
        val runnable = queued.receive()
        return scope.launch(Dispatchers.IO) { runnable.run() }
    }

    fun runCurrentlyQueued(scope: CoroutineScope): List<Job> = buildList {
        while (true) {
            val runnable = queued.tryReceive().getOrNull() ?: break
            add(scope.launch(Dispatchers.IO) { runnable.run() })
        }
    }

    fun drain(scope: CoroutineScope): Job = scope.launch {
        for (runnable in queued) launch(Dispatchers.IO) { runnable.run() }
    }
}

internal data class NativeReadCounts(val active: Int = 0, val peak: Int = 0, val calls: Int = 0)

internal class NativePhysicalRead(val number: Int, val response: String) {
    val release = CompletableDeferred<Unit>()
}

internal class NativeGatedCore : MetaSecretCoreInterface by FakeMetaSecretCore() {
    private val entries = Channel<NativePhysicalRead>(Channel.UNLIMITED)
    private val released = CompletableDeferred<Unit>()
    private val counts = MutableStateFlow(NativeReadCounts())
    val response = MutableStateFlow(localStateJson())
    val statistics: NativeReadCounts get() = counts.value

    override fun getAppState(): String = runBlocking {
        val entered = counts.updateAndGet {
            val active = it.active + 1
            NativeReadCounts(active, maxOf(active, it.peak), it.calls + 1)
        }
        val entry = NativePhysicalRead(entered.calls, response.value)
        entries.send(entry)
        try {
            select { entry.release.onAwait { }; released.onAwait { } }
            entry.response
        } finally {
            counts.update { it.copy(active = it.active - 1) }
        }
    }

    suspend fun nextEntry(): NativePhysicalRead = entries.receive()
    fun releaseAll() { released.complete(Unit) }

    companion object {
        fun localStateJson(): String = JsonConfig.json.encodeToString(
            AppStateModel(
                message = Message(State.Local(DeviceData("read-device", "Read device", "Android", keys = OpenBox("d", "t")))),
                success = true,
            )
        )
    }
}

internal class NativeReadOwnerFixture {
    val io = NativeReadIoDispatcher()
    val core = NativeGatedCore()
    val logger = FakeDebugLogger()
    val storage = FakeKeyValueStorage()
    val keys = FakeKeyChain(mutableMapOf("master_key" to "read-owner-key"))
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    private val notification = FakeNotificationCoordinator()
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    private val environment = NativeReadEnvironment(io, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(
        NativeReadDependencies(core, logger, cache, formatter),
        NativeReadRuntime(initialization, environment, privacy),
    )
    private val keyLifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notification, ErrorMapper(strings), formatter, cache),
        reads,
        NativeManagerRuntime(keyLifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notification, ErrorMapper(strings)),
        NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, Dispatchers.Unconfined, Dispatchers.Unconfined),
        reads,
    )
    val localState = LocalState("read@example.org", core, logger, reads, operations)

    suspend fun initialize(scope: CoroutineScope) {
        val result = scope.async(start = CoroutineStart.UNDISPATCHED) { manager.initWithSavedKey() }
        val pump = io.drain(scope)
        try {
            assertIs<InitResult.Success>(result.await())
        } finally {
            pump.cancel()
            pump.join()
        }
    }

    suspend fun completeNextRead(scope: CoroutineScope): NativeReadFrame {
        val oldAttempt = cache.canonicalFrames.value?.attempt
        val worker = io.runNext(scope)
        core.nextEntry().release.complete(Unit)
        val frame = cache.canonicalFrames.first { it != null && it.attempt != oldAttempt }!!
        worker.join()
        return frame
    }

    suspend fun close(scope: CoroutineScope, calls: List<Job>) = withContext(NonCancellable) {
        withTimeout(5_000) {
            val drain = io.drain(this)
            core.releaseAll()
            try {
                calls.forEach { it.join() }
                socket.pauseRefreshesAndAwait()
                socket.onAppBackground()
            } finally {
                drain.cancel()
                drain.join()
            }
        }
    }
}

/** Constructor-only legacy fixtures share the same real initialization and read owner. */
internal class LegacyNativeReadComposition(
    private val core: MetaSecretCoreInterface,
    cache: core.AppStateCacheProviderInterface,
    logger: core.DebugLoggerInterface,
    formatter: core.LogFormatterInterface? = FakeLogFormatter(),
    private val environment: NativeReadEnvironment = NativeReadEnvironment(ui = Dispatchers.Unconfined),
) {
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(
        NativeReadDependencies(core, logger, cache, formatter),
        NativeReadRuntime(initialization, environment, privacy),
    )
    private var selectedKeys: core.KeyChainInterface? = null
    private var selectedLifecycle: NativeKeyLifecycle? = null

    fun keyLifecycle(keys: core.KeyChainInterface): NativeKeyLifecycle {
        selectedLifecycle?.let {
            check(selectedKeys === keys) { "One composition must keep the same key owner" }
            return it
        }
        return NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads).also {
            selectedKeys = keys
            selectedLifecycle = it
        }
    }

    fun manager(ports: NativeManagerPorts, presentation: NativeManagerPresentation) =
        MetaSecretAppManager(ports, presentation, reads, NativeManagerRuntime(keyLifecycle(ports.keys), operations))

    suspend fun initializeImplicitCore() {
        val keys = FakeKeyChain(mutableMapOf("master_key" to "legacy-fixture-key"))
        val result = keyLifecycle(keys).initializeSavedKey(initialization.capture())
        assertIs<InitResult.Success>(result?.value)
    }
}
