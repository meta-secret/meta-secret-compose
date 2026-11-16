package core.metaSecretCore.liveboard

import core.AppStateCacheProvider
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.NoopMetaSecretSocketClient
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import models.apiModels.DeviceData
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.State
import kotlinx.serialization.encodeToString
import testutils.FakeDebugLogger
import testutils.FakeKeyChain
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

/** An actual dispatcher continuation, retained so cleanup can release a taken task too. */
internal class NativeHeldPublication(private val runnable: Runnable) {
    private val started = MutableStateFlow(false)
    fun publish() { if (started.compareAndSet(false, true)) runnable.run() }
}

/** Controls only the owner's UI admission; no fake snapshot or read owner is introduced. */
internal class NativePublicationDispatcher : CoroutineDispatcher() {
    private val queued = Channel<NativeHeldPublication>(Channel.UNLIMITED)
    private val owned = MutableStateFlow<List<NativeHeldPublication>>(emptyList())

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val publication = NativeHeldPublication(block)
        owned.update { it + publication }
        check(queued.trySend(publication).isSuccess)
    }

    suspend fun take(): NativeHeldPublication = queued.receive()

    fun drain(scope: CoroutineScope): Job = scope.launch {
        owned.value.forEach { launch(Dispatchers.IO) { it.publish() } }
        for (publication in queued) launch(Dispatchers.IO) { publication.publish() }
    }
}

internal class NativePublicationTestFixture {
    val io = NativeReadIoDispatcher()
    val publicationDispatcher = NativePublicationDispatcher()
    val core = NativeGatedCore()
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val formatter = FakeLogFormatter()
    private val strings = FakeStringProvider()
    private val notifications = FakeNotificationCoordinator()
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    private val composition = LegacyNativeReadComposition(
        core, cache, logger, formatter, NativeReadEnvironment(io, publicationDispatcher),
    )
    val reads = composition.reads
    private val manager = composition.manager(
        NativeManagerPorts(core, FakeKeyChain(mutableMapOf("master_key" to "publication-key")), storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)),
        NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), composition.operations, Dispatchers.Unconfined, Dispatchers.Unconfined),
        reads,
    )
    private val jobs = mutableListOf<Job>()

    suspend fun initialize(scope: CoroutineScope) {
        val initialized = scope.async(start = CoroutineStart.UNDISPATCHED) { manager.initWithSavedKey() }
        jobs.add(initialized)
        withContext(NonCancellable) {
            val pump = io.drain(this)
            val uiPump = launch(Dispatchers.IO) { while (true) publicationDispatcher.take().publish() }
            try {
                initialized.join()
                assertIs<InitResult.Success>(initialized.await())
            } finally {
                pump.cancel()
                uiPump.cancel()
                pump.join()
                uiPump.join()
            }
        }
    }

    fun startManagerRead(scope: CoroutineScope): Deferred<AppStateModel?> =
        scope.async(start = CoroutineStart.UNDISPATCHED) { manager.getStateModel() }.also { jobs.add(it) }

    suspend fun completePhysicalAndHoldPublication(scope: CoroutineScope): NativeHeldPublication {
        val worker = io.runNext(scope)
        jobs.add(worker)
        core.nextEntry().release.complete(Unit)
        val publication = publicationDispatcher.take()
        worker.join()
        return publication
    }

    suspend fun awaitSocketIdle(expectedOtherRefreshes: Int = 0) {
        // Scheduling barrier only; this never certifies a required read or UI permissions.
        cache.synchronization.first {
            it.activeRefreshes == expectedOtherRefreshes && it.loading == (expectedOtherRefreshes > 0)
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val ioDrain = io.drain(this)
            val uiDrain = publicationDispatcher.drain(this)
            core.releaseAll()
            try {
                jobs.forEach { it.join() }
                socket.pauseRefreshesAndAwait()
                socket.onAppBackground()
            } finally {
                ioDrain.cancel()
                uiDrain.cancel()
                ioDrain.join()
                uiDrain.join()
            }
        }
    }

    companion object {
        fun localJson(name: String): String = JsonConfig.json.encodeToString(
            AppStateModel(
                Message(State.Local(DeviceData("read-device", name, "Android", keys = OpenBox("d", "t")))),
                success = true,
            )
        )
    }
}
