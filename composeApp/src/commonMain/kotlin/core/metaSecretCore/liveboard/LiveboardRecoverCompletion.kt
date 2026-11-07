package core.metaSecretCore.liveboard

import models.apiModels.liveboard.LiveboardSnapshot

/** Call-local observation only; no frame, value or additional command is retained. */
internal class LiveboardRecoverProof(
    val session: LiveboardSession,
    val frame: NativeReadFrame,
    val member: LiveboardSnapshot,
)

/** Retires only the prior selection; journal outcome/target and new-entry guards stay owned elsewhere. */
internal object LiveboardRecoverCompletion {
    fun terminal(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent,
        admission: LiveboardEntryAdmission?, entered: NativeLiveboardOutcome.Entered<*>): Boolean {
        val receipt = (entered.result as? NativeResult.Success<*>)?.value as? LiveboardCheckedReceipt
        val claim = receipt?.identity?.claimId
        return when {
            receipt == null || claim == null -> false
            compatible(proof.member, intent, claim) -> true
            else -> exact(proof, intent, admission, entered, receipt)
        }
    }
    private fun exact(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent,
        admission: LiveboardEntryAdmission?, entered: NativeLiveboardOutcome.Entered<*>,
        receipt: LiveboardCheckedReceipt): Boolean {
        if (!currentFrame(proof) || !sameOwner(proof, intent, entered) ||
            !checked(intent, admission, receipt)) return false
        val selection = admission?.selection
        return selection != null && selected(proof, intent, selection) &&
            completed(proof, intent, receipt.identity.claimId)
    }
    private fun currentFrame(proof: LiveboardRecoverProof): Boolean = proof.session.isFrameCurrent(proof.frame) &&
        proof.frame.outcome == NativeReadOutcome.SUCCESS && proof.frame.state?.success == true
    private fun sameOwner(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent,
        entered: NativeLiveboardOutcome.Entered<*>): Boolean = entered.scope == proof.session.scope &&
        proof.frame.scope == proof.session.scope && proof.session.currentMember() == proof.member &&
        intent.generation === proof.session.generation
    private fun checked(intent: LiveboardRecoverIntent, admission: LiveboardEntryAdmission?,
        receipt: LiveboardCheckedReceipt): Boolean = receipt.kind == LiveboardKind.Recover &&
        receipt.identity.secretId == intent.secretId && admission?.isAdmitted == true
    private fun selected(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent,
        selection: LiveboardEntrySelection): Boolean {
        val original = selection.subject as? LiveboardEntrySubject.Secret ?: return false
        return selection.session === proof.session && !original.delete && ready(proof, intent, original)
    }
    private fun ready(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent,
        original: LiveboardEntrySubject.Secret): Boolean {
        val secret = proof.member.secrets.singleOrNull { it.identity.passId == intent.secretId } ?: return false
        return secret == original.frozen && secret.primary.action == "recover" && secret.primary.readiness == "ready" &&
            secret.primary.claimId == null && secret.deletionRequestId == null
    }
    private fun completed(proof: LiveboardRecoverProof, intent: LiveboardRecoverIntent, claim: String?): Boolean {
        val operation = proof.member.operations.singleOrNull {
            it.identity.kind == LiveboardKind.Recover && it.identity.requestId == claim
        } ?: return false
        return operation.identity.secretId == intent.secretId && operation.identity.requesterId == proof.member
            .identity.currentDeviceId && operation.identity.targetDeviceId == null &&
            operation.status.clientStatus == "done" && operation.status.outcome == "accepted" &&
            operation.status.resolutionSource == "verified" && operation.status.readiness == "waiting" &&
            !operation.permissions.canApprove && !operation.permissions.canDecline &&
            proof.session.currentMember() == proof.member && proof.session.isFrameCurrent(proof.frame)
    }

    /** Preserve the pre-existing Done/settled and Declined/waiting paths exactly. */
    private fun compatible(member: LiveboardSnapshot, intent: LiveboardRecoverIntent, claim: String): Boolean =
        member.operations.any { it.identity.kind == LiveboardKind.Recover && it.identity.requestId == claim &&
            it.identity.secretId == intent.secretId && it.identity.requesterId == member.identity.currentDeviceId &&
            ((it.status.clientStatus == "done" && it.status.readiness in listOf("done", "settled")) ||
                (it.status.clientStatus == "declined" && it.status.outcome == "declined" &&
                    it.status.resolutionSource in listOf("canonical", "verified") &&
                    it.status.readiness == "waiting")) } &&
            member.secrets.any { it.identity.passId == intent.secretId && it.primary.readiness == "ready" &&
                it.primary.action == "recover" }
}
