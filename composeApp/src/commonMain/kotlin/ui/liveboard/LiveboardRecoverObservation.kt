package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardDecisionRejectionSource
import core.metaSecretCore.liveboard.LiveboardDecisionRejectionTrace
import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.NativeLiveboardOutcome
import core.metaSecretCore.liveboard.NativeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_RECOVER_TICKETS = 6
private const val MAX_RECOVER_ROW_BYTES = 256

internal enum class RecoverObservationFlag { ZERO, ONE, UNKNOWN }
internal enum class RecoverObservationStage { ROOT_CALLBACK, POST_REGISTRATION, RETURNED, SAME_JOB_COMPLETION }
internal enum class RecoverObservationOutcome {
    UNOBSERVED, NOT_ENTERED, ENTERED_SUCCESS, ENTERED_UNAVAILABLE, ENTERED_UNCERTAIN
}
internal enum class RecoverObservationCompletion { UNOBSERVED, COMPLETED, CANCELLED, THREW }
internal data class RecoverObservationFacts(
    val sessionPresent: RecoverObservationFlag = RecoverObservationFlag.UNKNOWN,
    val intentPresent: RecoverObservationFlag = RecoverObservationFlag.UNKNOWN,
    val bodyReached: RecoverObservationFlag = RecoverObservationFlag.UNKNOWN,
    val outcome: RecoverObservationOutcome = RecoverObservationOutcome.UNOBSERVED,
)

/** Fixed metadata only; never a product identity, result, job or admission capability. */
class LiveboardRecoverObservationRecord internal constructor(
    internal val sequence: Int,
    internal val stage: RecoverObservationStage,
    internal val facts: RecoverObservationFacts,
    internal val completion: RecoverObservationCompletion,
    internal val rejectionSource: LiveboardDecisionRejectionSource,
)

internal data class RecoverTicketState(
    val facts: RecoverObservationFacts = RecoverObservationFacts(),
    val stages: Set<RecoverObservationStage> = emptySet(),
    val completion: RecoverObservationCompletion = RecoverObservationCompletion.UNOBSERVED,
    val rejection: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
)
internal class LiveboardRecoverObservationTicket internal constructor(
    private val owner: Any,
    internal val sequence: Int,
) {
    internal val state = MutableStateFlow(RecoverTicketState())
    internal fun belongsTo(candidate: Any) = owner === candidate
}

