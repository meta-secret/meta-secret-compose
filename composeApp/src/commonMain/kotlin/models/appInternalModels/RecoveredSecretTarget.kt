package models.appInternalModels

/** Exact claim and secret selected for a sender's post-approval Show action. */
data class RecoveredSecretTarget(
    val claimId: String,
    val secretId: String,
)
