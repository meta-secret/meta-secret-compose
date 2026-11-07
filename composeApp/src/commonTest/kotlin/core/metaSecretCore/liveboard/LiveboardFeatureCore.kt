package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import models.apiModels.liveboard.LiveboardOperationIdentity
import models.apiModels.liveboard.LiveboardStateDecoder

internal object FeatureIds {
    const val join = "10bb5e25-598a-4d9b-8cb7-2f9425960901"
    const val remove = "10bb5e25-598a-4d9b-8cb7-2f9425960902"
    const val delete = "10bb5e25-598a-4d9b-8cb7-2f9425960903"
    const val claim = LiveboardNativeFixtures.claimId
    val kinds = listOf(LiveboardKind.Join, LiveboardKind.RemoveDevice, LiveboardKind.DeleteSecret, LiveboardKind.Recover)
    fun wire(kind: LiveboardKind) = when (kind) {
        LiveboardKind.Join -> "join"
        LiveboardKind.RemoveDevice -> "removeDevice"
        LiveboardKind.DeleteSecret -> "deleteSecret"
        else -> "recover"
    }
    fun request(kind: LiveboardKind) = when (kind) {
        LiveboardKind.Join -> join
        LiveboardKind.RemoveDevice -> remove
        LiveboardKind.DeleteSecret -> delete
        else -> claim
    }
}

/** Real synchronous test Core boundaries; metadata is consumed only through real physical reads. */
internal class LiveboardFeatureCore(trace: NativeRegistrationTrace) : MetaSecretCoreInterface by NativeRegistrationCore(trace) {
    val calls = MutableStateFlow<List<NativeCall>>(emptyList())
    val reads = MutableStateFlow(0)
    val state = MutableStateFlow(member())
    val failures = MutableStateFlow(0)
    private val gates = MutableStateFlow<Map<String, NativeTransitionGate>>(emptyMap())
    var failRead = false
    var decisionRaw = "{\"success\":true}"
    var recoveredFixture: RecoveredFixtureReply? = null

    fun hold(route: String, gate: NativeTransitionGate) { gates.update { it + (route to gate) } }
    private fun take(route: String): NativeTransitionGate? {
        while (true) {
            val old = gates.value
            val gate = old[route] ?: return null
            if (gates.compareAndSet(old, old - route)) return gate
        }
    }
    private fun call(route: String, args: List<String>, raw: String): String = runBlocking {
        calls.update { it + NativeCall(route, args) }
        try { take(route)?.hold(); raw }
        catch (error: Exception) { failures.update { it + 1 }; throw error }
    }
    override fun getAppState(): String = runBlocking {
        reads.update { it + 1 }
        val raw = state.value
        take("read")?.hold()
        check(!failRead) { "Synthetic unreadable feature state" }
        raw
    }
    fun checked(kind: String, request: String = FeatureIds.join) = LiveboardNativeFixtures.envelope(
        LiveboardNativeFixtures.checked(kind = kind, request = request),
    )
    fun producer(kind: String = "removeDevice", continued: Boolean = false) = LiveboardNativeFixtures.envelope(
        LiveboardNativeFixtures.producer(kind = kind, request = if (kind == "deleteSecret") FeatureIds.delete else FeatureIds.remove,
            decision = if (continued) LiveboardNativeFixtures.attemptedDecision() else null),
    )
    override fun clusterDistributionChecked(name: String, value: String) = call("create", listOf(name, value), checked("create"))
    override fun recoverChecked(secretId: String) = call("recover", listOf(secretId), checked("recover"))
    override fun deleteSecretChecked(secretId: String) = call("delete", listOf(secretId),
        producer("deleteSecret").replace("\"secretId\":\"pass-b\"", "\"secretId\":\"$secretId\""))
    override fun removeMemberChecked(targetDeviceId: String) = call("remove", listOf(targetDeviceId), producer())
    override fun reconcileProducerReceipt(reference: String) = call("reconcile", listOf(reference), producer(continued = true))
    override fun continueLocalRemoveChecked(reference: String) = call("continue", listOf(reference), producer(continued = true))
    override fun decideJoinRequest(requestJson: String) = call("join", listOf(requestJson), checked("join", FeatureIds.join))
    override fun decideRemoveMember(requestJson: String) = call("decideRemove", listOf(requestJson), checked("removeDevice", FeatureIds.remove))
    override fun decideDeleteSecret(requestJson: String) = call("decideDelete", listOf(requestJson), decisionRaw)
    override fun acceptRecover(claimId: String) = call("accept", listOf(claimId), decisionRaw)
    override fun declineRecover(claimId: String) = call("decline", listOf(claimId), decisionRaw)
    override fun showLocalSecret(secretId: String) = call("local", listOf(secretId), disclosure("local fixture value"))
    override fun showRecovered(claimId: String): String {
        val fixture = recoveredFixture ?: return call("recovered", listOf(claimId), disclosure("one two three four five six seven eight nine ten eleven twelve"))
        check(claimId == fixture.expectedClaim) { "Synthetic exact recovered Claim mismatch" }
        return call("recovered", listOf(claimId), fixture.raw).also {
            fixture.canonicalAfter?.let { next -> state.value = next }
        }
    }
    override fun findClaim(secretId: String): String = error("No lookup fallback is permitted")

