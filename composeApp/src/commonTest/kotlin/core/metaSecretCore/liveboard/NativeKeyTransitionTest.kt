package core.metaSecretCore.liveboard

import core.AppStateCacheProvider
import core.KeyChainInterface
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketClient
import core.metaSecretCore.MetaSecretSocketHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.AppStateModel
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeMetaSecretCore
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

internal class NativeTransitionGate(val failure: Exception? = null) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val exited = CompletableDeferred<Unit>()
    suspend fun hold() {
        entered.complete(Unit)
        try { release.await(); failure?.let { throw it } }
        finally { exited.complete(Unit) }
    }
}

internal class NativeTransitionKeys : KeyChainInterface {
    val values = MutableStateFlow(mapOf("master_key" to "key-a"))
    val reads = MutableStateFlow(0)
    val clears = MutableStateFlow(0)
    var readGate: NativeTransitionGate? = null
    var clearGate: NativeTransitionGate? = null
    var clearResult = true
    override suspend fun saveString(key: String, value: String): Boolean {
        values.update { it + (key to value) }; return true
    }
    override suspend fun getString(key: String): String? {
        reads.update { it + 1 }
        readGate?.also { readGate = null }?.hold()
        return values.value[key]
    }
    override suspend fun containsKey(key: String) = key in values.value
    override suspend fun removeKey(key: String): Boolean {
        val had = key in values.value; values.update { it - key }; return had
    }
    override suspend fun clearAll(isCleanDB: Boolean): Boolean {
        // The partial key effect is deliberately BEFORE the controlled return/failure gate.
        values.value = emptyMap()
        clears.update { it + 1 }
        clearGate?.also { clearGate = null }?.hold()
        return clearResult
    }
}

internal class NativeTransitionCore : MetaSecretCoreInterface by FakeMetaSecretCore() {
    val inits = MutableStateFlow(0)
    val reads = MutableStateFlow(0)
    val tokens = MutableStateFlow(0)
    val response = MutableStateFlow(LiveboardNativeFixtures.member())
    var initGate: NativeTransitionGate? = null
    var tokenGate: NativeTransitionGate? = null
    override fun initAppManager(masterKey: String): String = runBlocking {
        inits.update { it + 1 }; initGate?.also { initGate = null }?.hold(); "ok"
    }
    override fun getAppState(): String { reads.update { it + 1 }; return response.value }
    override fun stateEventsAuthToken(vaultName: String): String = runBlocking {
        tokens.update { it + 1 }; tokenGate?.also { tokenGate = null }?.hold(); "test-token"
    }
    override fun joinResultAuthToken(): String {
        tokens.update { it + 1 }; return "test-join-token"
    }
}

/** Real composition; only the existing synchronous Core/key ports are controlled. */
internal class NativeTransitionFixture {
    val io = NativeLifecycleIo()
    val immediate = NativeReentrantDispatcher()
    val core = NativeTransitionCore()
    val keys = NativeTransitionKeys()
    val notifications = FakeNotificationCoordinator()
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val formatter = FakeLogFormatter()
    private val strings = FakeStringProvider()
    private val environment = NativeReadEnvironment(io, immediate)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger),
        NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache), reads, NativeManagerRuntime(lifecycle, operations),
    )
    private val jobs = mutableListOf<Job>()
    private val pumps = mutableListOf<Job>()
    private val gates = mutableListOf<NativeTransitionGate>()
    fun gate(failure: Exception? = null) = NativeTransitionGate(failure).also { gates.add(it) }
    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun pump(scope: CoroutineScope) = io.drain(scope).also { pumps.add(it) }
    fun socket(client: MetaSecretSocketClient) = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(client, operations, immediate, immediate), reads,
    )
    suspend fun initialize(scope: CoroutineScope) {
        val call = track(scope.async(immediate, CoroutineStart.UNDISPATCHED) { manager.initWithSavedKey() })
        withContext(NonCancellable) {
            val pump = io.drain(this)
            try { call.join(); assertIs<InitResult.Success>(call.await()) }
            finally { pump.cancel(); pump.join() }
        }
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            val drain = io.drain(this)
            gates.forEach { it.release.complete(Unit) }
            try { jobs.forEach { it.cancel() }; jobs.forEach { it.join() } }
            finally {
                pumps.forEach { it.cancel() }; pumps.forEach { it.join() }
                drain.cancel(); drain.join()
            }
        }
    }
}

