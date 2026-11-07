package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import models.apiModels.liveboard.LiveboardOperation
import models.apiModels.liveboard.LiveboardRevealContext
import models.apiModels.liveboard.LiveboardRevealReceipt
import models.apiModels.liveboard.LiveboardSecret
import models.apiModels.liveboard.LiveboardSnapshot

/** Metadata beside one exact card; never a value, permission or replacement native owner. */
internal class LiveboardCardContinuity private constructor(
    val card: Any,
    val selection: LiveboardSecret,
    private val originalAttempt: Long,
    private val accepted: LiveboardRevealContext?,
    internal val observation: LiveboardShowObservationTicket?,
) {
    private val bound = MutableStateFlow<LiveboardRevealContext?>(null)
    private val doneRequired = MutableStateFlow(false)
    internal val selectedFact = observation?.let {
        val available = accepted != null
        ShowGuardFacts(ShowGuardPoint.SELECT, if (available) ShowGuardResult.TRUE else ShowGuardResult.FALSE,
            values = ShowGuardValues(accepted = available.guardFlag()))
    }

    fun bind(receipt: LiveboardRevealReceipt?) {
        val context = accepted ?: run {
            observation.guard(ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE,
                ShowGuardCause.ACCEPTED_ABSENT, ShowGuardValues(accepted = ShowGuardFlag.FALSE)))
            return
        }
        val ready = !doneRequired.value
        if (ready && receipt != null) {
            val matched = matchesReceipt(receipt, context)
            val won = if (matched) bound.compareAndSet(null, context) else null
            observation.guard(ShowGuardFacts(ShowGuardPoint.BIND,
                if (won == true) ShowGuardResult.TRUE else ShowGuardResult.FALSE,
                if (!matched) ShowGuardCause.RECEIPT_MISMATCH else if (won == true) ShowGuardCause.NONE
                    else ShowGuardCause.BIND_NOT_WON,
                ShowGuardValues(ShowGuardFlag.TRUE, ShowGuardFlag.TRUE, matched.guardFlag(),
                    if (won == true) ShowGuardFlag.TRUE else ShowGuardFlag.UNOBSERVED)))
        } else observation.guard(ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE,
            if (ready) ShowGuardCause.RECEIPT_ABSENT else ShowGuardCause.DONE_REQUIRED,
            ShowGuardValues(ShowGuardFlag.TRUE, if (ready) ShowGuardFlag.FALSE else ShowGuardFlag.UNOBSERVED)))
    }

    private fun matchesReceipt(receipt: LiveboardRevealReceipt, context: LiveboardRevealContext): Boolean =
        receipt.version == 1 && receipt.secretId == selection.identity.passId &&
            receipt.recoveryClaimId == selection.primary.claimId &&
            receipt.requesterId == context.identity.currentDeviceId && receipt.context == context

    fun current(member: LiveboardSnapshot?, attempt: Long): Boolean = matches(member, attempt)

    /** Only an already-public disclosure can call this at the controller's existing mask boundary. */
    fun capture(member: LiveboardSnapshot?, attempt: Long): Boolean {
        val current = matches(member, attempt)
        val recovered = selection.primary.action == "showRecovered"
        if (current && recovered) doneRequired.value = true
        return current && (!recovered || bound.value != null)
    }

    private fun matches(member: LiveboardSnapshot?, attempt: Long): Boolean {
        val current = member?.secrets?.singleOrNull { it.identity.passId == selection.identity.passId }
        return when {
            member == null || current == null -> false
            selection.primary.action != "showRecovered" -> current == selection
            else -> matchesRecovered(member, current, attempt)
        }
    }

    private fun matchesRecovered(snapshot: LiveboardSnapshot, current: LiveboardSecret, attempt: Long): Boolean {
        if (attempt > originalAttempt) doneRequired.value = true
        // Legacy first Show still uses its original frame; no unchanged-Secret shortcut survives a read.
        return when {
            attempt < originalAttempt -> false
            !doneRequired.value -> current == selection
            else -> bound.value?.let { matchesDone(snapshot, current, it) } == true
        }
    }

    private fun matchesDone(snapshot: LiveboardSnapshot, current: LiveboardSecret,
        context: LiveboardRevealContext): Boolean =
        current.copy(primary = selection.primary) == selection && current.primary.action == "recover" &&
            current.primary.readiness == "ready" && current.primary.claimId == null &&
            snapshot.identity == context.identity && snapshot.operations.any { matchesDoneOperation(it, context) }

    private fun matchesDoneOperation(operation: LiveboardOperation, context: LiveboardRevealContext): Boolean =
        operation.identity.kind == LiveboardKind.Recover &&
            operation.identity.requestId == selection.primary.claimId &&
            operation.identity.secretId == selection.identity.passId &&
            operation.identity.requesterId == context.identity.currentDeviceId &&
            operation.identity.targetDeviceId == null && operation.status.clientStatus == "done" &&
            operation.status.outcome == "accepted" && operation.revealContext == context

    companion object {
        fun select(card: Any, selection: LiveboardSecret, member: LiveboardSnapshot?, attempt: Long,
            observation: LiveboardShowObservationTicket? = null
            ): LiveboardCardContinuity {
            val accepted = member?.let { snapshot ->
                snapshot.takeIf { selection.primary.action == "showRecovered" && selection.primary.readiness ==
                    "ready" && it.secrets.any { secret -> secret == selection }
                }?.operations?.singleOrNull {
                    it.identity.kind == LiveboardKind.Recover && it.identity.requestId == selection.primary.claimId &&
                        it.identity.secretId == selection.identity.passId && it.identity.requesterId == snapshot
                            .identity.currentDeviceId && it.status.clientStatus == "accepted"
                }?.revealContext
            }
            return LiveboardCardContinuity(card, selection, attempt, accepted, observation)
        }
    }
}
