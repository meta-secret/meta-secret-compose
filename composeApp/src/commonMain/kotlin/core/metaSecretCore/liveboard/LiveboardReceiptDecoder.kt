package core.metaSecretCore.liveboard

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Separate typed native envelopes; legacy String-message decoding is unchanged. */
object LiveboardReceiptDecoder {
    fun decodeChecked(raw: String): NativeResult<LiveboardCheckedReceipt> = decode(raw) { message ->
        LiveboardJson.fields(message, "kind", "secretId", "claimId", "requestId", "phase", "errorCode")
        val kind = LiveboardJson.kind(message["kind"])
        val identity = LiveboardCheckedIdentity(
            LiveboardJson.optionalText(message["secretId"]),
            LiveboardJson.optionalText(message["claimId"]),
            optionalUuid(message["requestId"]),
        )
        require(when (kind) {
            LiveboardKind.Create -> identity.secretId != null
            LiveboardKind.Recover -> identity.secretId != null && identity.claimId != null
            LiveboardKind.Join, LiveboardKind.RemoveDevice -> identity.requestId != null
            LiveboardKind.DeleteSecret -> false
        }) { "InvalidResponse" }
        LiveboardCheckedReceipt(kind, identity, LiveboardJson.phase(message["phase"]),
            LiveboardJson.optionalText(message["errorCode"]))
    }

    fun decodeProducer(raw: String): NativeResult<LiveboardProducerReceipt> = decode(raw) { message ->
        LiveboardJson.fields(message, "version", "kind", "phase", "secretId", "targetDeviceId", "requestId",
            "requestStage", "decisionStage", "materialStage", "canonicalState", "readiness", "errorCode")
        LiveboardJson.version(message["version"])
        val kind = LiveboardJson.kind(message["kind"])
        val identity = LiveboardProducerIdentity(kind,
            LiveboardJson.optionalText(message["secretId"]),
            LiveboardJson.optionalText(message["targetDeviceId"]), optionalUuid(message["requestId"]))
        require(when (kind) {
            LiveboardKind.DeleteSecret -> identity.secretId != null && identity.requestId != null &&
                "decisionStage" !in message
            LiveboardKind.RemoveDevice -> identity.targetDeviceId != null
            else -> false
        }) { "InvalidResponse" }
        LiveboardProducerReceipt(1, identity, state(message), attempt(message["requestStage"]),
            decision(message["decisionStage"]))
    }

    private fun <T> decode(raw: String, read: (JsonObject) -> T): NativeResult<T> = try {
        val outer = LiveboardJson.record(LiveboardJson.parse(raw))
        if (LiveboardJson.boolean(outer["success"])) {
            LiveboardJson.fields(outer, "success", "message")
            NativeResult.Success(read(LiveboardJson.record(outer["message"])))
        } else {
            LiveboardJson.fields(outer, "success", "errorCode")
            NativeResult.Unavailable(when (LiveboardJson.text(outer["errorCode"])) {
                "NotInitialized" -> NativeFailure.NotInitialized
                "OperationUnavailable" -> NativeFailure.OperationUnavailable
                "InvalidDecisionInput" -> NativeFailure.InvalidDecisionInput
                "InvalidProducerReference" -> NativeFailure.InvalidProducerReference
                else -> NativeFailure.InvalidResponse
            })
        }
    } catch (_: Exception) {
        NativeResult.Unavailable(NativeFailure.InvalidResponse)
    }

    private fun optionalUuid(value: JsonElement?): String? =
        if (LiveboardJson.optionalText(value) == null) null else LiveboardJson.uuid(value)

    private fun state(value: JsonObject): LiveboardProducerState = LiveboardProducerState(
        LiveboardJson.phase(value["phase"]),
        value["materialStage"]?.takeUnless { it == JsonNull }?.let {
            LiveboardJson.choice(it, "notStarted", "pending", "ready", "unknown")
        },
        LiveboardJson.choice(value["canonicalState"], "unknown", "pending", "prepared", "applied", "declined"),
        LiveboardJson.choice(value["readiness"], "waiting", "ready", "synchronizing", "done", "settled"),
        LiveboardJson.optionalText(value["errorCode"]),
    )

    private fun attempt(value: JsonElement?): LiveboardProducerAttempt {
        val objectValue = LiveboardJson.record(value)
        LiveboardJson.fields(objectValue, "source", "observation")
        return LiveboardProducerAttempt(source(objectValue["source"]), observation(objectValue["observation"]))
    }

    private fun decision(value: JsonElement?): LiveboardProducerDecision? {
        if (value == null || value == JsonNull) return null
        val objectValue = LiveboardJson.record(value)
        return when (LiveboardJson.choice(objectValue["state"], "notAttempted", "attempted")) {
            "notAttempted" -> {
                LiveboardJson.fields(objectValue, "state")
                LiveboardProducerDecision.NotAttempted
            }
            else -> {
                LiveboardJson.fields(objectValue, "state", "source", "observation")
                LiveboardProducerDecision.Attempted(source(objectValue["source"]), observation(objectValue[
                    "observation"]))
            }
        }
    }

    private fun observation(value: JsonElement?): String = LiveboardJson.choice(value,
        "unconfirmed", "persisted", "admitted", "rawAcknowledged", "absent", "redacted", "conflict")

    private fun source(value: JsonElement?): LiveboardProducerSource {
        val objectValue = LiveboardJson.record(value)
        LiveboardJson.fields(objectValue, "eventId", "bodyCommitment")
        val event = LiveboardJson.record(objectValue["eventId"])
        LiveboardJson.fields(event, "fqdn", "id")
        val fqdn = LiveboardJson.record(event["fqdn"])
        LiveboardJson.fields(fqdn, "objType", "objInstance")
        val sequence = LiveboardJson.record(event["id"])
        LiveboardJson.fields(sequence, "curr", "prev")
        val curr = LiveboardJson.u64(sequence["curr"])
        val prev = LiveboardJson.u64(sequence["prev"])
        require(curr != "0" && curr.toULong() - 1u == prev.toULong()) { "InvalidResponse" }
        val commitment = LiveboardJson.text(objectValue["bodyCommitment"])
        require(Regex("[0-9a-f]{64}").matches(commitment)) { "InvalidResponse" }
        return LiveboardProducerSource(LiveboardArtifactId(
            LiveboardFqdn(LiveboardJson.text(fqdn["objType"]), LiveboardJson.text(fqdn["objInstance"])),
            LiveboardSequence(curr, prev)), commitment)
    }
}
