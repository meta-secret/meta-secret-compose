package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_SHOW_ATTEMPTS = 32
private const val MAX_SHOW_RECORD_BYTES = 512
private const val MAX_POST_CYCLES = 65535

internal enum class ShowObservationFlag { TRUE, FALSE, UNKNOWN }
internal enum class ShowObservationStage { RENDER_REJECTED, POST_REGISTRATION, SAME_JOB_COMPLETION }
internal enum class ShowObservationCompletion { UNOBSERVED, COMPLETED, CANCELLED, THREW }
internal enum class ShowObservationRaw { UNOBSERVED, VALUE, FAILURE, RETIRED }
internal enum class ShowObservationPhase {
    UNOBSERVED, RENDER_REJECTED, ROOT_BODY, NOT_READY, NO_SELECTION, NOT_SHOWABLE, MISSING_CLAIM,
    AUTH_DECLINED, POST_AUTH_RETIRED, RAW_PENDING, RAW_RETURNED, RAW_NOT_VALUE, DECODE_REJECTED,
    DELIVERY_REJECTED, DELIVERY_STORED, UI_DELIVERY_FALSE, UI_DELIVERY_TRUE,
}

internal data class ShowObservationProgress(
    val phase: ShowObservationPhase = ShowObservationPhase.UNOBSERVED,
    val checkedCallStarted: Boolean = false,
    val raw: ShowObservationRaw = ShowObservationRaw.UNOBSERVED,
    val returned: ShowObservationFlag = ShowObservationFlag.UNKNOWN,
)

internal data class ShowObservationFacts(
    val rendered: ShowObservationFlag,
    val session: ShowObservationFlag = ShowObservationFlag.UNKNOWN,
    val progress: ShowObservationProgress = ShowObservationProgress(),
)

internal data class ShowObservationState(
    val facts: ShowObservationFacts,
    val claimed: Boolean = false,
    val initialReserved: Boolean = false,
    val finishing: Boolean = false,
    val finalReserved: Boolean = false,
)

/** Immutable fixed metadata only; no arbitrary string, result, identity or business reference. */
class LiveboardShowObservationRecord internal constructor(
    internal val attempt: Int,
    internal val stage: ShowObservationStage,
    internal val facts: ShowObservationFacts,
    internal val completion: ShowObservationCompletion = ShowObservationCompletion.UNOBSERVED,
    internal val rejection: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
) {
    internal var postDelivery: ShowPostFacts? = null
        private set
    private var postDeliveryTag = false
    internal var guardFacts: ShowGuardFacts? = null
        private set
    private var guardTag = false
    internal constructor(attempt: Int, guard: ShowGuardFacts) : this(attempt,
        ShowObservationStage.SAME_JOB_COMPLETION, ShowObservationFacts(ShowObservationFlag.UNKNOWN)) {
        guardFacts = guard
        guardTag = true
    }
    internal constructor(attempt: Int, post: ShowPostFacts) : this(attempt,
        ShowObservationStage.SAME_JOB_COMPLETION, ShowObservationFacts(ShowObservationFlag.UNKNOWN)) {
        postDelivery = post
        postDeliveryTag = true
    }
    internal val variant: ShowObservationVariant get() = when {
        attempt !in 1..MAX_SHOW_ATTEMPTS -> ShowObservationVariant.UNRECOGNIZED
        guardTag && guardFacts != null && !postDeliveryTag && postDelivery == null ->
            ShowObservationVariant.CONTINUITY_V1
        guardTag || guardFacts != null -> ShowObservationVariant.UNRECOGNIZED
        postDeliveryTag && postDelivery != null -> ShowObservationVariant.POST_DELIVERY_V1
        !postDeliveryTag && postDelivery == null -> ShowObservationVariant.SHOW_V1
        else -> ShowObservationVariant.UNRECOGNIZED
    }
}
internal enum class ShowObservationVariant { SHOW_V1, POST_DELIVERY_V1, CONTINUITY_V1, UNRECOGNIZED }

