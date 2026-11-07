package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import models.apiModels.liveboard.LiveboardOperationIdentity
import models.apiModels.liveboard.LiveboardOperation
import models.apiModels.liveboard.LiveboardSecret
import models.apiModels.liveboard.LiveboardDevice

/** Only issued producer selections can identify a read-only observation. */
sealed interface LiveboardProducerIntent

class LiveboardCreateIntent internal constructor(
    internal val generation: Any,
    internal val revision: Any,
    val name: String,
    internal val authoredVersion: Any,
    internal val formIdentity: Any,
)

/** Ephemeral presentation after exact canonical completion; carries no input or native result. */
class LiveboardCreateCompletion internal constructor()

class LiveboardRecoverIntent internal constructor(internal val generation: Any, val secretId: String)
class LiveboardDeleteIntent internal constructor(internal val generation: Any, val secretId: String
    ) : LiveboardProducerIntent
class LiveboardDecisionIntent internal constructor(
    internal val generation: Any,
    val identity: LiveboardOperationIdentity,
    val accepted: Boolean,
    internal val hardStamp: Any? = null,
)
class LiveboardContinueIntent internal constructor(
    internal val generation: Any,
    internal val original: LiveboardRemoveIntent,
    internal val reference: LiveboardProducerReference,
)

/** A fresh gate may bind once, only to this original unfinished receiver command. */
internal class LiveboardCommandEntry(admission: LiveboardEntryAdmission, preparing: Boolean = false) {
    private enum class Phase { Preparing, Bound, Closed }
    private class State(val admission: LiveboardEntryAdmission, val phase: Phase)
    private val state = MutableStateFlow(State(admission, if (preparing) Phase.Preparing else Phase.Bound))
    private val readSlot = MutableStateFlow<NativeDecisionRead?>(null)
    val admission: LiveboardEntryAdmission get() = state.value.admission
    val isPreparing: Boolean get() = state.value.phase == Phase.Preparing
    val isCancelled: Boolean get() = state.value.let {
        it.phase == Phase.Closed || it.phase == Phase.Bound && it.admission.isCancelled
    }
    val read: NativeDecisionRead? get() = readSlot.value
    fun attachRead(read: NativeDecisionRead?, reads: NativeCanonicalReadOwner) {
        readSlot.value = read
        if (isCancelled) releaseRead(reads)
    }
    fun releaseRead(reads: NativeCanonicalReadOwner) {
        while (true) {
            val read = readSlot.value ?: return
            if (readSlot.compareAndSet(read, null)) { reads.releaseDecision(read); return }
        }
    }
    fun bind(fresh: LiveboardEntryAdmission): Boolean {
        val old = state.value
        if (old.phase != Phase.Preparing || !state.compareAndSet(old, State(fresh, Phase.Bound))) {
            fresh.cancelQueued()
            return false
        }
        old.admission.cancelQueued()
        return true
    }
    fun revoke(technical: Boolean = false): Boolean {
        while (true) {
            val old = state.value
            if (old.phase == Phase.Preparing) {
                if (technical) { old.admission.cancelQueued(); return false }
                if (!state.compareAndSet(old, State(old.admission, Phase.Closed))) continue
                old.admission.cancelQueued()
                return true
            }
            return old.phase == Phase.Closed || old.admission.cancelQueued() || old.admission.isCancelled
        }
    }
}

/** A returned attempt does not certify a canonical approval or material readiness. */
sealed interface LiveboardDecisionAttempt {
    data class Checked(val receipt: LiveboardCheckedReceipt) : LiveboardDecisionAttempt
    data class Returned(val kind: LiveboardKind, val requestId: String, val accepted: Boolean
        ) : LiveboardDecisionAttempt
}

enum class LiveboardDraftMode { Plain, Seed }

