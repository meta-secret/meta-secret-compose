package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import models.apiModels.liveboard.LiveboardSnapshot
import models.apiModels.liveboard.LiveboardOperation

/** Decoded negative receipt only, never a submitted approval. */
internal object RecoverApproveNotSubmitted : LiveboardDecisionAttempt

/** Process-local acknowledgement evidence; never an automatic retry command. */
internal class LiveboardDecisionRetryEvidence(
    private val session: LiveboardSession,
    private val reads: NativeCanonicalReadOwner,
) {
    private class Ack(val outcome: NativeLiveboardOutcome.Entered<*>,
        val requirement: NativeReadRequirement, val stamp: Any)
    private class Published(val outcome: NativeLiveboardOutcome.Entered<*>, val event: Any)
    private val published = MutableStateFlow<Map<LiveboardDecisionIntent, Published>>(emptyMap())
    private val acknowledgements = MutableStateFlow<Map<LiveboardDecisionIntent, Ack>>(emptyMap())

    fun recognized(result: NativeResult<*>?): Boolean =
        result is NativeResult.Success && result.value === RecoverApproveNotSubmitted

    fun decode(intent: LiveboardDecisionIntent, invocation: NativeInvocation<String>
        ): NativeLiveboardOutcome<LiveboardDecisionAttempt> = when (invocation) {
        NativeInvocation.Retired -> NativeLiveboardOutcome.NotEntered(session.scope, NativeFailure.OperationUnavailable)
        is NativeInvocation.Failure -> NativeLiveboardOutcome.Entered(invocation.scope, NativeResult.Uncertain)
        is NativeInvocation.Value -> NativeLiveboardOutcome.Entered(invocation.scope,
            decodeRetryDecision(session, intent, invocation))
    }

    fun published(intent: LiveboardDecisionIntent, outcome: NativeLiveboardOutcome.Entered<*>, event: Any) {
        if (recognized(outcome.result)) published.update { it + (intent to Published(outcome, event)) }
    }

    fun rebindPublished(event: Any) {
        published.update { old -> old.mapValues { (_, value) -> Published(value.outcome, event) } }
    }
    fun publishedMatches(intent: LiveboardDecisionIntent, outcome: NativeLiveboardOutcome.Entered<*>,
        event: Any): Boolean = published.value[intent]?.let { it.event === event && it.outcome === outcome } == true

    fun acknowledge(intent: LiveboardDecisionIntent, outcome: NativeLiveboardOutcome.Entered<*>,
        required: NativeReadRequirement, event: Any) {
        val publication = published.value[intent] ?: return
        val bound = publication.event === event && publication.outcome === outcome
        if (!bound || !recognized(outcome.result) || !session.ownsNativeScope()) return
        val ack = Ack(outcome, required, session.sensitive.decisionStamp())
        acknowledgements.update { it + (intent to ack) }
    }

    fun resolved(intent: LiveboardDecisionIntent, outcome: NativeLiveboardOutcome.Entered<*>,
        frame: NativeReadFrame, member: LiveboardSnapshot): Boolean {
        if (!recognized(outcome.result) || !retryFrameCurrent(session, frame, member)) return false
        val operation = member.operations.singleOrNull { it.identity == intent.identity }
        return when {
            operation == null -> false
            retryTerminal(operation) -> {
                acknowledgements.update { it - intent }
                true
            }
            else -> acknowledged(intent, outcome, frame, operation)
        }
    }

    private fun acknowledged(intent: LiveboardDecisionIntent, outcome: NativeLiveboardOutcome.Entered<*>,
        frame: NativeReadFrame, operation: LiveboardOperation): Boolean {
        val ack = acknowledgements.value[intent] ?: return false
        val current = ack.outcome === outcome && session.sensitive.isDecisionStamp(ack.stamp) &&
            liveboardRequirementResolved(reads, session.scope, ack.requirement, frame)
        return current && operation.permissions.canApprove && consume(intent, ack)
    }

    private fun consume(intent: LiveboardDecisionIntent, ack: Ack): Boolean {
        // Consume exact evidence before presentation release; no command is invoked here.
        while (true) {
            val old = acknowledgements.value
            if (old[intent] !== ack) return false
            if (acknowledgements.compareAndSet(old, old - intent)) return true
        }
    }
}

private fun decodeRetryDecision(session: LiveboardSession, intent: LiveboardDecisionIntent,
    invocation: NativeInvocation.Value<String>): NativeResult<LiveboardDecisionAttempt> = try {
    val envelope = LiveboardJson.record(LiveboardJson.parse(invocation.value))
    val eligible = intent.identity.kind == LiveboardKind.Recover && intent.accepted
    if (eligible) LiveboardJson.fields(envelope, "success", "error", "decisionAttempt")
    else LiveboardJson.fields(envelope, "success", "error")
    val success = LiveboardJson.boolean(envelope["success"])
    if ("decisionAttempt" in envelope) {
        require(!success && eligible)
        decodeNotSubmitted(session, intent, invocation.scope, envelope["decisionAttempt"])
    } else if (success) NativeResult.Success(LiveboardDecisionAttempt.Returned(
        intent.identity.kind, intent.identity.requestId, intent.accepted))
    else NativeResult.Unavailable(NativeFailure.InvalidResponse)
} catch (_: Exception) { NativeResult.Unavailable(NativeFailure.InvalidResponse) }

private fun decodeNotSubmitted(session: LiveboardSession, intent: LiveboardDecisionIntent,
    scope: NativeReadScope, value: kotlinx.serialization.json.JsonElement?): NativeResult<LiveboardDecisionAttempt> {
    require(scope == session.scope && intent.generation === session.generation && session.ownsNativeScope())
    val member = requireNotNull(session.currentMember())
    val proof = LiveboardJson.record(value)
    LiveboardJson.fields(proof, "schemaVersion", "kind", "claimId", "actorId", "vaultName", "phase")
    LiveboardJson.version(proof["schemaVersion"])
    LiveboardJson.choice(proof["kind"], "recoverApprove")
    LiveboardJson.choice(proof["phase"], "notSubmitted")
    require(LiveboardJson.text(proof["claimId"]) == intent.identity.requestId &&
        LiveboardJson.text(proof["actorId"]) == member.identity.currentDeviceId &&
        LiveboardJson.text(proof["vaultName"]) == member.identity.vaultName)
    return NativeResult.Success(RecoverApproveNotSubmitted)
}

private fun retryFrameCurrent(session: LiveboardSession, frame: NativeReadFrame,
    member: LiveboardSnapshot): Boolean = session.isFrameCurrent(frame) && session.ownsNativeScope() &&
    session.currentMember()?.identity == member.identity

private fun retryTerminal(operation: LiveboardOperation): Boolean {
    val verified = operation.status.resolutionSource == "verified" &&
        operation.status.outcome in listOf("accepted", "declined") && !operation.status.responderId.isNullOrEmpty()
    return verified && !operation.permissions.canApprove && !operation.permissions.canDecline
}

/** Publication bookkeeping only, after the original error callback has returned. */
internal fun publishRetryFailureWave(claimed: Set<Any>, journal: LiveboardCommandJournal,
    evidence: LiveboardDecisionRetryEvidence, consumed: () -> Set<Any>, event: Any) {
    claimed.filterIsInstance<LiveboardDecisionIntent>().forEach { intent ->
        val outcome = journal.retained<Any>(intent) ?: return@forEach
        if (intent in consumed()) evidence.published(intent, outcome, event)
    }
    if (claimed.any { evidence.recognized(journal.retained<Any>(it)?.result) }) evidence.rebindPublished(event)
}
