package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardDecisionRejectionSource
import core.metaSecretCore.liveboard.LiveboardDecisionRejectionTrace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_DECISION_OBSERVATION_TICKETS = 32
private const val MAX_DECISION_OBSERVATION_BYTES = 512
private const val DECISION_OBSERVATION_VERSION = 3

internal enum class DecisionObservationFlag { ZERO, ONE, UNKNOWN }
internal enum class DecisionObservationStage { RENDER_REJECTED, POST_REGISTRATION, SAME_JOB_COMPLETION }
internal enum class DecisionObservationOutcome {
    UNOBSERVED, NOT_ENTERED, ENTERED_SUCCESS, ENTERED_UNAVAILABLE, ENTERED_UNCERTAIN
}
internal enum class DecisionObservationCompletion { UNOBSERVED, COMPLETED, CANCELLED, THREW }

internal data class DecisionObservationFacts(
    val rendered: DecisionObservationFlag,
    val sessionPresent: DecisionObservationFlag = DecisionObservationFlag.UNKNOWN,
    val intentPresent: DecisionObservationFlag = DecisionObservationFlag.UNKNOWN,
    val bodyReached: DecisionObservationFlag = DecisionObservationFlag.UNKNOWN,
    val outcome: DecisionObservationOutcome = DecisionObservationOutcome.UNOBSERVED,
)

/** Diagnostic metadata only; construction cannot accept identifiers, arbitrary strings or native results. */
class LiveboardDecisionObservationRecord internal constructor(
    internal val delivery: Int,
    internal val stage: DecisionObservationStage,
    internal val facts: DecisionObservationFacts,
    internal val completion: DecisionObservationCompletion = DecisionObservationCompletion.UNOBSERVED,
    internal val rejectionSource: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
)

internal data class DecisionObservationState(
    val facts: DecisionObservationFacts,
    val claimed: Boolean = false,
    val initialReserved: Boolean = false,
    val finalReserved: Boolean = false,
    val rejectionSource: LiveboardDecisionRejectionSource = LiveboardDecisionRejectionSource.UNOBSERVED,
)

/** Opaque observation ticket has no product admission authority or reference to its owning Root. */
class LiveboardDecisionObservationTicket internal constructor(
    private val owner: Any,
    internal val delivery: Int,
    facts: DecisionObservationFacts,
) {
    internal val state = MutableStateFlow(DecisionObservationState(facts))
    internal fun belongsTo(candidate: Any): Boolean = owner === candidate
}

/** Only fixed metadata is atomically cached; callbacks, serialization and delivery happen elsewhere. */
internal class LiveboardDecisionObservations {
    private val owner = Any()
    private val issued = MutableStateFlow(0)

    fun begin(enabled: Boolean, rendered: Boolean): LiveboardDecisionObservationTicket? {
        if (enabled) {
            while (true) {
                val count = issued.value
                if (count >= MAX_DECISION_OBSERVATION_TICKETS) break
                if (issued.compareAndSet(count, count + 1)) return LiveboardDecisionObservationTicket(
                    owner, count + 1,
                    DecisionObservationFacts(
                        if (rendered) DecisionObservationFlag.ONE else DecisionObservationFlag.ZERO))
            }
        }
        return null
    }