data class LiveboardDraft(
    val name: String,
    val value: String,
    val mode: LiveboardDraftMode = LiveboardDraftMode.Plain,
    val wordCount: Int = 12,
    val words: List<String> = List(MAX_SEED_WORDS) { "" },
) {
    override fun toString(): String = "LiveboardDraft(redacted)"
}
data class LiveboardDisclosure(val secretId: String, val value: String) {
    internal var showObservation: LiveboardShowObservationTicket? = null
        private set
    internal companion object {
        fun observed(id: String, value: String, ticket: LiveboardShowObservationTicket?): LiveboardDisclosure =
            LiveboardDisclosure(id, value).also { it.showObservation = ticket }
    }
    override fun toString(): String = "LiveboardDisclosure(redacted)"
}
data class LiveboardSensitiveSnapshot(
    val draft: LiveboardDraft? = null,
    val revealed: Map<String, LiveboardDisclosure> = emptyMap(),
    val createOpen: Boolean = false,
    val pasteHint: String = "",
    val createCompletion: LiveboardCreateCompletion? = null,
) {
    internal var showWriter: ShowSnapshotWriter? = null
        private set
    internal companion object {
        fun copyRevealed(previous: LiveboardSensitiveSnapshot, revealed: Map<String, LiveboardDisclosure>,
            writer: ShowSnapshotWriter, clearPasteHint: Boolean = false): LiveboardSensitiveSnapshot {
            val next = if (clearPasteHint) previous.copy(revealed = revealed, pasteHint = "")
                else previous.copy(revealed = revealed)
            next.showWriter = writer
            return next
        }
        fun concealed(draft: LiveboardDraft?, createOpen: Boolean,
            completion: LiveboardCreateCompletion?): LiveboardSensitiveSnapshot =
            LiveboardSensitiveSnapshot(draft = draft, createOpen = createOpen, createCompletion = completion)
                .also { it.showWriter = ShowSnapshotWriter.CONCEAL }
    }
    override fun toString(): String = "LiveboardSensitiveSnapshot(redacted)"
}

class LiveboardPasteIntent internal constructor(internal val generation: Any, internal val revision: Any)
enum class LiveboardCreatePhase { Idle, Busy, Submitted, Unknown }
data class LiveboardCreatePresentation(val phase: LiveboardCreatePhase, val canOpen: Boolean, val canSubmit: Boolean)

sealed interface LiveboardActionTarget {
    data object Create : LiveboardActionTarget
    data class Recover(val secretId: String) : LiveboardActionTarget
    data class Delete(val secretId: String) : LiveboardActionTarget
    data class Remove(val targetDeviceId: String) : LiveboardActionTarget
    data class Decision(val identity: LiveboardOperationIdentity) : LiveboardActionTarget
    data class Continue(val targetDeviceId: String) : LiveboardActionTarget
}
enum class LiveboardActionPhase { Selected, Busy, Submitted, Unknown }
data class LiveboardActionPresentation(val target: LiveboardActionTarget, val phase: LiveboardActionPhase,
    val requestId: String? = null)

/** Display correlation from the same typed returned truth; never nearest-item selection. */
internal fun liveboardActionRequest(result: NativeResult<*>?): String? {
    val receipt = (result as? NativeResult.Success<*>)?.value ?: return null
    return when (receipt) {
        is LiveboardCheckedReceipt -> receipt.identity.claimId ?: receipt.identity.requestId
        is LiveboardProducerReceipt -> receipt.identity.requestId
        is LiveboardDecisionAttempt.Checked -> receipt.receipt.identity.claimId ?: receipt.receipt.identity.requestId
        is LiveboardDecisionAttempt.Returned -> receipt.requestId
        else -> null
    }
}