/** The owner is a private diagnostic token, never a Root, Session, Job or native scope. */
class LiveboardShowObservationTicket internal constructor(
    private val owner: Any,
    internal val attempt: Int,
    rendered: Boolean,
    guardWake: ShowGuardWake? = null,
) {
    internal val state = MutableStateFlow(ShowObservationState(ShowObservationFacts(
        if (rendered) ShowObservationFlag.TRUE else ShowObservationFlag.FALSE)))
    internal val rejection = LiveboardDecisionRejectionTrace()
    internal val post = LiveboardShowPostObserver()
    internal val guards = LiveboardShowGuardObserver(guardWake)
    internal fun belongsTo(expected: Any): Boolean = owner === expected

    internal fun phase(phase: ShowObservationPhase) {
        runCatching { state.update { old -> if (old.finishing) old else old.copy(facts = old.facts.copy(
            progress = old.facts.progress.copy(phase = phase))) } }
    }

    internal fun body(sessionPresent: Boolean, generation: Any? = null) {
        runCatching { state.update { old -> if (old.finishing) old else old.copy(facts = old.facts.copy(
            session = if (sessionPresent) ShowObservationFlag.TRUE else ShowObservationFlag.FALSE,
            progress = old.facts.progress.copy(phase = ShowObservationPhase.ROOT_BODY))) } }
        post.bind(generation)
    }

    internal fun checkedCallStarted() {
        runCatching { state.update { old -> if (old.finishing) old else old.copy(facts = old.facts.copy(
            progress = old.facts.progress.copy(checkedCallStarted = true))) } }
    }

    internal fun raw(raw: ShowObservationRaw) {
        runCatching { state.update { old -> if (old.finishing) old else old.copy(facts = old.facts.copy(
            progress = old.facts.progress.copy(phase = ShowObservationPhase.RAW_RETURNED, raw = raw))) } }
    }

    internal fun returned(value: Boolean) {
        runCatching { state.update { old -> if (old.finishing) old else old.copy(facts = old.facts.copy(
            progress = old.facts.progress.copy(returned = if (value) ShowObservationFlag.TRUE
                else ShowObservationFlag.FALSE))) } }
    }
}

/** Pure metadata reservations; no callback, serialization or sink runs inside a CAS. */
internal class LiveboardShowObservations {
    private val owner = Any()
    private class Registry(val issued: Int = 0, val tickets: List<LiveboardShowObservationTicket> = emptyList(),
        val closed: Boolean = false)
    private val registry = MutableStateFlow(Registry())
    private val guardWake = ShowGuardWake()
    val guardChanges get() = guardWake.changes
    fun guardRecords(): List<LiveboardShowObservationRecord> = runCatching {
        val old = registry.value
        if (old.closed) emptyList() else old.tickets.flatMap { ticket ->
            ticket.guards.drain().map { LiveboardShowObservationRecord(ticket.attempt, it) }
        }
    }.getOrDefault(emptyList())
    internal val guardRegistryOpen: Boolean get() = !registry.value.closed

    fun begin(enabled: Boolean, rendered: Boolean): LiveboardShowObservationTicket? = runCatching {
        var ticket: LiveboardShowObservationTicket? = null
        if (enabled) {
            while (ticket == null) {
                val old = registry.value
                if (old.closed || old.issued >= MAX_SHOW_ATTEMPTS) break
                val candidate = LiveboardShowObservationTicket(owner, old.issued + 1, rendered, guardWake)
                if (registry.compareAndSet(old, Registry(old.issued + 1, old.tickets + candidate))) ticket = candidate
            }
        }
        ticket
    }.getOrNull()

