package core.metaSecretCore.liveboard

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.liveboard.LiveboardRevealContext
import models.apiModels.liveboard.LiveboardRevealReceipt
import models.apiModels.liveboard.LiveboardRevealSource
import models.apiModels.liveboard.LiveboardRevealSources
import models.apiModels.liveboard.LiveboardSnapshotIdentity

/** The value stays on the existing clearable delivery path, never in a metadata DTO. */
internal class LiveboardRevealValue(val value: String, val receipt: LiveboardRevealReceipt?) {
    override fun toString(): String = "LiveboardRevealValue([REDACTED])"
}

/** Strict nested output grammar, using the same raw duplicate guard as canonical state. */
internal object LiveboardRevealDecoder {
    private val commitmentPattern = Regex("[0-9a-f]{64}")

    fun disclosure(raw: String, recovered: Boolean): LiveboardRevealValue? = try {
        val envelope = LiveboardJson.record(LiveboardJson.parse(raw))
        require(LiveboardJson.boolean(envelope["success"])) { "InvalidResponse" }
        val message = LiveboardJson.record(envelope["message"])
        val secret = message["secret"] as? JsonPrimitive
        require(secret?.isString == true) { "InvalidResponse" }
        val receipt = if (recovered && "revealReceipt" in message) receipt(message["revealReceipt"]) else null
        LiveboardRevealValue(secret.content, receipt)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    fun context(value: JsonElement?): LiveboardRevealContext {
        val metadata = LiveboardJson.record(value)
        LiveboardJson.fields(metadata, "version", "vaultName", "currentDeviceId", "vaultSource",
            "localDistributionSource", "recoverySources", "claimScopeCommitment")
        LiveboardJson.version(metadata["version"])
        val identity = LiveboardSnapshotIdentity(id(metadata["vaultName"]), id(metadata["currentDeviceId"]))
        val sources = LiveboardRevealSources(source(metadata["vaultSource"], "Vault"),
            source(metadata["localDistributionSource"], "SsDistribution"),
            LiveboardJson.array(metadata["recoverySources"]).map { source(it, "SsRecovery") })
        require(sources.recoveries.size in 1..2 && sources.recoveries.zipWithNext().all {
            (left, right) -> compareSources(left, right) < 0
        }) { "InvalidResponse" }
        val context = LiveboardRevealContext(1, identity, sources, commitment(metadata["claimScopeCommitment"]))
        require(compactBytes(metadata) <= MAX_CONTEXT_BYTES) { "InvalidResponse" }
        return context
    }

    private fun receipt(value: JsonElement?): LiveboardRevealReceipt {
        val metadata = LiveboardJson.record(value)
        LiveboardJson.fields(metadata, "version", "secretId", "recoveryClaimId", "requesterId", "revealContext")
        LiveboardJson.version(metadata["version"])
        val context = context(metadata["revealContext"])
        val receipt = LiveboardRevealReceipt(1, id(metadata["secretId"]), id(metadata["recoveryClaimId"]),
            id(metadata["requesterId"]), context)
        require(receipt.requesterId == context.identity.currentDeviceId && compactBytes(metadata) <=
            MAX_RECEIPT_BYTES) { "InvalidResponse" }
        return receipt
    }

    private fun source(value: JsonElement?, kind: String): LiveboardRevealSource {
        val metadata = LiveboardJson.record(value)
        LiveboardJson.fields(metadata, "eventId", "bodyCommitment")
        val event = LiveboardJson.record(metadata["eventId"])
        LiveboardJson.fields(event, "fqdn", "id")
        val fqdn = LiveboardJson.record(event["fqdn"])
        LiveboardJson.fields(fqdn, "objType", "objInstance")
        require(id(fqdn["objType"]) == kind) { "InvalidResponse" }
        val instance = id(fqdn["objInstance"])
        val sequence = LiveboardJson.record(event["id"])
        LiveboardJson.fields(sequence, "curr", "prev")
        val curr = LiveboardJson.u64(sequence["curr"])
        val prev = LiveboardJson.u64(sequence["prev"])
        require(curr.toULong() in 1uL..MAX_SAFE_SEQUENCE && prev.toULong() == curr.toULong() - 1uL) {
            "InvalidResponse"
        }
        return LiveboardRevealSource(LiveboardArtifactId(LiveboardFqdn(kind, instance),
            LiveboardSequence(curr, prev)), commitment(metadata["bodyCommitment"]))
    }

    private fun id(value: JsonElement?): String {
        val primitive = value as? JsonPrimitive
        require(primitive?.isString == true && primitive.content.isNotEmpty()) { "InvalidResponse" }
        require(primitive.content.encodeToByteArray(throwOnInvalidSequence = true).size <= MAX_ID_BYTES) {
            "InvalidResponse"
        }
        return primitive.content
    }

    private fun commitment(value: JsonElement?): String = id(value).also {
        require(commitmentPattern.matches(it)) { "InvalidResponse" }
    }

    private fun compactBytes(value: JsonObject): Int =
        value.toString().encodeToByteArray(throwOnInvalidSequence = true).size

    private fun compareSources(left: LiveboardRevealSource, right: LiveboardRevealSource): Int {
        val type = compareUtf8(left.eventId.fqdn.objType, right.eventId.fqdn.objType)
        if (type != 0) return type
        val instance = compareUtf8(left.eventId.fqdn.objInstance, right.eventId.fqdn.objInstance)
        return if (instance != 0) instance else left.eventId.id.curr.toLong().compareTo(right.eventId.id.curr.toLong())
    }

    private fun compareUtf8(left: String, right: String): Int {
        val a = left.encodeToByteArray(throwOnInvalidSequence = true)
        val b = right.encodeToByteArray(throwOnInvalidSequence = true)
        for (index in 0 until minOf(a.size, b.size)) {
            val compared = (a[index].toInt() and UNSIGNED_BYTE_MASK)
                .compareTo(b[index].toInt() and UNSIGNED_BYTE_MASK)
            if (compared != 0) return compared
        }
        return a.size.compareTo(b.size)
    }
}

private const val UNSIGNED_BYTE_MASK: Int = 255
private const val MAX_ID_BYTES: Int = 1024
private const val MAX_CONTEXT_BYTES: Int = 8192
private const val MAX_RECEIPT_BYTES: Int = 12288
private const val MAX_SAFE_SEQUENCE: ULong = 9007199254740991uL