    fun claim(ticket: LiveboardDecisionObservationTicket): Boolean {
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (old.claimed || old.initialReserved || old.finalReserved) break
                if (ticket.state.compareAndSet(old, old.copy(claimed = true))) return true
            }
        }
        return false
    }

    fun rejected(ticket: LiveboardDecisionObservationTicket): LiveboardDecisionObservationRecord? {
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (old.initialReserved || old.facts.rendered != DecisionObservationFlag.ZERO) break
                val next = old.copy(initialReserved = true)
                if (ticket.state.compareAndSet(old, next)) return LiveboardDecisionObservationRecord(
                    ticket.delivery, DecisionObservationStage.RENDER_REJECTED, next.facts,
                    rejectionSource = next.rejectionSource)
            }
        }
        return null
    }

    fun postRegistration(ticket: LiveboardDecisionObservationTicket, sessionPresent: DecisionObservationFlag,
        intentPresent: DecisionObservationFlag): LiveboardDecisionObservationRecord? {
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (old.initialReserved) break
                val facts = old.facts.copy(sessionPresent = sessionPresent, intentPresent = intentPresent,
                    bodyReached = if (old.facts.bodyReached == DecisionObservationFlag.UNKNOWN)
                        DecisionObservationFlag.ZERO else old.facts.bodyReached)
                val next = old.copy(facts = facts, initialReserved = true)
                if (ticket.state.compareAndSet(old, next)) return LiveboardDecisionObservationRecord(
                    ticket.delivery, DecisionObservationStage.POST_REGISTRATION, next.facts,
                    rejectionSource = next.rejectionSource)
            }
        }
        return null
    }

    fun bodyReached(ticket: LiveboardDecisionObservationTicket) {
        if (!owns(ticket)) return
        ticket.state.update { old -> if (old.finalReserved) old
            else old.copy(facts = old.facts.copy(bodyReached = DecisionObservationFlag.ONE)) }
    }

    fun returned(ticket: LiveboardDecisionObservationTicket, outcome: DecisionObservationOutcome) =
        returnedObserved(ticket, outcome, LiveboardDecisionRejectionSource.UNOBSERVED)

    fun returnedObserved(ticket: LiveboardDecisionObservationTicket, outcome: DecisionObservationOutcome,
        source: LiveboardDecisionRejectionSource) {
        if (!owns(ticket)) return
        ticket.state.update { old ->
            if (old.finalReserved) old else old.copy(facts = old.facts.copy(outcome = outcome),
                rejectionSource = source)
        }
    }

    fun completed(ticket: LiveboardDecisionObservationTicket, completion: DecisionObservationCompletion
        ): LiveboardDecisionObservationRecord? {
        if (owns(ticket)) {
            while (true) {
                val old = ticket.state.value
                if (!old.initialReserved || old.finalReserved) break
                val next = old.copy(finalReserved = true)
                if (ticket.state.compareAndSet(old, next)) return LiveboardDecisionObservationRecord(
                    ticket.delivery, DecisionObservationStage.SAME_JOB_COMPLETION, next.facts, completion,
                    next.rejectionSource)
            }
        }
        return null
    }

    private fun owns(ticket: LiveboardDecisionObservationTicket): Boolean = ticket.belongsTo(owner) &&
        ticket.delivery in 1..MAX_DECISION_OBSERVATION_TICKETS
}

internal fun createDecisionRejectionTrace(
    ticket: LiveboardDecisionObservationTicket?,
): LiveboardDecisionRejectionTrace? =
    runCatching { ticket?.let { LiveboardDecisionRejectionTrace() } }.getOrNull()

internal fun LiveboardDecisionObservationRecord.encoded(): String? {
    if (delivery !in 1..MAX_DECISION_OBSERVATION_TICKETS) return null
    val payload = buildJsonObject {
        put("VERSION", DECISION_OBSERVATION_VERSION)
        put("DELIVERY", delivery)
        put("STAGE", stage.name)
        put("RENDERED", facts.rendered.jsonValue())
        put("SESSION_PRESENT", facts.sessionPresent.jsonValue())
        put("INTENT_PRESENT", facts.intentPresent.jsonValue())
        put("BODY_REACHED", facts.bodyReached.jsonValue())
        put("CURRENT", "UNOBSERVED")
        put("OUTCOME", facts.outcome.name)
        put("COMPLETION", completion.name)
        put("REJECTION_SOURCE", rejectionSource.name)
    }
    val line = "LIVEBOARD_DECISION_OBSERVATION $payload"
    return line.takeIf { ("E2E_DIAG " + it + "\n").encodeToByteArray().size <= MAX_DECISION_OBSERVATION_BYTES }
}

private fun DecisionObservationFlag.jsonValue(): JsonPrimitive = when (this) {
    DecisionObservationFlag.ZERO -> JsonPrimitive(0)
    DecisionObservationFlag.ONE -> JsonPrimitive(1)
    DecisionObservationFlag.UNKNOWN -> JsonPrimitive("UNKNOWN")
}
