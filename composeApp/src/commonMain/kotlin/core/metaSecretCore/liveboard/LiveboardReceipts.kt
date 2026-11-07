package core.metaSecretCore.liveboard

enum class LiveboardKind { Join, Create, Recover, DeleteSecret, RemoveDevice }
enum class LiveboardPhase { Queued, Unknown }

data class LiveboardCheckedIdentity(
    val secretId: String? = null,
    val claimId: String? = null,
    val requestId: String? = null,
)

data class LiveboardCheckedReceipt(
    val kind: LiveboardKind,
    val identity: LiveboardCheckedIdentity,
    val phase: LiveboardPhase,
    val errorCode: String? = null,
)

data class LiveboardProducerIdentity(
    val kind: LiveboardKind,
    val secretId: String? = null,
    val targetDeviceId: String? = null,
    val requestId: String? = null,
)

data class LiveboardProducerState(
    val phase: LiveboardPhase,
    val materialStage: String?,
    val canonicalState: String,
    val readiness: String,
    val errorCode: String?,
)

data class LiveboardFqdn(val objType: String, val objInstance: String)

/** Validated decimal tokens remain strings until emitted as JSON integer literals. */
data class LiveboardSequence(val curr: String, val prev: String)

data class LiveboardArtifactId(val fqdn: LiveboardFqdn, val id: LiveboardSequence)
data class LiveboardProducerSource(val eventId: LiveboardArtifactId, val bodyCommitment: String)
data class LiveboardProducerAttempt(val source: LiveboardProducerSource, val observation: String)

sealed interface LiveboardProducerDecision {
    data object NotAttempted : LiveboardProducerDecision
    data class Attempted(val source: LiveboardProducerSource, val observation: String) : LiveboardProducerDecision
}

data class LiveboardProducerReceipt(
    val version: Int,
    val identity: LiveboardProducerIdentity,
    val state: LiveboardProducerState,
    val requestStage: LiveboardProducerAttempt,
    val decisionStage: LiveboardProducerDecision? = null,
)