/** Owner stores bounded primitive tickets only. Failure/close cannot cancel a product command. */
internal class LiveboardRecoverObservations {
    private data class State(
        val tickets: List<LiveboardRecoverObservationTicket> = emptyList(),
        val closed: Boolean = false,
        val continuous: Boolean = true,
    )
    private val owner = Any()
    private val state = MutableStateFlow(State())
    val continuous: Boolean get() = state.value.continuous
    fun fault() { state.update { it.copy(continuous = false) } }
    fun begin(enabled: Boolean): LiveboardRecoverObservationTicket? {
        if (!enabled) return null
        var result: LiveboardRecoverObservationTicket? = null
        var refused = false
        while (result == null && !refused) {
            val old = state.value
            if (old.closed || old.tickets.size >= MAX_RECOVER_TICKETS) {
                fault()
                refused = true
            } else {
                val ticket = LiveboardRecoverObservationTicket(owner, old.tickets.size + 1)
                if (state.compareAndSet(old, old.copy(tickets = old.tickets + ticket))) result = ticket
            }
        }
        return result
    }
    fun callback(ticket: LiveboardRecoverObservationTicket) =
        reserve(ticket, RecoverObservationStage.ROOT_CALLBACK) { it }
    fun registered(ticket: LiveboardRecoverObservationTicket, sessionPresent: RecoverObservationFlag,
        intentPresent: RecoverObservationFlag) = reserve(ticket, RecoverObservationStage.POST_REGISTRATION) {
        it.copy(facts = it.facts.copy(sessionPresent = sessionPresent, intentPresent = intentPresent))
    }
    fun body(ticket: LiveboardRecoverObservationTicket) {
        if (owns(ticket)) ticket.state.update {
            it.copy(facts = it.facts.copy(bodyReached = RecoverObservationFlag.ONE))
        }
    }
    fun returned(ticket: LiveboardRecoverObservationTicket, outcome: RecoverObservationOutcome,
        rejection: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED) =
        reserve(ticket, RecoverObservationStage.RETURNED) { it.copy(facts = it.facts.copy(outcome = outcome),
            rejection = rejection) }
    fun completed(ticket: LiveboardRecoverObservationTicket, completion: RecoverObservationCompletion) =
        reserve(ticket, RecoverObservationStage.SAME_JOB_COMPLETION) { it.copy(completion = completion) }
    fun close() {
        state.update { old -> old.copy(closed = true, continuous = old.continuous && old.tickets.all {
            it.state.value.completion == RecoverObservationCompletion.COMPLETED &&
                RecoverObservationStage.ROOT_CALLBACK in it.state.value.stages &&
                RecoverObservationStage.POST_REGISTRATION in it.state.value.stages &&
                (it.state.value.facts.bodyReached != RecoverObservationFlag.ONE ||
                    it.state.value.facts.intentPresent != RecoverObservationFlag.ONE ||
                    RecoverObservationStage.RETURNED in it.state.value.stages)
        }) }
    }
    private fun owns(ticket: LiveboardRecoverObservationTicket): Boolean {
        val old = state.value
        if (old.closed || !ticket.belongsTo(owner) || old.tickets.none { it === ticket }) { fault(); return false }
        return true
    }
    private fun reserve(ticket: LiveboardRecoverObservationTicket, stage: RecoverObservationStage,
        change: (RecoverTicketState) -> RecoverTicketState): LiveboardRecoverObservationRecord? {
        if (!owns(ticket)) return null
        var result: LiveboardRecoverObservationRecord? = null
        var finished = false
        while (!finished) {
            val old = ticket.state.value
            if (stage in old.stages) {
                finished = true
            } else {
                val next = change(old).copy(stages = old.stages + stage)
                if (ticket.state.compareAndSet(old, next)) {
                    result = LiveboardRecoverObservationRecord(ticket.sequence, stage, next.facts,
                        next.completion, next.rejection)
                    finished = true
                }
            }
        }
        return result
    }
}

internal class RecoverObservationInput(
    val session: LiveboardSession?,
    val secretId: String,
    val platform: LiveboardPlatformPort,
    val command: (suspend () -> Unit) -> Job,
)

/** Delivery state holds only primitive records and diagnostic ports, never a session/secret/intent. */
private class RecoverObservationDelivery(
    private val owner: LiveboardRecoverObservations,
    private val platform: LiveboardPlatformPort,
) {
    private val buffered = MutableStateFlow<List<LiveboardRecoverObservationRecord>?>(emptyList())
    fun offer(record: LiveboardRecoverObservationRecord?) {
        if (record == null) return
        var offered = false
        while (!offered) {
            val old = buffered.value
            if (old == null) {
                deliver(record)
                offered = true
            } else offered = buffered.compareAndSet(old, old + record)
        }
    }
    fun flush() {
        var flushed = false
        while (!flushed) {
            val rows = buffered.value
            if (rows == null) {
                flushed = true
            } else if (buffered.compareAndSet(rows, null)) {
                rows.forEach(::deliver)
                flushed = true
            }
        }
    }
    private fun deliver(record: LiveboardRecoverObservationRecord) {
        runCatching { platform.emitRecoverObservation(record) }.onFailure { owner.fault() }
    }
}