internal fun liveboardActionPhase(result: NativeResult<*>?, pending: Boolean): LiveboardActionPhase = when {
    result is NativeResult.Success<*> -> when (val receipt = result.value) {
        is LiveboardCheckedReceipt -> if (receipt.phase == LiveboardPhase.Unknown) LiveboardActionPhase
            .Unknown else LiveboardActionPhase.Submitted
        is LiveboardDecisionAttempt.Checked -> if (receipt.receipt.phase == LiveboardPhase.Unknown
            ) LiveboardActionPhase.Unknown else LiveboardActionPhase.Submitted
        is LiveboardProducerReceipt -> if (receipt.state.phase == LiveboardPhase.Unknown) LiveboardActionPhase
            .Unknown else LiveboardActionPhase.Submitted
        else -> LiveboardActionPhase.Submitted
    }
    result != null -> LiveboardActionPhase.Unknown
    pending -> LiveboardActionPhase.Busy
    else -> LiveboardActionPhase.Selected
}

/** Closed metadata checks; admission invokes no user/UI callback. */
internal sealed interface LiveboardEntrySubject {
    data class Create(val revision: Any) : LiveboardEntrySubject
    data class Secret(val frozen: LiveboardSecret, val delete: Boolean) : LiveboardEntrySubject
    data class Device(val frozen: LiveboardDevice) : LiveboardEntrySubject
    data class Decision(val identity: LiveboardOperationIdentity, val accepted: Boolean) : LiveboardEntrySubject
    data class Continue(val original: LiveboardRemoveIntent) : LiveboardEntrySubject
    data class Reveal(val frozen: LiveboardSecret, val card: Any) : LiveboardEntrySubject
}

internal class LiveboardEntrySelection(
    val session: LiveboardSession,
    val generation: Any,
    val subject: LiveboardEntrySubject,
) {
    fun isCurrent(): Boolean = isCurrentObserved(null)

    fun isCurrentObserved(trace: LiveboardDecisionRejectionTrace? = null): Boolean {
        trace?.beginCurrentness()
        if (!session.sensitive.isCurrentObserved(generation, trace)) return false
        val member = session.currentMemberObserved(trace) ?: return false
        if (!session.inbox.isReadyObserved(trace)) return false
        return when (val selected = subject) {
            is LiveboardEntrySubject.Create -> member.permissions.canCreateSecret && session.sensitive.isRevision(
                selected.revision)
            is LiveboardEntrySubject.Secret -> member.secrets.any { it == selected.frozen &&
                if (selected.delete) it.canDelete else it.primary.action == "recover" && it.primary.readiness ==
                    "ready" }
            is LiveboardEntrySubject.Device -> member.devices.any { it == selected.frozen && it.canRemove && !it
                .current }
            is LiveboardEntrySubject.Decision -> decisionCurrent(member.operations, selected, trace)
            is LiveboardEntrySubject.Continue -> session.retainedProducer(selected.original) != null
            is LiveboardEntrySubject.Reveal -> member.secrets.any { it == selected.frozen && it.primary.readiness ==
                "ready" } &&
                session.sensitive.isCard(selected.frozen.identity.passId, selected.card)
        }
    }

    private fun decisionCurrent(
        operations: List<LiveboardOperation>,
        selected: LiveboardEntrySubject.Decision,
        trace: LiveboardDecisionRejectionTrace?,
    ): Boolean {
        var matched = false
        var permission = LiveboardDecisionRejectionSource.UNOBSERVED
        val current = operations.any {
            val same = it.identity == selected.identity
            if (trace != null && same) matched = true
            same && if (selected.accepted) it.permissions.canApprove.also { allowed ->
            if (trace != null && !allowed)
                permission = LiveboardDecisionRejectionSource.POST_CAS_DECISION_APPROVE_FALSE
        } else it.permissions.canDecline.also { allowed ->
            if (trace != null && !allowed)
                permission = LiveboardDecisionRejectionSource.POST_CAS_DECISION_DECLINE_FALSE
        } }
        // Only final any(false) interprets these actual local results; a later true records nothing.
        if (trace != null && !current) trace.noteCurrentness(
            if (matched) permission else LiveboardDecisionRejectionSource.POST_CAS_DECISION_IDENTITY_ABSENT)
        return current
    }
}

private const val MAX_SEED_WORDS: Int = 24


