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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
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
import testutils.FakeBiometricAuthenticator
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent
import ui.scenes.profilescreen.ProfileScreenViewModel

internal data class SessionRawRoute(
    val name: String,
    val invoke: suspend (NativeLiveboardOperations, NativeReadScope, String) -> NativeInvocation<String>,
)

internal fun sessionRawRoutes() = listOf(
    SessionRawRoute("show") { p, s, claim -> p.showRecovered(s, claim) },
    SessionRawRoute("accept") { p, s, claim -> p.acceptRecover(s, claim) },
    SessionRawRoute("decline") { p, s, claim -> p.declineRecover(s, claim) },
)

/** These are real synchronous Core boundaries; the fixture grants no native admission. */
internal class SessionCommandCore(trace: NativeRegistrationTrace) : MetaSecretCoreInterface by NativeRegistrationCore(trace) {
    val calls = MutableStateFlow<List<NativeCall>>(emptyList())
    val thrown = MutableStateFlow(0)
    val forbidden = MutableStateFlow<List<String>>(emptyList())
    val producer = LiveboardNativeFixtures.envelope(
        LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision()),
    )
    var gate: NativeTransitionGate? = null
    fun rawValue(name: String, claim: String) = "fixture-$name::$claim"
    private fun call(name: String, argument: String, result: String): String = runBlocking {
        calls.update { it + NativeCall(name, listOf(argument)) }
        try {
            gate?.also { gate = null }?.hold()
            result
        } catch (error: Exception) {
            thrown.update { it + 1 }
            throw error
        }
    }
    override fun getAppState() = LiveboardNativeFixtures.member()
    override fun removeMemberChecked(targetDeviceId: String) = call("remove", targetDeviceId, producer)
    override fun showRecovered(claimId: String) = call("show", claimId, rawValue("show", claimId))
    override fun acceptRecover(claimId: String) = call("accept", claimId, rawValue("accept", claimId))
    override fun declineRecover(claimId: String) = call("decline", claimId, rawValue("decline", claimId))
    override fun findClaim(secretId: String): String {
        forbidden.update { it + "findClaim" }; error("Fixture forbids a Claim lookup")
    }
    override fun showLocalSecret(secretId: String): String {
        forbidden.update { it + "showLocalSecret" }; error("Fixture forbids a local Show fallback")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class LiveboardSessionCommandFixture {
    val io = NativeCheckedIo()
    val ui = NativeCheckedIo()
    val main = NativeReentrantDispatcher()
    val trace = NativeRegistrationTrace()
    val core = SessionCommandCore(trace)
    val keys = NativeRegistrationKeys(trace)
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val strings = FakeStringProvider()
    private val formatter = FakeLogFormatter()
    private val notifications = FakeNotificationCoordinator()
    private val environment = NativeReadEnvironment(io, main)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val privacy = LiveboardPrivacyOwner()
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, formatter), NativeReadRuntime(initialization, environment, privacy))
    private val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger), NativeManagerPresentation(notifications, ErrorMapper(strings), formatter, cache),
        reads, NativeManagerRuntime(lifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, main, main), reads,
    )
    val checked = NativeLiveboardOperations(LiveboardCoreAdapter(core), operations, environment)
    val factory = LiveboardSessionFactory(checked, reads, operations, NativeReadEnvironment(io, ui),
        LiveboardClientPorts(privacy, FakeBiometricAuthenticator(), socket, notifications, strings))
    private val jobs = mutableListOf<Job>()
    private val gates = mutableListOf<NativeTransitionGate>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()
    private val sessions = mutableSetOf<LiveboardSession>()
    init {
        Dispatchers.setMain(main)
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@LiveboardSessionCommandFixture.logger }
            single<NotificationCoordinatorInterface> { this@LiveboardSessionCommandFixture.notifications }
        }) }
    }
    fun <T : Job> track(job: T): T = job.also { jobs.add(it) }
    fun run(task: NativeHeldPublication, scope: CoroutineScope) = track(scope.launch(Dispatchers.IO) { task.publish() })
    fun hold(failure: Exception? = null) = NativeTransitionGate(failure).also { gates.add(it); core.gate = it }
    fun session(frame: NativeReadFrame) = assertNotNull(factory.open(frame)).also { sessions.add(it) }
    fun expectedProducer(): NativeResult<LiveboardProducerReceipt> = LiveboardReceiptDecoder.decodeProducer(core.producer)
    suspend fun initialize(key: String) {
        keys.saveString("master_key", key)
        assertIs<InitResult.Success>(manager.initWithSavedKey())
    }
    suspend fun proof(): NativeReadFrame {
        val target = assertNotNull(reads.requireFreshRead())
        val state = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(cache.canonicalFrames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertSame(state, frame.state)
        assertTrue(target in frame.coveredRequirements)
        assertTrue(target.completion.isCompleted)
        assertSame(frame, target.completion.await())
        return frame
    }
    suspend fun profile(): ProfileScreenViewModel {
        assertNotNull(manager.getStateModel())
        socket.pauseRefreshesAndAwait()
        return ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "device-a"
        }, socket, FakeVaultStatsProvider(), reset).also { profiles.add(it) }
    }
    fun reset(profile: ProfileScreenViewModel) { profile.handle(ProfileEvents.ResetAllData) }
    suspend fun resetFinished(profile: ProfileScreenViewModel) {
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
        assertEquals(1, trace.count("key.clear"))
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            io.resumeInline(); ui.resumeInline()
            gates.forEach { it.release.complete(Unit) }
            io.releaseAll(); ui.releaseAll()
            sessions.forEach { it.dispose() }
            profiles.forEach { it.viewModelScope.cancel() }
            jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
            sessions.forEach { it.awaitSettled() }
            profiles.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
            socket.pauseRefreshesAndAwait(); socket.onAppBackground()
            stopKoin(); Dispatchers.resetMain()
        }
    }
}
