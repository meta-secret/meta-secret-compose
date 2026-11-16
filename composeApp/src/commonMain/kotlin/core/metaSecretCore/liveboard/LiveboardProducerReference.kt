package core.metaSecretCore.liveboard

import kotlinx.serialization.json.JsonPrimitive

/** Immutable original correlation; its serialized form contains input-reference fields only. */
class LiveboardProducerReference private constructor(
    private val identity: LiveboardProducerIdentity,
    private val requestSource: LiveboardProducerSource,
    private val decisionSource: LiveboardProducerSource?,
) {
    fun toJson(): String {
        val kind = when (identity.kind) {
            LiveboardKind.DeleteSecret -> "deleteSecret"
            LiveboardKind.RemoveDevice -> "removeDevice"
            else -> error("InvalidProducerReference")
        }
        val requestId = identity.requestId?.let { ",\"requestId\":${quoted(it)}" }.orEmpty()
        val decision = decisionSource?.let { ",\"decisionStage\":${sourceJson(it)}" }.orEmpty()
        val encoded =
            "{\"version\":1,\"kind\":${quoted(kind)}$requestId,\"requestStage\":${sourceJson(requestSource)}$decision}"
        require(encoded.encodeToByteArray().size <= MAX_PRODUCER_REFERENCE_BYTES) { "InvalidProducerReference" }
        return encoded
    }

    internal fun isRemove(): Boolean = identity.kind == LiveboardKind.RemoveDevice

    internal fun matches(receipt: LiveboardProducerReceipt): Boolean =
        receipt.identity.kind == identity.kind &&
            receipt.identity.secretId == identity.secretId &&
            receipt.identity.targetDeviceId == identity.targetDeviceId &&
            (identity.requestId == null || receipt.identity.requestId == identity.requestId) &&
            receipt.requestStage.source == requestSource &&
            (decisionSource == null ||
                (receipt.decisionStage as? LiveboardProducerDecision.Attempted)?.source == decisionSource)

    companion object {
        fun fromReceipt(receipt: LiveboardProducerReceipt): LiveboardProducerReference {
            require(receipt.version == 1) { "InvalidProducerReference" }
            val identity = receipt.identity
            require(when (identity.kind) {
                LiveboardKind.DeleteSecret -> identity.secretId != null && identity.requestId != null && receipt
                    .decisionStage == null
                LiveboardKind.RemoveDevice -> identity.targetDeviceId != null
                else -> false
            }) { "InvalidProducerReference" }
            identity.requestId?.let { LiveboardJson.uuid(JsonPrimitive(it)) }
            validateSource(receipt.requestStage.source)
            val attempted = receipt.decisionStage as? LiveboardProducerDecision.Attempted
            attempted?.let { validateSource(it.source) }
            return LiveboardProducerReference(
                receipt.identity,
                receipt.requestStage.source,
                attempted?.source,
            )
        }
    }
}

private fun validateSource(source: LiveboardProducerSource) {
    val sequence = source.eventId.id
    val decimal = Regex("0|[1-9][0-9]*")
    require(decimal.matches(sequence.curr) && decimal.matches(sequence.prev)) { "InvalidProducerReference" }
    val curr = sequence.curr.toULongOrNull()
    val prev = sequence.prev.toULongOrNull()
    require(curr != null && curr != 0uL && prev != null && curr - 1u == prev) { "InvalidProducerReference" }
    require(Regex("[0-9a-f]{64}").matches(source.bodyCommitment)) { "InvalidProducerReference" }
    LiveboardJson.text(JsonPrimitive(source.eventId.fqdn.objType))
    LiveboardJson.text(JsonPrimitive(source.eventId.fqdn.objInstance))
}

private fun sourceJson(source: LiveboardProducerSource): String {
    val event = source.eventId
    val fqdn = "{\"objType\":${quoted(event.fqdn.objType)},\"objInstance\":${quoted(event.fqdn.objInstance)}}"
    val sequence = "{\"curr\":${event.id.curr},\"prev\":${event.id.prev}}"
    return "{\"eventId\":{\"fqdn\":$fqdn,\"id\":$sequence},\"bodyCommitment\":${quoted(source.bodyCommitment)}}"
}

private fun quoted(value: String): String = JsonPrimitive(value).toString()

private const val MAX_PRODUCER_REFERENCE_BYTES: Int = 4096