class NativeKeyTransitionTest {
    @Test
    fun testSameKeyKeepsEpochAndChangedKeyCallbackCannotEraseFreshSuccessorProof() = runBlocking {
        withTimeout(10_000) {
            val f = NativeTransitionFixture()
            try {
                f.initialize(this)
                val scopeA = assertNotNull(f.initialization.currentScope())
                f.initialize(this)
                assertEquals(1, f.core.inits.value)
                assertSame(scopeA.owner, f.initialization.capture())
                assertSame(scopeA.epoch, assertNotNull(f.initialization.currentScope()).epoch)
                val permitted = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.initialization.invoke(scopeA) { f.core.stateEventsAuthToken("parser-vault") }
                })
                f.track(f.io.runNext(this)).join()
                assertIs<NativeInvocation.Value<String>>(permitted.await())

                val oldTarget = assertNotNull(f.reads.requireFreshRead())
                val initReturned = MutableStateFlow(false)
                val committed = CompletableDeferred<NativeReadFrame>()
                var targetB: NativeReadRequirement? = null
                var stateB: AppStateModel? = null
                val callback = f.track(launch(f.immediate, CoroutineStart.UNDISPATCHED) {
                    val retired = oldTarget.completion.await()
                    assertEquals(NativeReadOutcome.RETIRED, retired.outcome)
                    assertNull(retired.state)
                    val requiredB = assertNotNull(f.reads.requireFreshRead())
                    targetB = requiredB
                    stateB = assertNotNull(f.manager.getStateModel())
                    val frame = assertNotNull(f.cache.canonicalFrames.value)
                    assertTrue(!initReturned.value, "Fresh B must commit inside the actual retirement callback before init returns")
                    assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
                    assertTrue(requiredB in frame.coveredRequirements)
                    committed.complete(frame)
                })
                f.keys.saveString("master_key", "key-b")
                val changed = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                    f.manager.initWithSavedKey().also { initReturned.value = true }
                })
                f.io.resumeInline() // Initial key IO was queued; nested callback IO runs on its actual worker.
                f.track(f.io.runNext(this)).join()
                assertIs<InitResult.Success>(changed.await())
                callback.join()
                assertTrue(committed.isCompleted, "No timeout await may substitute for actual B commit")
                val fresh = committed.await()
                assertNotSame(scopeA.owner, fresh.scope?.owner)
                assertNotSame(scopeA.epoch, fresh.scope?.epoch)
                val failures = mutableListOf<String>()
                if (f.cache.canonicalFrames.value !== fresh) failures.add("changed-key init erased fresh B proof after retirement callback")
                if (f.cache.appState.value !== stateB) failures.add("changed-key init replaced fresh B data")
                if (f.initialization.currentScope() != fresh.scope) failures.add("fresh callback used an obsolete owner/epoch")
                if (targetB?.completion?.isCompleted != true) failures.add("fresh B required read did not complete")
                if (f.core.inits.value != 2 || f.core.reads.value != 1) failures.add("unexpected actual init/read counts")
                assertEquals(emptyList(), failures)
            } finally { f.close() }
        }
    }

    @Test
    fun testKeyReadInitAndPartialClearFailuresReleaseReservationForRealSuccessor() = runBlocking {
        withTimeout(10_000) {
            for (stage in listOf("read", "init", "clear-throw", "clear-false")) {
                val f = NativeTransitionFixture()
                try {
                    f.initialize(this)
                    f.pump(this)
                    val oldScope = assertNotNull(f.initialization.currentScope())
                    val gate = f.gate(if (stage == "clear-false") null else IllegalStateException("controlled $stage failure"))
                    when (stage) {
                        "read" -> f.keys.readGate = gate
                        "init" -> { f.keys.saveString("master_key", "changed-key"); f.core.initGate = gate }
                        else -> { f.keys.clearGate = gate; f.keys.clearResult = stage != "clear-false" }
                    }
                    val operation = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                        if (stage.startsWith("clear")) f.reset.reset(oldScope.owner)
                        else f.manager.initWithSavedKey()
                    })
                    gate.entered.await()
                    assertNull(f.initialization.currentScope())
                    gate.release.complete(Unit)
                    val outcome = operation.await()
                    if (stage.startsWith("clear")) {
                        val receipt = assertIs<NativeKeyResult<*>>(outcome)
                        assertEquals(false, receipt.value)
                        assertEquals(stage == "clear-throw", receipt.failure != null)
                        assertEquals(1, f.keys.clears.value)
                    } else assertIs<InitResult.Error>(outcome)
                    assertNull(f.initialization.currentScope(), "Failure must not restore the prior initialized epoch")
                    assertNotSame(oldScope.owner, f.initialization.capture())
                    val tokens = f.core.tokens.value
                    assertEquals(NativeInvocation.Retired, f.initialization.invoke(oldScope) { f.core.stateEventsAuthToken("parser-vault") })
                    assertEquals(tokens, f.core.tokens.value)
                    f.keys.saveString("master_key", "successor-$stage")
                    assertIs<InitResult.Success>(f.manager.initWithSavedKey())
                    val successor = assertNotNull(f.initialization.currentScope())
                    val state = assertNotNull(f.manager.getStateModel())
                    assertSame(state, assertNotNull(f.cache.canonicalFrames.value).state)
                    assertEquals("successor-$stage", f.keys.getString("master_key"))
                    assertSame(successor.owner, f.initialization.capture())
                    assertEquals(if (stage == "init") 3 else 2, f.core.inits.value)
                } finally { f.close() }
            }
        }
    }

    @Test
    fun testCancellationAtDrainKeyReadInitAndPartialClearCannotStrandOrRestoreOldOwner() = runBlocking {
        withTimeout(10_000) {
            for (stage in listOf("drain", "read", "init", "clear")) {
                val f = NativeTransitionFixture()
                try {
                    f.initialize(this)
                    f.pump(this)
                    val original = assertNotNull(f.initialization.currentScope())
                    val gate = f.gate()
                    var native: Job? = null
                    when (stage) {
                        "drain" -> {
                            f.core.tokenGate = gate
                            native = f.track(async(Dispatchers.IO) { f.initialization.invoke(original) { f.core.stateEventsAuthToken("parser-vault") } })
                        }
                        "read" -> f.keys.readGate = gate
                        "init" -> { f.keys.saveString("master_key", "changed-key"); f.core.initGate = gate }
                        else -> f.keys.clearGate = gate
                    }
                    if (stage == "drain") gate.entered.await()
                    val old = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) {
                        if (stage == "drain" || stage == "clear") f.reset.reset(original.owner)
                        else f.manager.initWithSavedKey()
                    })
                    if (stage != "drain") gate.entered.await()
                    assertNull(f.initialization.currentScope())
                    old.cancel()
                    f.initialization.invalidate()
                    val waitingOwner = f.initialization.capture()
                    // Clear's actual partial mutation already occurred before gate.entered.
                    f.keys.saveString("master_key", "successor-$stage")
                    val before = f.core.inits.value to f.keys.reads.value
                    val successor = f.track(async(f.immediate, CoroutineStart.UNDISPATCHED) { f.manager.initWithSavedKey() })
                    assertEquals(before, f.core.inits.value to f.keys.reads.value, "Successor must wait the actual old reservation")
                    assertSame(waitingOwner, f.initialization.capture())
                    gate.release.complete(Unit)
                    gate.exited.await()
                    native?.join()
                    old.join()
                    assertTrue(old.isCancelled)
                    assertIs<InitResult.Success>(successor.await())
                    val ready = assertNotNull(f.initialization.currentScope())
                    assertNotSame(original.owner, ready.owner)
                    assertEquals("successor-$stage", f.keys.getString("master_key"))
                    assertNotNull(f.manager.getStateModel())
                    assertSame(ready.owner, assertNotNull(f.cache.canonicalFrames.value).scope?.owner)
                    assertEquals(if (stage == "init") 3 else 2, f.core.inits.value)
                    assertEquals(if (stage == "clear") 1 else 0, f.keys.clears.value)
                    assertTrue(f.notifications.errorMessages.isEmpty(), "Cancelled old work must not notify the successor")
                } finally { f.close() }
            }
        }
    }
}