    fun claim(ticket: LiveboardShowObservationTicket): Boolean = runCatching {
        var claimed = false
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (old.claimed || old.initialReserved || old.finishing) break
                if (ticket.state.compareAndSet(old, old.copy(claimed = true))) {
                    claimed = true
                    return@runCatching claimed
                }
            }
        }
        claimed
    }.getOrDefault(false)

    fun rejected(ticket: LiveboardShowObservationTicket): LiveboardShowObservationRecord? = runCatching {
        var record: LiveboardShowObservationRecord? = null
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (old.claimed || old.initialReserved || old.facts.rendered != ShowObservationFlag.FALSE) break
                val next = old.copy(claimed = true, initialReserved = true, finishing = true,
                    finalReserved = true, facts = old.facts.copy(progress = old.facts.progress.copy(
                        phase = ShowObservationPhase.RENDER_REJECTED)))
                if (ticket.state.compareAndSet(old, next)) {
                    record = LiveboardShowObservationRecord(ticket.attempt, ShowObservationStage.RENDER_REJECTED,
                        next.facts)
                    return@runCatching record
                }
            }
        }
        record
    }.getOrNull()

    fun registered(ticket: LiveboardShowObservationTicket): LiveboardShowObservationRecord? = runCatching {
        var record: LiveboardShowObservationRecord? = null
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (!old.claimed || old.initialReserved || old.finishing) break
                val next = old.copy(initialReserved = true)
                if (ticket.state.compareAndSet(old, next)) {
                    record = LiveboardShowObservationRecord(ticket.attempt, ShowObservationStage.POST_REGISTRATION,
                        next.facts)
                    return@runCatching record
                }
            }
        }
        record
    }.getOrNull()

    /** Only the original Show Job completion calls this, after its business finally and lease drain. */
    fun completed(ticket: LiveboardShowObservationTicket, cancelled: Boolean, threw: Boolean
        ): LiveboardShowObservationRecord? = runCatching {
        var record: LiveboardShowObservationRecord? = null
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (!old.claimed || !old.initialReserved || old.finishing) break
                val freezing = old.copy(finishing = true)
                if (ticket.state.compareAndSet(old, freezing)) {
                    // Outside both the native lease and metadata CAS; source() reads published, not candidate.
                    ticket.rejection.finish(freezing.facts.progress.checkedCallStarted)
                    val source = ticket.rejection.source()
                    val frozen = freezing.copy(finalReserved = true)
                    if (ticket.state.compareAndSet(freezing, frozen)) {
                        val completion = when {
                            cancelled -> ShowObservationCompletion.CANCELLED
                            threw -> ShowObservationCompletion.THREW
                            else -> ShowObservationCompletion.COMPLETED
                        }
                        record = LiveboardShowObservationRecord(ticket.attempt,
                            ShowObservationStage.SAME_JOB_COMPLETION, frozen.facts, completion, source)
                    }
                    return@runCatching record
                }
            }
        }
        record
    }.getOrNull()

    fun collected(generation: Any, empty: Boolean,
        present: List<LiveboardShowObservationTicket>,
        writer: ShowSnapshotWriter = ShowSnapshotWriter.UNOBSERVED
    ): List<LiveboardShowObservationRecord> = runCatching {
        val old = registry.value
        if (old.closed) emptyList() else old.tickets.mapNotNull { ticket ->
            val store = if (present.any { it === ticket }) ShowPostStore.TICKET_PRESENT
                else if (empty) ShowPostStore.MAP_EMPTY else ShowPostStore.NONEMPTY_TICKET_UNOBSERVED
            ticket.post.collect(generation, store,
                ticket.state.value.facts.progress.returned == ShowObservationFlag.TRUE, writer)?.let {
                LiveboardShowObservationRecord(ticket.attempt, it) }
        }
    }.getOrDefault(emptyList())
    fun committed(ticket: LiveboardShowObservationTicket): LiveboardShowObservationRecord? = runCatching {
        if (!owns(ticket) || registry.value.closed) null
        else ticket.post.committed()?.let { LiveboardShowObservationRecord(ticket.attempt, it) }
    }.getOrNull()
    fun close() {
        while (true) {
            val old = registry.value
            if (old.closed) return
            if (registry.compareAndSet(old, Registry(old.issued, closed = true))) {
                old.tickets.forEach { it.post.close(); it.guards.close() }
                return
            }
        }
    }
    private fun owns(ticket: LiveboardShowObservationTicket): Boolean = ticket.belongsTo(owner) &&
        ticket.attempt in 1..MAX_SHOW_ATTEMPTS
}

