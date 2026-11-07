package models.apiModels.liveboard

import core.metaSecretCore.liveboard.LiveboardArtifactId

/** Checked Core output metadata; none of these values grants a new Show. */
data class LiveboardRevealSource(val eventId: LiveboardArtifactId, val bodyCommitment: String) {
    override fun toString(): String = "LiveboardRevealSource([REDACTED])"
}

data class LiveboardRevealSources(
    val vault: LiveboardRevealSource,
    val localDistribution: LiveboardRevealSource,
    val recoveries: List<LiveboardRevealSource>,
) {
    override fun toString(): String = "LiveboardRevealSources([REDACTED])"
}

data class LiveboardRevealContext(
    val version: Int,
    val identity: LiveboardSnapshotIdentity,
    val sources: LiveboardRevealSources,
    val claimScopeCommitment: String,
) {
    override fun toString(): String = "LiveboardRevealContext([REDACTED])"
}

data class LiveboardRevealReceipt(
    val version: Int,
    val secretId: String,
    val recoveryClaimId: String,
    val requesterId: String,
    val context: LiveboardRevealContext,
) {
    override fun toString(): String = "LiveboardRevealReceipt([REDACTED])"
}
