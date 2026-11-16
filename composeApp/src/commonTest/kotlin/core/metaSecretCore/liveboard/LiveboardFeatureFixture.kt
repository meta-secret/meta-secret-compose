package core.metaSecretCore.liveboard

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProvider
import core.BiometricAuthenticatorInterface
import core.DebugLoggerInterface
import core.DeviceInfoProviderInterface
import core.NotificationCoordinator
import core.NotificationCoordinatorInterface
import core.NotificationState
import core.errors.ErrorMapper
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManager
import core.metaSecretCore.MetaSecretSocketHandler
import core.metaSecretCore.NoopMetaSecretSocketClient
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import models.apiModels.liveboard.LiveboardOperationIdentity
import models.apiModels.liveboard.LiveboardStateDecoder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import testutils.FakeDebugLogger
import testutils.FakeKeyValueStorage
import testutils.FakeLogFormatter
import testutils.FakeStringProvider
import testutils.FakeVaultStatsProvider
import ui.scenes.profilescreen.ProfileEvents
import ui.scenes.profilescreen.ProfileNavigationEvent
import ui.scenes.profilescreen.ProfileScreenViewModel

internal class FeatureAuthReply(val success: () -> Unit, val error: (String) -> Unit)

/** Captures the existing OS port callback; it does not grant native permission or readiness. */
internal class FeatureBiometric : BiometricAuthenticatorInterface {
    var held = false
    private val replies = Channel<FeatureAuthReply>(Channel.UNLIMITED)
    var calls = 0
    override fun isBiometricAvailable() = true
    override fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit, onFallback: () -> Unit) {
        calls++
        if (held) check(replies.trySend(FeatureAuthReply(onSuccess, onError)).isSuccess) else onSuccess()
    }
    override fun openAppSettings() = Unit
    suspend fun take() = replies.receive()
    fun clear() { while (replies.tryReceive().isSuccess) Unit }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class LiveboardFeatureFixture {
    val io = NativeCheckedIo()
    val main = NativeReentrantDispatcher()
    init { Dispatchers.setMain(main) }
    val trace = NativeRegistrationTrace()
    val core = LiveboardFeatureCore(trace)
    val keys = NativeRegistrationKeys(trace)
    val auth = FeatureBiometric()
    val privacy = LiveboardPrivacyOwner()
    val notifications = NotificationCoordinator(privacy)
    private val logger = FakeDebugLogger()
    private val storage = FakeKeyValueStorage()
    private val strings = FakeStringProvider()
    private val environment = NativeReadEnvironment(io, main)
    val cache = AppStateCacheProvider(logger, storage, Dispatchers.Unconfined)
    val initialization = NativeInitializationOwner(core, environment)
    val operations = NativeOperationPort(initialization, environment)
    val reads = NativeCanonicalReadOwner(NativeReadDependencies(core, logger, cache, FakeLogFormatter()), NativeReadRuntime(initialization, environment, privacy))
    private val lifecycle = NativeKeyLifecycle(NativeKeyPorts(core, keys), initialization, environment, reads)
    private val reset = NativeLocalReset(initialization, lifecycle, cache)
    val manager = MetaSecretAppManager(
        NativeManagerPorts(core, keys, storage, logger), NativeManagerPresentation(notifications, ErrorMapper(strings), FakeLogFormatter(), cache),
        reads, NativeManagerRuntime(lifecycle, operations),
    )
    val socket = MetaSecretSocketHandler(
        NativeSocketPorts(core, logger, notifications, ErrorMapper(strings)), NativeSocketPresentation(cache, strings),
        NativeSocketRuntime(NoopMetaSecretSocketClient(), operations, main, main), reads,
    )
    val checked = NativeLiveboardOperations(LiveboardCoreAdapter(core), operations, environment)
    val factory = LiveboardSessionFactory(checked, reads, operations, environment,
        LiveboardClientPorts(privacy, auth, socket, notifications, strings))
    private val jobs = mutableListOf<Job>()
    private val gates = mutableListOf<NativeTransitionGate>()
    private val sessions = mutableSetOf<LiveboardSession>()
    private val profiles = mutableListOf<ProfileScreenViewModel>()
    init {
        startKoin { modules(module {
            single<DebugLoggerInterface> { this@LiveboardFeatureFixture.logger }
            single<NotificationCoordinatorInterface> { this@LiveboardFeatureFixture.notifications }
        }) }
    }
    fun <T : Job> track(job: T) = job.also { jobs.add(it) }
    fun run(task: NativeHeldPublication, scope: CoroutineScope) = track(scope.launch(Dispatchers.IO) { task.publish() })
    fun hold(route: String, failure: Exception? = null) = NativeTransitionGate(failure).also { gates.add(it); core.hold(route, it) }
    suspend fun initialize(key: String = "feature-key-a") {
        keys.saveString("master_key", key)
        assertIs<InitResult.Success>(manager.initWithSavedKey())
    }
    suspend fun proof(): NativeReadFrame {
        val requirement = assertNotNull(reads.requireFreshRead())
        val state = assertNotNull(manager.getStateModel())
        val frame = assertNotNull(reads.frames.value)
        assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertSame(state, frame.state)
        assertSame(frame, requirement.completion.await())
        return frame
    }
    fun session(frame: NativeReadFrame) = assertNotNull(factory.open(frame)).also { sessions.add(it) }
    suspend fun start(): LiveboardSession { initialize(); return session(proof()) }
    fun identity(kind: LiveboardKind): LiveboardOperationIdentity = assertNotNull(
        LiveboardStateDecoder.decode(assertNotNull(reads.frames.value?.state)),
    ).operations.first { it.identity.kind == kind }.identity
    suspend fun profile(): ProfileScreenViewModel {
        assertNotNull(manager.getStateModel()); socket.pauseRefreshesAndAwait()
        return ProfileScreenViewModel(object : DeviceInfoProviderInterface {
            override fun getAppVersion() = "test"
            override fun getDeviceMake() = "test"
            override fun getDeviceId() = "device-a"
        }, socket, FakeVaultStatsProvider(), reset).also { profiles.add(it) }
    }
    suspend fun successor(profile: ProfileScreenViewModel): LiveboardSession {
        profile.handle(ProfileEvents.ResetAllData)
        profile.navigationEvent.first { it == ProfileNavigationEvent.NavigateToSignIn }
        core.fresh(); core.failRead = false; auth.held = false
        initialize("feature-key-b")
        return session(proof())
    }
    fun observeErrors(scope: CoroutineScope, observer: (String) -> Unit) = track(scope.launch(main,
        start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
        notifications.notificationState.collect { state ->
            if (state is NotificationState.Visible && state.isError) observer(state.message)
        }
    })
    suspend fun seenError(session: LiveboardSession, message: String) {
        session.inbox.state.first { it.error == message }
    }
    suspend fun settleSocket() {
        cache.synchronization.first { it.activeRefreshes == 0 && !it.loading }
    }
    fun disclosuresEmpty(session: LiveboardSession) = session.sensitive.state.value.revealed.isEmpty() &&
        session.sensitive.copyValue("pass-a") == null && session.sensitive.copyValue("pass-b") == null
    fun privateEmpty(session: LiveboardSession) = session.sensitive.state.value.draft == null && disclosuresEmpty(session)
    suspend fun disclose(session: LiveboardSession, id: String = "pass-a") {
        session.sensitive.hide(id)
        val before = core.calls.value.size
        check(session.sensitive.show(id))
        assertEquals(before + 1, core.calls.value.size)
        assertEquals(NativeCall(if (id == "pass-a") "local" else "recovered", listOf(if (id == "pass-a") id else FeatureIds.claim)), core.calls.value.last())
        check(session.sensitive.state.value.revealed[id]?.secretId == id)
    }
    suspend fun close() = withContext(NonCancellable) {
        withTimeout(5_000) {
            io.resumeInline(); gates.forEach { it.release.complete(Unit) }; io.releaseAll()
            sessions.forEach { it.dispose() }
            profiles.forEach { it.viewModelScope.cancel() }
            jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
            sessions.forEach { it.awaitSettled() }
            profiles.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
            socket.pauseRefreshesAndAwait(); socket.onAppBackground()
            notifications.dismiss(); auth.clear(); stopKoin(); Dispatchers.resetMain()
        }
    }
}
