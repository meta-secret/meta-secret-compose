package core.metaSecretCore.liveboard

import kotlinx.serialization.json.*
import ui.liveboard.LiveboardRootTestFixture

/** Synthetic wire inputs from Core v1 grammar; no client permission/reconstruction implementation. */
internal object RecoveredRevealFixture {
    const val freshClaim = "independent-fresh-recovery-claim-r32"
    const val value = "one two three four five six seven eight nine ten eleven twelve"
    private val scope = "ab".repeat(32)

    fun source(kind: String, instance: String, curr: String = "1"): JsonObject = buildJsonObject {
        put("eventId", buildJsonObject {
            put("fqdn", buildJsonObject { put("objType", kind); put("objInstance", instance) })
            put("id", buildJsonObject {
                put("curr", Json.parseToJsonElement(curr))
                put("prev", Json.parseToJsonElement((curr.toULong() - 1u).toString()))
            })
        })
        put("bodyCommitment", "12".repeat(32))
    }

    fun context(claim: String = FeatureIds.claim): JsonObject = buildJsonObject {
        put("version", 1); put("vaultName", "parser-vault"); put("currentDeviceId", "device-a")
        put("vaultSource", source("Vault", "vault-source"))
        put("localDistributionSource", source("SsDistribution", "local-source"))
        put("recoverySources", JsonArray(listOf(source("SsRecovery", "recovery-source"))))
        put("claimScopeCommitment", if (claim == freshClaim) "cd".repeat(32) else scope)
    }

    fun receipt(claim: String = FeatureIds.claim, proof: JsonElement = context(claim)): JsonObject =
        buildJsonObject {
            put("version", 1); put("secretId", "pass-b"); put("recoveryClaimId", claim)
            put("requesterId", "device-a"); put("revealContext", proof)
        }

    fun response(proof: JsonElement? = receipt(), plaintext: String = value): String = buildJsonObject {
        put("success", true)
        put("message", buildJsonObject {
            put("secret", plaintext)
            if (proof != null) put("revealReceipt", proof)
        })
    }.toString()

    fun member(done: Boolean = false, claim: String = FeatureIds.claim,
        proof: JsonElement? = context(claim)): String {
        val metadata = Json.parseToJsonElement(LiveboardNativeFixtures.metadata).jsonObject.toMutableMap()
        metadata["storageMode"] = JsonPrimitive("shares")
        metadata["secrets"] = JsonArray(metadata.getValue("secrets").jsonArray.map { item ->
            if (item.jsonObject["secretId"] != JsonPrimitive("pass-b")) item else buildJsonObject {
                put("secretId", "pass-b"); put("primaryAction", if (done) "recover" else "showRecovered")
                if (!done) put("recoveryClaimId", claim)
                put("canDelete", false); put("readiness", "ready")
            }
        })
        metadata["operations"] = JsonArray(listOf(buildJsonObject {
            put("kind", "recover"); put("requestId", claim); put("requesterId", "device-a")
            put("secretId", "pass-b"); put("clientStatus", if (done) "done" else "accepted")
            put("eligiblePeerIds", JsonArray(emptyList())); put("canApprove", false); put("canDecline", false)
            put("outcome", "accepted"); put("responderId", "device-b"); put("resolutionSource", "verified")
            // Core retains ordinary operation readiness on historical Done; no inferred done/settled.
            put("readiness", if (done) "waiting" else "ready")
            if (proof != null) put("revealContext", proof)
        }))
        val raw = Json.parseToJsonElement(LiveboardNativeFixtures.member(JsonObject(metadata).toString()))
        return edit(raw, listOf("message", "state", "vault", "member", "member", "vault", "users")) { users ->
            JsonObject(users.jsonObject + ("device-c" to buildJsonObject {
                put("member", buildJsonObject { put("userData", buildJsonObject {
                    put("vaultName", "parser-vault")
                    put("device", buildJsonObject {
                        put("deviceId", "device-c"); put("deviceName", "Third fixture phone"); put("deviceType", "Other")
                        put("keys", buildJsonObject { put("dsaPk", "fixture-dsa-c"); put("transportPk", "fixture-transport-c") })
                    })
                }) })
            }))
        }.toString()
    }

    fun install(f: LiveboardRootTestFixture, claim: String = FeatureIds.claim, receiptPresent: Boolean = true,
        completes: Boolean = true) {
        f.core.state.value = member(claim = claim)
        f.core.recoveredFixture = RecoveredFixtureReply(claim, response(if (receiptPresent) receipt(claim) else null),
            if (completes) member(done = true, claim = claim) else null)
    }

    fun edit(element: JsonElement, path: List<String>, change: (JsonElement) -> JsonElement): JsonElement {
        if (path.isEmpty()) return change(element)
        val fields = element.jsonObject.toMutableMap()
        val key = path.first()
        fields[key] = edit(fields.getValue(key), path.drop(1), change)
        return JsonObject(fields)
    }

    fun field(element: JsonElement, name: String, value: JsonElement): JsonObject =
        JsonObject(element.jsonObject + (name to value))

    fun contextSized(bytes: Int): JsonObject {
        val base = context().toMutableMap()
        base["vaultSource"] = source("Vault", "\u0001".repeat(1000))
        base["localDistributionSource"] = source("SsDistribution", "")
        val remaining = bytes - JsonObject(base).toString().encodeToByteArray().size
        require(remaining > 0)
        val instance = "\u0001".repeat(remaining / 6) + "x".repeat(remaining % 6)
        require(instance.encodeToByteArray().size <= 1024)
        base["localDistributionSource"] = source("SsDistribution", instance)
        return JsonObject(base).also { require(it.toString().encodeToByteArray().size == bytes) }
    }

    fun receiptSized(bytes: Int): Pair<String, JsonObject> {
        val base = receipt(proof = contextSized(8192)).toMutableMap()
        base["secretId"] = JsonPrimitive("")
        val remaining = bytes - JsonObject(base).toString().encodeToByteArray().size
        require(remaining > 0)
        val id = "\u0001".repeat(remaining / 6) + "x".repeat(remaining % 6)
        require(id.encodeToByteArray().size <= 1024)
        base["secretId"] = JsonPrimitive(id)
        return id to JsonObject(base).also { require(it.toString().encodeToByteArray().size == bytes) }
    }

    fun replacePass(raw: String, id: String): String {
        fun replace(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { replace(it.value) })
            is JsonArray -> JsonArray(element.map(::replace))
            else -> if (element == JsonPrimitive("pass-b")) JsonPrimitive(id) else element
        }
        return replace(Json.parseToJsonElement(raw)).toString()
    }
}

internal class RecoveredFixtureReply(val expectedClaim: String, val raw: String, val canonicalAfter: String?)
