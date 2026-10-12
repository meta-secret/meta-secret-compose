package models.apiModels

import kotlinx.serialization.Serializable

@Serializable
enum class SecretDeletionStatus { Pending, NeedApprove, Done, Declined }

@Serializable
data class SecretDeletionState(
    val passId: PassId,
    val secretId: String,
    val requestId: String? = null,
    val requiresApproval: Boolean,
    val clientStatus: SecretDeletionStatus? = null,
)

@Serializable
data class SecretDeletionDecision(val requestId: String, val accepted: Boolean)

// Core supplies statuses; presentation prefers a live request over historical outcomes.
fun currentDeletionState(states: List<SecretDeletionState>, secretId: String): SecretDeletionState? {
    val matching = states.filter { it.secretId == secretId }
    return matching.firstOrNull { it.clientStatus == SecretDeletionStatus.Pending || it.clientStatus == SecretDeletionStatus.NeedApprove }
        ?: matching.lastOrNull { it.clientStatus == SecretDeletionStatus.Declined }
        ?: matching.lastOrNull()
}

/** A new operation uses Core's current policy entry, never a historical request's policy. */
fun deletionPolicyState(states: List<SecretDeletionState>, secretId: String): SecretDeletionState? {
    val current = currentDeletionState(states, secretId)
    if (current?.clientStatus == SecretDeletionStatus.Pending || current?.clientStatus == SecretDeletionStatus.NeedApprove) return null
    return states.singleOrNull { it.secretId == secretId && it.requestId == null && it.clientStatus == null }
}