internal enum class ShowSnapshotWriter { UNOBSERVED, HIDE_CARD, READ_MASK, READ_RESTORE, CONCEAL }
internal enum class ShowPostStore { UNOBSERVED, TICKET_PRESENT, MAP_EMPTY, NONEMPTY_TICKET_UNOBSERVED }
internal enum class ShowPostEvent {
    FIRST_SAMPLE, FIRST_EMPTY, FIRST_REAPPEARANCE, CYCLE_SUMMARY, VALUE_BRANCH_COMMITTED, CAPPED,
}
internal data class ShowPostFacts(
    val event: ShowPostEvent,
    val order: Long,
    val store: ShowPostStore,
    val cycles: Int,
    val lastPresent: Long,
    val lastEmpty: Long,
    val loss: Boolean,
    val writer: ShowSnapshotWriter = ShowSnapshotWriter.UNOBSERVED,
)
private data class ShowPostState(
    val generation: Any? = null,
    val closed: Boolean = false,
    val store: ShowPostStore = ShowPostStore.UNOBSERVED,
    val order: Long = 0,
    val cycles: Int = 0,
    val lastPresent: Long = 0,
    val lastEmpty: Long = 0,
    val seenPresent: Boolean = false,
    val emptyPending: Boolean = false,
    val loss: Boolean = false,
    val reservations: Int = 0,
)

private const val POST_FIRST = 1
private const val POST_EMPTY = 2
private const val POST_RETURN = 4
private const val POST_COMMIT = 8
private const val POST_CAPPED = 16
private const val POST_MAX_CYCLES = 65535
private const val POST_CYCLE_4 = 4
private const val POST_CYCLE_16 = 16
private const val POST_CYCLE_64 = 64
private const val POST_CYCLE_256 = 256
private const val POST_CYCLE_1024 = 1024
private const val POST_CYCLE_4096 = 4096
private const val POST_CYCLE_16384 = 16384
private const val POST_SLOT_CYCLE_1 = 32
private const val POST_SLOT_CYCLE_4 = 64
private const val POST_SLOT_CYCLE_16 = 128
private const val POST_SLOT_CYCLE_64 = 256
private const val POST_SLOT_CYCLE_256 = 512
private const val POST_SLOT_CYCLE_1024 = 1024
private const val POST_SLOT_CYCLE_4096 = 2048
private const val POST_SLOT_CYCLE_16384 = 4096

/** Only fixed metadata; neither this state nor its changes are observed by the application UI. */
internal class LiveboardShowPostObserver {
    private val state = MutableStateFlow(ShowPostState())
    fun bind(generation: Any?) {
        if (generation == null) return
        state.update { if (it.closed || it.generation != null) it else it.copy(generation = generation) }
    }
    fun collect(generation: Any, store: ShowPostStore, delivered: Boolean,
        writer: ShowSnapshotWriter = ShowSnapshotWriter.UNOBSERVED): ShowPostFacts? = runCatching {
        var facts: ShowPostFacts? = null
        while (true) {
            val old = state.value
            if (postCollectionBlocked(old, generation) || postSampleSuppressed(old, store, delivered)) break
            val next = nextPostState(old, store)
            val event = postEvent(next.reservations and old.reservations.inv())
            if (state.compareAndSet(old, next)) {
                if (event != null) facts = fixedPostFacts(next, event, writer)
                return@runCatching facts
            }
        }
        facts
    }.getOrNull()
    fun committed(): ShowPostFacts? = runCatching {
        var facts: ShowPostFacts? = null
        while (true) {
            val old = state.value
            if (old.closed || old.generation == null || old.reservations and POST_COMMIT != 0) break
            val next = old.copy(reservations = old.reservations or POST_COMMIT)
            if (state.compareAndSet(old, next)) {
                facts = fixedPostFacts(next, ShowPostEvent.VALUE_BRANCH_COMMITTED)
                return@runCatching facts
            }
        }
        facts
    }.getOrNull()
    fun close() { state.update { it.copy(generation = null, closed = true) } }
}

