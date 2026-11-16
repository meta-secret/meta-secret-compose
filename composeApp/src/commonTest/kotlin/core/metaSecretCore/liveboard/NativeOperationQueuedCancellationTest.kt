package core.metaSecretCore.liveboard

import core.AppStateCacheProvider
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.appInternalModels.SecretModel
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

/** Real operation DI with a queued native dispatcher, not a second admission owner. */
private class NativeQueuedBusinessFixture {
    val io = NativeLifecycleIo()
    val immediate = NativeReentrantDispatcher()
    val core = NativeBusinessCore()
    val keys = NativeTransitionKeys()
    val notifications = FakeNotificationCoordinator()
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val formatter = FakeLogFormatter()
    private val environment = NativeReadEnvironment(io, immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(
        NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy),
    )
    private val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(FakeStringProvider()), formatter, cache),
        reads, NativeManagerRuntime(lifecycle, operations),
    )
    private val jobs = mutableListOf<Job>()

    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    suspend fun initialize(scope: CoroutineScope) {
        val initialized = track(scope.async(immediate, CoroutineStart.UNDISPATCHED) { manager.initWithSavedKey() })
        withContext(NonCancellable) {
            val pump = io.drain(this)
            try { initialized.join(); assertIs<InitResult.Success>(initialized.await()) }
            finally { pump.cancel(); pump.join() }
        }
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val drain = io.drain(this)
            try {
                jobs.forEach { it.cancel() }
                jobs.forEach { it.join() }
            } finally { drain.cancel(); drain.join() }
        }
    }
}

class NativeOperationQueuedCancellationTest {
    @Test
    fun testCancelledQueuedManagerOperationOwnsNoNativeLeaseBeforeItsRunnableExecutes() = runBlocking {
        withTimeout(10_000) {
            val f = NativeQueuedBusinessFixture()
            try {
                f.initialize(this)
                val original = assertNotNull(f.initialization.currentScope())
                val queued = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.manager.splitSecret(SecretModel("queued-secret", "fixture-value"))
                })
                assertTrue(!queued.isCompleted, "Real Manager entered and suspended at its injected native IO boundary")
                assertEquals(emptyList(), f.core.calls.value)
                queued.cancel()

                // The old actual IO task stays queued. Fresh Reset has immediate key IO available.
                f.io.resumeInline()
                val reset = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) { f.reset.reset(original.owner) })
                assertTrue(reset.isCompleted,
                    "Actual Reset must complete BEFORE running cancelled queued business IO: it owns zero native leases")
                assertEquals(true, assertNotNull(reset.await()).value)
                assertEquals(1, f.keys.clears.value)
                assertEquals(emptyList(), f.core.calls.value, "No native business entry before the queued task executes")
                assertNull(f.keys.getString("master_key"))
                assertNull(f.cache.canonicalFrames.value)

                f.track(f.io.runNext(this)).join()
                queued.join()
                assertTrue(queued.isCancelled)
                assertEquals(emptyList(), f.core.calls.value, "Executing the cancelled task must not enter Core")

                f.keys.saveString("master_key", "fresh-after-queued-operation")
                assertIs<InitResult.Success>(f.manager.initWithSavedKey())
                val successor = assertNotNull(f.initialization.currentScope())
                assertNotSame(original.owner, successor.owner)
                assertEquals(true, f.manager.splitSecret(SecretModel("fresh-secret", "fixture-value"))?.success)
                assertEquals(listOf(NativeCall("split", listOf("fresh-secret", "fixture-value"))), f.core.calls.value)
                val target = assertNotNull(f.reads.requireFreshRead())
                val state = assertNotNull(f.manager.getStateModel())
                val frame = assertNotNull(f.cache.canonicalFrames.value)
                assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
                assertEquals(successor, frame.scope)
                assertSame(state, frame.state)
                assertSame(state, f.cache.appState.value)
                assertTrue(target in frame.coveredRequirements)
                assertTrue(target.completion.isCompleted)
                assertSame(frame, target.completion.await())
                assertEquals(2, f.core.inits.value)
                assertTrue(f.notifications.errorMessages.isEmpty())
            } finally { f.close() }
        }
    }
}
