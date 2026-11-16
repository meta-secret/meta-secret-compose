package core.metaSecretCore.liveboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import models.apiModels.liveboard.LiveboardOperation

data class LiveboardInboxState(val error: String?, val ready: Boolean, val queue: List<LiveboardOperation>)

/** Pinned display and technical readiness are independent; only actual same-owner proof enables. */
class LiveboardInboxController internal constructor(
    private val session: LiveboardSession,
    private val dependencies: LiveboardSessionDependencies,
) {
    private val observers = CoroutineScope(SupervisorJob() + dependencies.environment.ui)
    private val started = MutableStateFlow(false)
    private val mutableState = MutableStateFlow(LiveboardInboxState(null, false, session.currentMember()?.operations
        .orEmpty()))
    val state: StateFlow<LiveboardInboxState> = mutableState.asStateFlow()
    private class Readiness(
        val target: NativeReadRequirement? = null,
        val confirmed: NativeReadRequirement? = null,
        val proofRequired: Boolean = false,
        val ready: Boolean = false,
    )
    // This private fence closes before any public conceal/error callback can reenter admission.
    private val readiness = MutableStateFlow(Readiness())
    private fun synchronizationReady(trace: LiveboardDecisionRejectionTrace?): Boolean =
        dependencies.reads.synchronization.value.let {
            (!it.loading).observedCurrentness(trace, LiveboardDecisionRejectionSource.POST_CAS_INBOX_SYNC_LOADING) &&
                (!it.failed).observedCurrentness(trace, LiveboardDecisionRejectionSource.POST_CAS_INBOX_SYNC_FAILED) &&
                (it.activeRefreshes == 0).observedCurrentness(trace,
                    LiveboardDecisionRejectionSource.POST_CAS_INBOX_SYNC_ACTIVE_NONZERO)
        }
    internal fun isReady(): Boolean = isReadyObserved(null)
    internal fun isReadyObserved(trace: LiveboardDecisionRejectionTrace?): Boolean =
        readiness.value.ready.observedCurrentness(trace,
            LiveboardDecisionRejectionSource.POST_CAS_INBOX_FENCE_NOT_READY) &&
            session.ownsNativeScopeObserved(trace) && synchronizationReady(trace)

    internal fun start() {
        if (!started.compareAndSet(false, true) || session.isDisposed) return
        observers.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(dependencies.reads.frames, dependencies.reads.synchronization) { frame, sync -> frame to sync }
                .collect { (frame, _) ->
                val currentSync = dependencies.reads.synchronization.value
                if (currentSync.loading || currentSync.activeRefreshes > 0) session.sensitive.suspendForRead()
                if (currentSync.failed) session.sensitive.discardRead()
                val member = session.currentMember()
                val actual = frame != null && frame.scope == session.scope && frame.outcome == NativeReadOutcome
                    .SUCCESS && member != null
                val accepted = accept(frame, actual)
                if (actual && frame != null && member != null) session.observeCanonical(frame, member)
                // Observation can synchronously invalidate readiness or publish a newer frame.
                if (readiness.value === accepted && dependencies.reads.frames.value === frame) {
                    if (actual && frame != null && isReady()) session.sensitive.resumeRead(frame)
                    if (readiness.value === accepted && dependencies.reads.frames.value === frame) {
                        mutableState.update { it.copy(ready = isReady(), queue = member?.operations ?: it.queue) }
                    }
                }
            }
        }
    }
    private fun accept(frame: NativeReadFrame?, actual: Boolean): Readiness {
        while (true) {
            val old = readiness.value
            val required = old.target
            val confirmed = if (actual && required != null && (required in frame!!.coveredRequirements || fulfilled(
                required))) required else old.confirmed
            val ready = actual && (!old.proofRequired || required != null && confirmed === required)
            val next = Readiness(required, confirmed, old.proofRequired, ready)
            if (readiness.compareAndSet(old, next)) return next
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun fulfilled(required: NativeReadRequirement): Boolean {
        if (!required.completion.isCompleted || required.completion.isCancelled) return false
        val proof = try { required.completion.getCompleted() } catch (_: Exception) { return false }
        return proof.scope == session.scope && proof.outcome == NativeReadOutcome.SUCCESS &&
            proof.state?.success == true && required in proof.coveredRequirements
    }
    private fun blockForRefresh(): Readiness = Readiness(proofRequired = true).also { readiness.value = it }
    internal fun prepareCanonical(frame: NativeReadFrame) {
        readiness.update { Readiness(it.target, it.confirmed, it.proofRequired, false) }
        session.sensitive.prepareRead(frame)
    }
    internal fun invalidateReady(): NativeReadRequirement? {
        val fence = blockForRefresh()
        val required = dependencies.reads.requireFreshRead(session.scope)
        readiness.compareAndSet(fence, Readiness(target = required, proofRequired = true))
        return required
    }
    internal fun publishReadiness() {
        if (session.ownsNativeScope()) mutableState.update { it.copy(ready = isReady()) }
    }
    private val errorEvent = MutableStateFlow<Any>(Any())
    private var errorPredecessor: Any? = null
    internal fun errorPublicationStamp(): Any = errorEvent.value
    internal fun publishedErrorEvent(previous: Any): Any? = visibleErrorEvent()?.takeIf {
        errorPredecessor === previous }
    internal fun visibleErrorEvent(): Any? = errorEvent.value.takeIf { mutableState.value.error != null &&
        session.ownsNativeScope() }
    internal fun onError(message: String) {
        if (session.ownsNativeScope()) {
            errorPredecessor = errorEvent.value
            errorEvent.value = Any()
            mutableState.update { it.copy(error = message, ready = isReady()) }
        }
    }
    internal fun onReadFailure() { publishReadiness() }
    fun dismissError() {
        if (!session.ownsNativeScope()) return
        val generation = session.sensitive.generation()
        val dismissedEvent = visibleErrorEvent()
        val acknowledge = dismissedEvent?.let { session.actions.captureDecisionErrors(it) }
        blockForRefresh()
        mutableState.update { it.copy(error = null, ready = false) }
        // Clearing display can synchronously Reset/init a successor; never recapture that owner.
        if (!session.ownsNativeScope() || !session.sensitive.isGeneration(generation)) return
        val required = invalidateReady() ?: return
        if (session.ownsNativeScope() && session.sensitive.isGeneration(generation)) {
            if (dismissedEvent != null && errorEvent.value === dismissedEvent && mutableState.value.error == null)
                acknowledge?.invoke(required)
            dependencies.client.socket.refreshAppState(required)
        }
    }
    internal fun dispose() { observers.cancel() }
}