private fun postCollectionBlocked(old: ShowPostState, generation: Any): Boolean =
    old.closed || old.generation !== generation || old.reservations and POST_CAPPED != 0
private fun postSampleSuppressed(old: ShowPostState, store: ShowPostStore, delivered: Boolean): Boolean =
    if (store == old.store) true else !old.seenPresent && !delivered && store != ShowPostStore.TICKET_PRESENT

private fun nextPostState(old: ShowPostState, store: ShowPostStore): ShowPostState {
    val present = store == ShowPostStore.TICKET_PRESENT
    val empty = store == ShowPostStore.MAP_EMPTY
    val returning = present && old.emptyPending
    val cycles = (old.cycles + if (returning) 1 else 0).coerceAtMost(POST_MAX_CYCLES)
    val order = if (old.order == Long.MAX_VALUE) old.order else old.order + 1
    return old.copy(store = store, order = order, cycles = cycles,
        lastPresent = if (present) order else old.lastPresent,
        lastEmpty = if (empty) order else old.lastEmpty,
        seenPresent = old.seenPresent || present,
        emptyPending = if (empty) old.seenPresent else false,
        loss = old.loss || store == ShowPostStore.NONEMPTY_TICKET_UNOBSERVED || !old.seenPresent && !present,
        reservations = old.reservations or postReservations(old, store, cycles))
}
private fun postReservations(old: ShowPostState, store: ShowPostStore, cycles: Int): Int {
    val returning = store == ShowPostStore.TICKET_PRESENT && old.emptyPending
    return (if (old.store == ShowPostStore.UNOBSERVED) POST_FIRST else 0) or
        (if (store == ShowPostStore.MAP_EMPTY && old.seenPresent) POST_EMPTY else 0) or
        (if (returning) POST_RETURN or postMilestone(cycles) else 0) or
        (if (cycles == POST_MAX_CYCLES || old.order == Long.MAX_VALUE) POST_CAPPED else 0)
}
private fun postMilestone(cycles: Int): Int = when (cycles) {
    1 -> POST_SLOT_CYCLE_1
    POST_CYCLE_4 -> POST_SLOT_CYCLE_4
    POST_CYCLE_16 -> POST_SLOT_CYCLE_16
    POST_CYCLE_64 -> POST_SLOT_CYCLE_64
    POST_CYCLE_256 -> POST_SLOT_CYCLE_256
    POST_CYCLE_1024 -> POST_SLOT_CYCLE_1024
    POST_CYCLE_4096 -> POST_SLOT_CYCLE_4096
    POST_CYCLE_16384 -> POST_SLOT_CYCLE_16384
    else -> 0
}
private fun postEvent(reserve: Int): ShowPostEvent? = when {
    reserve and POST_CAPPED != 0 -> ShowPostEvent.CAPPED
    reserve and POST_FIRST != 0 -> ShowPostEvent.FIRST_SAMPLE
    reserve and POST_EMPTY != 0 -> ShowPostEvent.FIRST_EMPTY
    reserve and POST_RETURN != 0 -> ShowPostEvent.FIRST_REAPPEARANCE
    reserve != 0 -> ShowPostEvent.CYCLE_SUMMARY
    else -> null
}
private fun fixedPostFacts(state: ShowPostState, event: ShowPostEvent,
    writer: ShowSnapshotWriter = ShowSnapshotWriter.UNOBSERVED) = ShowPostFacts(event, state.order,
    if (state.reservations and POST_CAPPED != 0) ShowPostStore.UNOBSERVED else state.store,
    state.cycles, state.lastPresent, state.lastEmpty, state.loss || state.reservations and POST_CAPPED != 0,
    if (event == ShowPostEvent.VALUE_BRANCH_COMMITTED || event == ShowPostEvent.CAPPED)
        ShowSnapshotWriter.UNOBSERVED else writer)