    private fun disclosure(value: String) = JsonObject(mapOf("success" to JsonPrimitive(true),
        "message" to JsonObject(mapOf("secret" to JsonPrimitive(value))))).toString()

    fun primary(action: String, claim: String = FeatureIds.claim, id: String = "pass-b") {
        editMetadata { metadata ->
            metadata["secrets"] = JsonArray((metadata.getValue("secrets") as JsonArray).map { element ->
                val secret = element.jsonObject.toMutableMap()
                if (secret["secretId"] == JsonPrimitive(id)) {
                    secret["primaryAction"] = JsonPrimitive(action)
                    if (action in listOf("showRecovered", "waiting")) secret["recoveryClaimId"] = JsonPrimitive(claim)
                    else secret.remove("recoveryClaimId")
                }
                JsonObject(secret)
            })
        }
    }
    fun stale(identity: LiveboardOperationIdentity, field: String) {
        editMetadata { metadata ->
            metadata["operations"] = JsonArray((metadata.getValue("operations") as JsonArray).map { element ->
                val operation = element.jsonObject.toMutableMap()
                if (operation["kind"] == JsonPrimitive(FeatureIds.wire(identity.kind)) && operation["requestId"] == JsonPrimitive(identity.requestId)) {
                    when (field) {
                        "actor" -> operation["requesterId"] = JsonPrimitive("another-actor")
                        "kind" -> operation["kind"] = JsonPrimitive("join")
                        "claim" -> operation["requestId"] = JsonPrimitive("another-opaque-claim")
                        else -> { operation["canApprove"] = JsonPrimitive(false); operation["canDecline"] = JsonPrimitive(false) }
                    }
                }
                JsonObject(operation)
            })
        }
    }
    fun fresh() { state.value = member(); decisionRaw = "{\"success\":true}"; recoveredFixture = null }
    private fun editMetadata(change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit) {
        val decoded = models.apiModels.AppStateModel.fromJson(state.value)
        val member = (decoded.getVaultFullInfo() as models.apiModels.VaultFullInfo.Member).member
        val metadata = member.liveboardState!!.jsonObject.toMutableMap()
        change(metadata)
        state.value = LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        check(LiveboardStateDecoder.decode(state.value) != null)
    }
    companion object {
        fun member(): String {
            val metadata = Json.parseToJsonElement(LiveboardNativeFixtures.metadata).jsonObject.toMutableMap()
            metadata["secrets"] = JsonArray((metadata.getValue("secrets") as JsonArray).map { element ->
                JsonObject(element.jsonObject.toMutableMap().apply { put("canDelete", JsonPrimitive(true)) })
            })
            metadata["operations"] = JsonArray(FeatureIds.kinds.map { kind ->
                val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                    "kind" to JsonPrimitive(FeatureIds.wire(kind)), "requestId" to JsonPrimitive(FeatureIds.request(kind)),
                    "requesterId" to JsonPrimitive(if (kind == LiveboardKind.Join) "outsider-d" else "device-b"),
                    "clientStatus" to JsonPrimitive("needApprove"), "eligiblePeerIds" to JsonArray(listOf(JsonPrimitive("device-a"))),
                    "canApprove" to JsonPrimitive(true), "canDecline" to JsonPrimitive(true),
                    "resolutionSource" to JsonPrimitive("unknown"), "readiness" to JsonPrimitive("waiting"),
                )
                if (kind == LiveboardKind.RemoveDevice) fields["targetDeviceId"] = JsonPrimitive("device-c")
                if (kind in listOf(LiveboardKind.DeleteSecret, LiveboardKind.Recover)) fields["secretId"] = JsonPrimitive("pass-b")
                JsonObject(fields)
            })
            return LiveboardNativeFixtures.member(JsonObject(metadata).toString())
        }
    }
}