internal fun LiveboardShowObservations.guardsOpen(): Boolean = guardRegistryOpen

/** Return the already-consulted operand unchanged; null diagnostics read no business state. */
internal fun Boolean.observedShowRejection(
    observation: LiveboardShowObservationTicket?,
    phase: ShowObservationPhase,
): Boolean {
    if (observation != null && !this) observation.phase(phase)
    return this
}

internal fun LiveboardShowObservationRecord.encodedShowObservation(): String? {
    if (attempt !in 1..MAX_SHOW_ATTEMPTS) return null
    return when {
        variant == ShowObservationVariant.CONTINUITY_V1 -> guardFacts?.let { encodedShowGuard(attempt, it) }
        variant == ShowObservationVariant.POST_DELIVERY_V1 -> encodedPostDelivery()
        variant != ShowObservationVariant.SHOW_V1 -> null
        else -> encodedShowV1()
    }
}

private fun LiveboardShowObservationRecord.encodedShowV1(): String? {
    val payload = buildJsonObject {
        put("VERSION", 1)
        put("KIND", "SHOW")
        put("ATTEMPT", attempt)
        put("STAGE", stage.name)
        put("RENDERED", facts.rendered.name)
        put("SESSION", facts.session.name)
        put("PHASE", facts.progress.phase.name)
        put("ENTRY", if (facts.progress.checkedCallStarted) "CHECKED_CALL_STARTED" else "UNOBSERVED")
        put("RAW", facts.progress.raw.name)
        put("RETURNED", facts.progress.returned.name)
        put("COMPLETION", completion.name)
        put("REJECTION_SOURCE", rejection.name)
    }
    val line = "LIVEBOARD_SHOW_OBSERVATION $payload"
    return line.takeIf { ("E2E_DIAG " + it + "\n").encodeToByteArray().size <= MAX_SHOW_RECORD_BYTES }
}


private fun LiveboardShowObservationRecord.encodedPostDelivery(): String? {
    val post = postDelivery
    return if (post == null) null
        else if (post.order < 0 || post.cycles !in 0..MAX_POST_CYCLES) null
        else if (post.lastPresent !in 0..post.order || post.lastEmpty !in 0..post.order) null
        else if ((post.event == ShowPostEvent.VALUE_BRANCH_COMMITTED || post.event == ShowPostEvent.CAPPED) &&
            post.writer != ShowSnapshotWriter.UNOBSERVED) null
        else {
        val payload = buildJsonObject {
            put("VERSION", 1)
            put("KIND", "SHOW_POST_DELIVERY")
            put("ATTEMPT", attempt)
            put("EVENT", post.event.name)
            put("ORDER", post.order)
            put("STORE", post.store.name)
            put("CYCLES", post.cycles)
            put("LAST_PRESENT", post.lastPresent)
            put("LAST_EMPTY", post.lastEmpty)
            put("ASSOCIATION_LOSS", if (post.loss) "TRUE" else "FALSE")
            put("COVERAGE", "COLLECTED_SNAPSHOTS_ONLY")
            put("REASON", post.writer.name)
            put("COMMIT", if (post.event == ShowPostEvent.VALUE_BRANCH_COMMITTED) "VALUE_BRANCH_COMMITTED"
                else "UNOBSERVED")
        }
        val line = "LIVEBOARD_SHOW_POST_DELIVERY $payload"
        line.takeIf { ("E2E_DIAG " + it + "\n").encodeToByteArray().size <= MAX_SHOW_RECORD_BYTES }
        }
}
