package ui.liveboard

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import models.apiModels.AppStateModel
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.State
import models.apiModels.VaultFullInfo

/** Controlled current R32 wire proof; does not decide authorization or reconstruct a value. */
internal object AndroidRecoveredHideFixture {
    const val claim = "renderer-claim"
    val cycleClaims = listOf("renderer-next-claim-one", "renderer-next-claim-two",
        "renderer-next-claim-three", "renderer-next-claim-four")
    const val value = "one two three four five six seven eight nine ten eleven twelve"

    private fun source(kind: String, instance: String) = buildJsonObject {
        put("eventId", buildJsonObject {
            put("fqdn", buildJsonObject { put("objType", kind); put("objInstance", instance) })
            put("id", buildJsonObject { put("curr", 1); put("prev", 0) })
        })
        put("bodyCommitment", "12".repeat(32))
    }

    private fun context(expectedClaim: String) = buildJsonObject {
        put("version", 1); put("vaultName", "Renderer vault"); put("currentDeviceId", "device-a")
        put("vaultSource", source("Vault", "vault-source"))
        put("localDistributionSource", source("SsDistribution", "local-source"))
        put("recoverySources", JsonArray(listOf(source("SsRecovery", "recovery-source"))))
        put("claimScopeCommitment", when (expectedClaim) {
            cycleClaims[0] -> "cd"; cycleClaims[1] -> "ef"; cycleClaims[2] -> "01"
            cycleClaims[3] -> "23"; else -> "ab"
        }.repeat(32))
    }

    val response: String get() = response(claim)

    fun response(expectedClaim: String) = buildJsonObject {
        put("success", true)
        put("message", buildJsonObject {
            put("secret", value)
            put("revealReceipt", buildJsonObject {
                put("version", 1); put("secretId", "pass-b"); put("recoveryClaimId", expectedClaim)
                put("requesterId", "device-a"); put("revealContext", context(expectedClaim))
            })
        })
    }.toString()

    fun member(done: Boolean, expectedClaim: String = claim): String {
        val parsed = AppStateModel.fromJson(AndroidBoardCore.MEMBER)
        val member = (parsed.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = requireNotNull(member.liveboardState).jsonObject.toMutableMap()
        metadata["storageMode"] = JsonPrimitive("shares")
        metadata["secrets"] = JsonArray(metadata.getValue("secrets").jsonArray.map { item ->
            if (item.jsonObject["secretId"] != JsonPrimitive("pass-b")) item else buildJsonObject {
                put("secretId", "pass-b"); put("primaryAction", if (done) "recover" else "showRecovered")
                if (!done) put("recoveryClaimId", expectedClaim)
                put("canDelete", false); put("readiness", "ready")
            }
        })
        metadata["operations"] = JsonArray(listOf(buildJsonObject {
            put("kind", "recover"); put("requestId", expectedClaim); put("requesterId", "device-a")
            put("secretId", "pass-b"); put("clientStatus", if (done) "done" else "accepted")
            put("eligiblePeerIds", JsonArray(emptyList())); put("canApprove", false); put("canDecline", false)
            put("outcome", "accepted"); put("responderId", "device-b"); put("resolutionSource", "verified")
            put("readiness", if (done) "waiting" else "ready"); put("revealContext", context(expectedClaim))
        }))
        val updated = member.copy(liveboardState = JsonObject(metadata))
        val raw = JsonConfig.json.encodeToString(AppStateModel(Message(State.Vault(VaultFullInfo.Member(updated))), true))
        return withThirdMember(Json.parseToJsonElement(raw),
            listOf("message", "state", "vault", "member", "member", "vault", "users")).toString()
    }

    private fun withThirdMember(element: JsonElement, path: List<String>): JsonObject {
        if (path.isEmpty()) return JsonObject(element.jsonObject + ("device-c" to buildJsonObject {
            put("member", buildJsonObject { put("userData", buildJsonObject {
                put("vaultName", "Renderer vault")
                put("device", buildJsonObject {
                    put("deviceId", "device-c"); put("deviceName", "Third fixture phone")
                    put("deviceType", "Other"); put("uiCategory", "android")
                    put("keys", buildJsonObject {
                        put("dsaPk", "fixture-dsa-c"); put("transportPk", "fixture-transport-c")
                    })
                })
            }) })
        }))
        val fields = element.jsonObject.toMutableMap()
        val key = path.first()
        fields[key] = withThirdMember(fields.getValue(key), path.drop(1))
        return JsonObject(fields)
    }
}
