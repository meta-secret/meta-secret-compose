package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Fixed observation categories; no identity, payload, native result or authority. */
internal enum class LiveboardDecisionRejectionSource {
    UNOBSERVED,
    ACTION_ADMISSION_MISSING,
    ACTION_SESSION_OR_INTENT_RETIRED,
    JOURNAL_PENDING,
    JOURNAL_WORKER_CANCELLED,
    WORKER_CANCELLED_OR_SELECTION_INVALID,
    WORKER_ADMISSION_CANCELLED,
    WORKER_SELECTION_INVALID,
    AUTH_DECLINED,
    POST_AUTH_SELECTION_INVALID,
    JOURNAL_EXCEPTION_BEFORE_ADMISSION,
    JOURNAL_COMPLETION_BEFORE_ADMISSION,
    JOURNAL_CANCELLED_SETTLEMENT,
    RAW_OWNER_ENTRY_RETIRED,
    RAW_FAILURE_BEFORE_ADMISSION,
    ADMIT_SCOPE_OR_SELECTION_INVALID,
    ADMIT_PHASE_NOT_QUEUED,
    ADMIT_CAS_LOST,
    ADMIT_POST_CAS_SELECTION_INVALID,
    POST_CAS_SENSITIVE_HIDDEN,
    POST_CAS_SENSITIVE_SUSPENDED,
    POST_CAS_SENSITIVE_DISPOSED,
    POST_CAS_SENSITIVE_GENERATION_CHANGED,
    POST_CAS_MEMBER_DISPOSED,
    POST_CAS_MEMBER_OWNER_INVALID,
    POST_CAS_MEMBER_FRAME_ABSENT,
    POST_CAS_MEMBER_SCOPE_MISMATCH,
    POST_CAS_MEMBER_OUTCOME_NOT_SUCCESS,
    POST_CAS_MEMBER_STATE_ABSENT,
    POST_CAS_MEMBER_DECODE_UNAVAILABLE,
    POST_CAS_MEMBER_IDENTITY_MISMATCH,
    POST_CAS_MEMBER_FRAME_REPLACED,
    POST_CAS_MEMBER_OWNER_RECHECK_INVALID,
    POST_CAS_INBOX_FENCE_NOT_READY,
    POST_CAS_INBOX_DISPOSED,
    POST_CAS_INBOX_OWNER_INVALID,
    POST_CAS_INBOX_SYNC_LOADING,
    POST_CAS_INBOX_SYNC_FAILED,
    POST_CAS_INBOX_SYNC_ACTIVE_NONZERO,
    POST_CAS_DECISION_IDENTITY_ABSENT,
    POST_CAS_DECISION_APPROVE_FALSE,
    POST_CAS_DECISION_DECLINE_FALSE,
}

/** Pure atomic metadata only; a losing worker cannot publish over the winning journal result. */
internal class LiveboardDecisionRejectionTrace {
    private data class State(
        val candidate: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
        val published: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
        val finished: Boolean = false,
        val currentness: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
    )
    private val state = MutableStateFlow(State())

    fun currentnessSource(): LiveboardDecisionRejectionSource = runCatching { state.value.currentness }
        .getOrDefault(LiveboardDecisionRejectionSource.UNOBSERVED)

    fun beginCurrentness() {
        runCatching { state.update { old -> if (old.finished) old else old.copy(
            currentness = LiveboardDecisionRejectionSource.UNOBSERVED) } }
    }

    fun noteCurrentness(source: LiveboardDecisionRejectionSource) {
        runCatching { state.update { old ->
            if (old.finished || old.currentness != LiveboardDecisionRejectionSource.UNOBSERVED) old
            else old.copy(currentness = source)
        } }
    }

    fun promoteCurrentness() {
        val observed = currentnessSource()
        note(if (observed == LiveboardDecisionRejectionSource.UNOBSERVED)
            LiveboardDecisionRejectionSource.ADMIT_POST_CAS_SELECTION_INVALID else observed)
    }

    fun source(): LiveboardDecisionRejectionSource = runCatching { state.value.published }
        .getOrDefault(LiveboardDecisionRejectionSource.UNOBSERVED)

    fun note(source: LiveboardDecisionRejectionSource) {
        runCatching { state.update { old ->
            if (old.finished || old.candidate != LiveboardDecisionRejectionSource.UNOBSERVED) old
            else old.copy(candidate = source)
        } }
    }

    fun direct(source: LiveboardDecisionRejectionSource) {
        note(source)
        finish(false, source)
    }

    // Only the winning finish calls this; source=null selects its worker's closest actual branch.
    fun finish(entered: Boolean, source: LiveboardDecisionRejectionSource? = null) {
        runCatching { state.update { old ->
            if (old.finished) old else old.copy(finished = true, published =
                if (entered) LiveboardDecisionRejectionSource.UNOBSERVED else source ?: old.candidate)
        } }
    }
}

/** Only the consumed cancellation operand and already-recorded currentness may classify this branch. */
internal fun LiveboardDecisionRejectionTrace?.noteWorkerRejection(cancelled: Boolean) {
    if (this == null) return
    val observed = if (cancelled) LiveboardDecisionRejectionSource.WORKER_ADMISSION_CANCELLED
        else currentnessSource()
    note(if (observed == LiveboardDecisionRejectionSource.UNOBSERVED)
        LiveboardDecisionRejectionSource.WORKER_SELECTION_INVALID else observed)
}

internal fun LiveboardDecisionRejectionTrace?.observedRejectionSource(): LiveboardDecisionRejectionSource =
    this?.source() ?: LiveboardDecisionRejectionSource.UNOBSERVED

/** Return the already-consulted operand unchanged; null tracing never records or schedules work. */
internal fun Boolean.observedCurrentness(
    trace: LiveboardDecisionRejectionTrace?,
    source: LiveboardDecisionRejectionSource,
): Boolean {
    if (trace != null && !this) trace.noteCurrentness(source)
    return this
}

internal fun Boolean.observedCurrentnessRejection(
    trace: LiveboardDecisionRejectionTrace?,
    source: LiveboardDecisionRejectionSource,
): Boolean {
    if (trace != null && this) trace.noteCurrentness(source)
    return this
}

internal fun <T> T?.observedCurrentnessValue(
    trace: LiveboardDecisionRejectionTrace?,
    source: LiveboardDecisionRejectionSource,
): T? {
    if (trace != null && this == null) trace.noteCurrentness(source)
    return this
}