/** Separate fixed diagnostics from original product selection and execution. */
private class RecoverObservationRun(
    private val owner: LiveboardRecoverObservations,
    platform: LiveboardPlatformPort,
    private val ticket: LiveboardRecoverObservationTicket?,
) {
    val trace = runCatching { ticket?.let { LiveboardDecisionRejectionTrace() } }
        .onFailure { owner.fault() }.getOrNull()
    private val delivery = RecoverObservationDelivery(owner, platform)
    fun callback() { delivery.offer(runCatching { ticket?.let(owner::callback) }.getOrNull()) }
    fun body() { runCatching { ticket?.let(owner::body) }.onFailure { owner.fault() } }
    fun returned(result: NativeLiveboardOutcome<*>) {
        delivery.offer(runCatching { ticket?.let { owner.returned(it, recoverObservationOutcome(result),
            trace?.source() ?: LiveboardDecisionRejectionSource.UNOBSERVED) } }.getOrNull())
    }
    fun registered(sessionPresent: Boolean, intentPresent: Boolean) {
        delivery.offer(runCatching { ticket?.let { owner.registered(it,
            if (sessionPresent) RecoverObservationFlag.ONE else RecoverObservationFlag.ZERO,
            intentFlag(sessionPresent, intentPresent)) } }.getOrNull())
    }
    fun flush() { delivery.flush() }
    fun attach(job: Job) {
        val observed = ticket ?: return
        job.invokeOnCompletion { failure ->
            val completion = when (failure) {
                null -> RecoverObservationCompletion.COMPLETED
                is CancellationException -> RecoverObservationCompletion.CANCELLED
                else -> RecoverObservationCompletion.THREW
            }
            delivery.offer(runCatching { owner.completed(observed, completion) }.getOrNull())
        }
    }
}
private fun intentFlag(sessionPresent: Boolean, intentPresent: Boolean): RecoverObservationFlag = when {
    !sessionPresent -> RecoverObservationFlag.UNKNOWN
    intentPresent -> RecoverObservationFlag.ONE
    else -> RecoverObservationFlag.ZERO
}

/** Original selection and original UNDIDSPATCHED registration occur once, in their original order. */
internal fun observeRecover(owner: LiveboardRecoverObservations, input: RecoverObservationInput): Job {
    val session = input.session
    val intent = session?.actions?.recoverIntent(input.secretId)
    val platform = input.platform
    val ticket = runCatching { owner.begin(platform.recoverObservationEnabled) }
        .onFailure { owner.fault() }.getOrNull()
    val observation = RecoverObservationRun(owner, platform, ticket)
    observation.callback()
    val job = input.command {
        observation.body()
        if (intent != null) {
            val returned = if (ticket == null) session?.actions?.recover(intent)
                else session?.actions?.recoverObserved(intent, observation.trace)
            if (returned != null) observation.returned(returned)
        }
    }
    observation.registered(session != null, intent != null)
    observation.flush()
    observation.attach(job)
    return job
}
private fun recoverObservationOutcome(result: NativeLiveboardOutcome<*>): RecoverObservationOutcome = when (result) {
    is NativeLiveboardOutcome.NotEntered -> RecoverObservationOutcome.NOT_ENTERED
    is NativeLiveboardOutcome.Entered -> when (result.result) {
        is NativeResult.Success -> RecoverObservationOutcome.ENTERED_SUCCESS
        is NativeResult.Unavailable -> RecoverObservationOutcome.ENTERED_UNAVAILABLE
        NativeResult.Uncertain -> RecoverObservationOutcome.ENTERED_UNCERTAIN
    }
}

/** Compact closed enum codes; complete row including prefix/newline stays below256 UTF-8 bytes. */
internal fun LiveboardRecoverObservationRecord.encodedRecoverObservation(): String? {
    if (sequence !in 1..MAX_RECOVER_TICKETS) return null
    val row = "E2E: RECOVER_ACTION V1 SEQ=$sequence STAGE=${stage.name} S=${facts.sessionPresent.name}" +
        " I=${facts.intentPresent.name} B=${facts.bodyReached.name} O=${facts.outcome.name}" +
        " C=${completion.name} R=${rejectionSource.name}\n"
    return row.takeIf { it.encodeToByteArray().size <= MAX_RECOVER_ROW_BYTES }
}

/** Passive summary only; no product lifetime or teardown work is moved here. */
internal fun closeRecoverObservation(owner: LiveboardRecoverObservations, platform: LiveboardPlatformPort) {
    owner.close()
    runCatching { platform.closeRecoverObservation(owner.continuous) }.onFailure { owner.fault() }
}
