package core.metaSecretCore.liveboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import models.apiModels.AppStateModel
import models.apiModels.VaultFullInfo
import models.apiModels.liveboard.LiveboardStateDecoder
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Test boundary metadata only; tuple status/result fields derive from Root V11 native evidence. */
internal object RecoverApproveRetryWire {
    const val CANARY = "private-retry-error-canary"
    fun proof(claim: String = FeatureIds.claim): String = """{"success":false,"error":"$CANARY",
        "decisionAttempt":{"schemaVersion":1,"kind":"recoverApprove","claimId":"$claim",
        "actorId":"device-a","vaultName":"parser-vault","phase":"notSubmitted"}}"""

    fun invalid(): List<String> {
        val raw = proof()
        return listOf(
            raw.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            raw.replace("\"schemaVersion\":1", "\"schemaVersion\":true"),
            raw.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            raw.replace("\"schemaVersion\":1,", ""),
            raw.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            raw.replace("\"success\":false", "\"success\":false,\"success\":false"),
            raw.replace("\"kind\":\"recoverApprove\"", "\"kind\":\"recoverDecline\""),
            raw.replace("\"phase\":\"notSubmitted\"", "\"phase\":\"unknown\""),
            raw.replace("\"phase\":\"notSubmitted\"", "\"phase\":null"),
            raw.replace("\"phase\":\"notSubmitted\"", "\"phase\":\"notSubmitted\",\"extra\":1"),
            raw.replace("\"actorId\":\"device-a\"", "\"actorId\":\"device-b\""),
            raw.replace("\"actorId\":\"device-a\"", "\"actorId\":\"\""),
            raw.replace("\"actorId\":\"device-a\"", "\"actorId\":null"),
            raw.replace("\"vaultName\":\"parser-vault\"", "\"vaultName\":\"other-vault\""),
            raw.replace(FeatureIds.claim, "another-opaque-claim"),
            raw.replace("\"success\":false", "\"success\":true"),
            raw.replace("\"success\":false", "\"success\":\"false\""),
            raw.replace("\"error\":\"$CANARY\",", "\"error\":\"$CANARY\",\"extra\":true,"),
            "{\"success\":false,\"decisionAttempt\":null}",
            "{\"success\":false,\"error\":\"$CANARY\"}",
            raw.replace("\"actorId\":\"device-a\"", "\"actorId\":\"${"x".repeat(4097)}\""),
            raw.replace("\"claimId\":\"${FeatureIds.claim}\"", "\"claimId\":3"),
            raw.dropLast(1),
        )
    }

    fun terminal(f: LiveboardFeatureFixture, case: String, change: String? = null) {
        val decoded = AppStateModel.fromJson(f.core.state.value)
        val member = (decoded.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = assertNotNull(member.liveboardState).jsonObject.toMutableMap()
        val operations = (metadata.getValue("operations") as JsonArray).map { element ->
            val fields = element.jsonObject.toMutableMap()
            if (fields["kind"] == JsonPrimitive("recover") && fields["requestId"] == JsonPrimitive(FeatureIds.claim)) {
                fields["readiness"] = JsonPrimitive("waiting")
                fields["clientStatus"] = JsonPrimitive(if (case == "peer-approve") "needApprove" else "unknown")
                fields["canApprove"] = JsonPrimitive(false)
                fields["canDecline"] = JsonPrimitive(false)
                fields["outcome"] = if (case == "own-decline-negative") JsonNull
                    else JsonPrimitive(if (case == "peer-decline") "declined" else "accepted")
                fields["responderId"] = if (case == "own-decline-negative") JsonNull
                    else JsonPrimitive(if (case == "own-approve") "device-a" else "device-c")
                fields["resolutionSource"] = JsonPrimitive(if (case == "own-decline-negative") "unknown" else "verified")
                when (change) {
                    "missing-outcome" -> fields.remove("outcome")
                    "missing-responder" -> fields.remove("responderId")
                    "unknown-resolution" -> fields["resolutionSource"] = JsonPrimitive("unknown")
                    "actionable" -> fields["canApprove"] = JsonPrimitive(true)
                    "requester" -> fields["requesterId"] = JsonPrimitive("device-c")
                    "secret" -> fields["secretId"] = JsonPrimitive("pass-a")
                    "claim" -> fields["requestId"] = JsonPrimitive("different-opaque-claim")
                    "generic-terminal-without-evidence" -> {
                        fields.remove("outcome"); fields.remove("responderId")
                        fields["readiness"] = JsonPrimitive("done")
                    }
                }
            }
            JsonObject(fields)
        }
        metadata["operations"] = JsonArray(operations)
        val next = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        assertNotNull(LiveboardStateDecoder.decode(next), "Terminal boundary fixture must be valid actual wire metadata")
        f.core.state.value = next
    }
    fun duplicateOperation(f: LiveboardFeatureFixture) {
        val decoded = AppStateModel.fromJson(f.core.state.value)
        val member = (decoded.getVaultFullInfo() as VaultFullInfo.Member).member
        val metadata = assertNotNull(member.liveboardState).jsonObject.toMutableMap()
        val operations = (metadata.getValue("operations") as JsonArray).toList()
        val recover = operations.single { it.jsonObject["kind"] == JsonPrimitive("recover") }
        metadata["operations"] = JsonArray(operations + recover)
        val next = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        assertNull(LiveboardStateDecoder.decode(next), "Duplicate operation is deliberately invalid canonical metadata")
        f.core.state.value = next
    }

}
