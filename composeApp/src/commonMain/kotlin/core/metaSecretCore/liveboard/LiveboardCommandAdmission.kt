package core.metaSecretCore.liveboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

/** Only admit() runs inside native invocation: a fixed private CAS, never a callback. */
internal class LiveboardEntryAdmission(
    private val scope: NativeReadScope,
    val selection: LiveboardEntrySelection? = null,
    payload: LiveboardDraft? = null,
) {
    private enum class Phase { Queued, Admitted, Cancelled }
    private class State(val phase: Phase, val payload: LiveboardDraft?)
    private val state = MutableStateFlow(State(Phase.Queued, payload))
    val isAdmitted: Boolean get() = state.value.phase == Phase.Admitted
    val isCancelled: Boolean get() = state.value.phase == Phase.Cancelled

    fun admit(expected: NativeReadScope): Boolean = admitObserved(expected, null)

    fun admitObserved(expected: NativeReadScope, trace: LiveboardDecisionRejectionTrace?): Boolean {
        if (expected != scope || selection?.isCurrent() == false) {
            trace?.note(LiveboardDecisionRejectionSource.ADMIT_SCOPE_OR_SELECTION_INVALID)
            return false
        }
        val old = state.value
        if (old.phase != Phase.Queued) {
            trace?.note(LiveboardDecisionRejectionSource.ADMIT_PHASE_NOT_QUEUED)
            return false
        }
        if (selection?.subject is LiveboardEntrySubject.Create && old.payload == null) return false
        val admitted = State(Phase.Admitted, old.payload)
        if (!state.compareAndSet(old, admitted)) {
            trace?.note(LiveboardDecisionRejectionSource.ADMIT_CAS_LOST)
            return false
        }
        if (selection?.isCurrentObserved(trace) == false) {
            state.compareAndSet(admitted, State(Phase.Cancelled, null))
            trace?.promoteCurrentness()
            return false
        }
        return true
    }

    fun cancelQueued(): Boolean {
        while (true) {
            val old = state.value
            if (old.phase != Phase.Queued) return false
            if (state.compareAndSet(old, State(Phase.Cancelled, null))) return true
        }
    }

    fun payload(): LiveboardDraft? = state.value.takeIf { it.phase == Phase.Admitted }?.payload
    fun discardPayload() {
        while (true) {
            val old = state.value
            if (old.payload == null || state.compareAndSet(old, State(old.phase, null))) return
        }
    }
}

internal class LiveboardCommandAdmission(
    private val scope: NativeReadScope,
    val intent: LiveboardRemoveIntent,
    selection: LiveboardEntrySelection? = null,
) {
    var job: Job? = null
    val entry = LiveboardEntryAdmission(scope, selection)
    private val result = CompletableDeferred<NativeLiveboardOutcome<LiveboardProducerReceipt>>(parent = null)
    private val nativeSettled = CompletableDeferred<Unit>(parent = null)

    val isAdmitted: Boolean get() = entry.isAdmitted
    val isCancelled: Boolean get() = entry.isCancelled

    fun admit(expected: NativeReadScope): Boolean =
        entry.admit(expected)

    fun cancelQueued(): Boolean = entry.cancelQueued()
    suspend fun awaitResult(): NativeLiveboardOutcome<LiveboardProducerReceipt> = result.await()
    suspend fun awaitNativeSettled() = nativeSettled.await()

    // Both completions occur outside every native/physical/lifecycle fence.
    fun complete(value: NativeLiveboardOutcome<LiveboardProducerReceipt>) { result.complete(value) }
    fun settleNative() { nativeSettled.complete(Unit) }
}

/** A skipped marker never impersonates an entered native exception or uncertain write. */
internal sealed interface LiveboardNativeEntry {
    data class Called(val raw: String) : LiveboardNativeEntry
    data object Skipped : LiveboardNativeEntry
}
