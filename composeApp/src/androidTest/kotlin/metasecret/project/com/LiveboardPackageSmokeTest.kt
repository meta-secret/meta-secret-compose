package metasecret.project.com

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metasecret.core.MetaSecretNative
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.mobile_uniffi.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Real generated UniFFI calls in one fresh native sandbox; no UI/provider substitution. */
@RunWith(AndroidJUnit4::class)
class LiveboardPackageSmokeTest {
    private val origin = "http://10.0.2.2:3002"
    private lateinit var runId: String
    private var cursor = 0L
    private val events = mutableListOf<JSONObject>()
    private var phase = "initialized"

    @Test
    fun realGeneratedAbi() {
        try {
            val config = http("/config")
            runId = config.getString("runId")
            check(runId.matches(Regex("[A-Za-z0-9_.-]{1,64}")))
            check(config.getString("actor") == "A" && config.getString("vaultName") == "g4-$runId")
            InstrumentationRegistry.getArguments().getString("g4RunId")?.let { check(it == runId) }
            awaitPhase("host", "capability-negative")
            check(MetaSecretNative.initRustlsPlatformVerifier(InstrumentationRegistry.getInstrumentation().targetContext))
            val key = success(generateMasterKey()).getString("message")
            success(initAndroidWithDevice(key, "g4-native-A", "Android"))
            check(success(getState()).getJSONObject("message").getJSONObject("state").has("local"))
            emit("initialized", checks = mapOf("realPublicInit" to true, "freshLocal" to true))
            success(generateUserCreds(config.getString("vaultName")))
            success(signUp())
            val firstMember = awaitMember { memberCount(it) == 1 }
            val actorId = firstMember.getJSONObject("liveboardState").getString("currentDeviceId")
            emit("registered", mapOf("deviceId" to actorId))
            val peerIds = listOf("B", "C").associateWith { peer ->
                val incoming = awaitPhase(peer, "join-submitted").getJSONObject("ids").getString("deviceId")
                val state = awaitMember { operation(it, "join", incoming) != null }
                val request = requireNotNull(operation(state, "join", incoming)).getString("requestId")
                oldReceipt(decideJoinRequest(decision(request)), "join", request)
                success(getState())
                awaitPhase(peer, "member")
                incoming
            }
            awaitMember { memberCount(it) == 3 }
            emit("member", checks = mapOf("member3" to true))
            val secret = "G4 package fixture value"
            val created = oldReceipt(clusterDistributionChecked(config.getString("primaryName"), secret), "create")
            val passId = created.getString("secretId")
            val deleted = oldReceipt(clusterDistributionChecked(config.getString("deleteName"), UUID.randomUUID().toString()), "create")
            val deleteId = deleted.getString("secretId")
            awaitMember { secretPresent(it, passId) && secretPresent(it, deleteId) }
            emit("secret-created", mapOf("passId" to passId))
            val recovery = oldReceipt(recoverChecked(passId), "recover")
            val firstClaim = recovery.getString("claimId")
            emit("recover1-requested", mapOf("passId" to passId, "claimId" to firstClaim))
            awaitPhase("B", "recover1-approved")
            awaitMember { claim(it, firstClaim)?.optString("clientStatus") == "accepted" }
            check(success(showRecovered(firstClaim)).getJSONObject("message").getString("secret") == secret)
            emit("recover1-shown", mapOf("claimId" to firstClaim), mapOf("plaintextMatchesInRam" to true))
            val second = awaitPhase("B", "recover2-requested").getJSONObject("ids").getString("claimId")
            check(second != firstClaim)
            val beforeSecond = awaitMember { claim(it, second)?.optString("clientStatus") == "needApprove" }
            check(claim(beforeSecond, firstClaim) != null)
            val exact = requireNotNull(claim(beforeSecond, second))
            check(exact.getString("id") == second && exact.getString("sender") == peerIds.getValue("B"))
            check(exact.getJSONObject("distClaimId").getJSONObject("passId").getString("id") == passId)
            success(acceptRecover(second))
            emit("recover2-approved", mapOf("claimId" to second, "firstClaimId" to firstClaim), mapOf("exactSecondOpaqueId" to true))
            awaitPhase("B", "second-selected")
            awaitPhase("B", "second-shown")
            val deleteReceipt = producer(deleteSecretChecked(deleteId), "deleteSecret")
            emit("delete-produced", mapOf("passId" to deleteId, "requestId" to deleteReceipt.value.getString("requestId"), "referenceJson" to deleteReceipt.reference))
            awaitPhase("B", "delete-approved")
            awaitMember { !secretPresent(it, deleteId) && secretPresent(it, passId) }
            sameSources(deleteReceipt, producer(reconcileProducerReceipt(deleteReceipt.reference), "deleteSecret"))
            val remove3 = awaitPhase("B", "remove3-produced").getJSONObject("ids").getString("requestId")
            val incomingRemove = awaitMember { operation(it, "removeDevice", peerIds.getValue("B"))?.optString("requestId") == remove3 }
            check(operation(incomingRemove, "removeDevice", peerIds.getValue("B"))?.getString("targetDeviceId") == peerIds.getValue("C"))
            oldReceipt(decideRemoveMember(decision(remove3)), "removeDevice", remove3)
            emit("remove3-approved", mapOf("requestId" to remove3))
            awaitMember { memberCount(it) == 2 && secretPresent(it, passId) && localReady(it, passId) }
            check(success(showLocalSecret(passId)).getJSONObject("message").getString("secret") == secret)
            emit("member2", checks = mapOf("member2" to true, "localPrimaryReady" to true, "plaintextMatchesInRam" to true))
            awaitPhase("host", "observation-fault-armed")
            val remove = producer(removeMemberChecked(peerIds.getValue("B")), "removeDevice")
            check(remove.value.getString("phase") == "unknown")
            check(remove.value.getJSONObject("decisionStage").getString("state") == "attempted")
            emit("remove2-produced", mapOf("referenceJson" to remove.reference), mapOf("unknown" to true,
                "materialReady" to (remove.value.optString("materialStage") == "ready"), "materialUnknown" to (remove.value.optString("materialStage") == "unknown"),
                "materialPending" to (remove.value.optString("materialStage") == "pending"), "canonicalApplied" to (remove.value.optString("canonicalState") == "applied"),
                "canonicalUnknown" to (remove.value.optString("canonicalState") == "unknown"), "knownOperationUnavailable" to (remove.value.optString("errorCode") == "OperationUnavailable")))
            val before = http("/evidence")
            val uncertain = producer(reconcileProducerReceipt(remove.reference), "removeDevice")
            sameSources(remove, uncertain)
            check(uncertain.value.getString("phase") == "unknown")
            readOnlyObservation(before, http("/evidence"))
            emit("reconciled-unknown", mapOf("referenceJson" to remove.reference), mapOf("sameSources" to true, "noResubmission" to true))
            awaitPhase("host", "observation-fault-cleared")
            val afterFault = http("/evidence")
            val applied = producer(reconcileProducerReceipt(remove.reference), "removeDevice")
            sameSources(remove, applied)
            sameSources(uncertain, applied)
            check(applied.value.getString("canonicalState") == "applied" && applied.value.getString("requestId").isNotBlank())
            readOnlyObservation(afterFault, http("/evidence"))
            emit("reconciled-applied", mapOf("referenceJson" to remove.reference), mapOf("sameSources" to true))
            awaitMember { memberCount(it) == 1 && secretPresent(it, passId) }
            val continued = producer(continueLocalRemoveChecked(remove.reference), "removeDevice")
            sameSources(remove, continued)
            sameSources(applied, continued)
            check(continued.value.getString("canonicalState") == "applied" && continued.value.getString("materialStage") == "ready")
            emit("continued", mapOf("referenceJson" to remove.reference), mapOf("sameSources" to true))
            val negativeBefore = http("/evidence")
            fixedFailure(reconcileProducerReceipt("{}"), "InvalidProducerReference")
            fixedFailure(decideJoinRequest("{}"), "InvalidDecisionInput")
            check(http("/evidence").toString() == negativeBefore.toString())
            emit("complete", checks = mapOf("generatedNativeAbi" to true, "fixedNativeFailureEnvelope" to true, "noLegacyFallback" to true))
        } catch (_: Throwable) {
            if (::runId.isInitialized) runCatching { emit(phase, failed = true) }
            throw AssertionError("G4 native package smoke failed at bounded phase $phase; inspect redacted host evidence")
        }
    }

    private data class Receipt(val value: JSONObject, val reference: String, val source: String, val decision: String?)

    private fun success(raw: String): JSONObject {
        val result = JSONObject(raw)
        check(result.optBoolean("success")) { "G4 native call failed" }
        return result
    }

    private fun oldReceipt(raw: String, kind: String, request: String? = null): JSONObject {
        val outer = success(raw)
        check(outer.keys().asSequence().toSet() == setOf("success", "message"))
        val receipt = outer.getJSONObject("message")
        check(receipt.getString("kind") == kind && receipt.getString("phase") in setOf("queued", "unknown"))
        check(receipt.keys().asSequence().all { it in setOf("kind", "secretId", "claimId", "requestId", "phase", "errorCode") })
        if (request != null) check(receipt.getString("requestId") == request)
        return receipt
    }

    private fun producer(raw: String, kind: String): Receipt {
        val outer = success(raw)
        check(outer.keys().asSequence().toSet() == setOf("success", "message"))
        val receipt = outer.getJSONObject("message")
        check(receipt.keys().asSequence().all { it in setOf("version", "kind", "phase", "secretId", "targetDeviceId", "requestId", "requestStage", "decisionStage", "materialStage", "canonicalState", "readiness", "errorCode") })
        check(receipt.getInt("version") == 1 && receipt.getString("kind") == kind)
        check(receipt.getString("phase") in setOf("queued", "unknown"))
        val exact = rawField(raw, "message")
        val request = rawField(rawField(exact, "requestStage"), "source")
        val decision = if (receipt.optJSONObject("decisionStage")?.optString("state") == "attempted") rawField(rawField(exact, "decisionStage"), "source") else null
        val uuid = if (receipt.has("requestId")) ",\"requestId\":" + rawField(exact, "requestId") else ""
        val decisionPart = decision?.let { ",\"decisionStage\":$it" } ?: ""
        val reference = "{\"version\":1,\"kind\":${JSONObject.quote(kind)}$uuid,\"requestStage\":$request$decisionPart}"
        return Receipt(receipt, reference, request, decision)
    }

    private fun sameSources(original: Receipt, observed: Receipt) {
        check(original.source == observed.source && original.decision == observed.decision)
        if (original.value.has("requestId")) check(original.value.getString("requestId") == observed.value.getString("requestId"))
        check(original.value.optString("targetDeviceId") == observed.value.optString("targetDeviceId"))
    }

    private fun readOnlyObservation(before: JSONObject, after: JSONObject) {
        check(after.getLong("observations") == before.getLong("observations") + 1)
        check(after.getLong("writes") == before.getLong("writes"))
        check(after.getLong("ordinaryReads") == before.getLong("ordinaryReads"))
        check(after.getJSONArray("business").toString() == before.getJSONArray("business").toString())
    }

    private fun fixedFailure(raw: String, code: String) {
        val value = JSONObject(raw)
        check(value.keys().asSequence().toSet() == setOf("success", "errorCode"))
        check(!value.getBoolean("success") && value.getString("errorCode") == code)
    }

    private fun member(): JSONObject = success(getState()).getJSONObject("message").getJSONObject("state").getJSONObject("vault").getJSONObject("member")
    private fun secretPresent(member: JSONObject, id: String): Boolean = member.getJSONObject("liveboardState").getJSONArray("secrets").let { list -> (0 until list.length()).any { list.getJSONObject(it).getString("secretId") == id } }
    private fun localReady(member: JSONObject, id: String): Boolean = member.getJSONObject("liveboardState").getJSONArray("secrets").let { list ->
        (0 until list.length()).any { index -> list.getJSONObject(index).let { it.getString("secretId") == id && it.getString("primaryAction") == "showLocal" && it.getString("readiness") == "ready" } }
    }
    private fun memberCount(member: JSONObject): Int = member.getJSONObject("member").getJSONObject("vault").getJSONObject("users").let { users -> users.keys().asSequence().count { users.getJSONObject(it).has("member") } }
    private fun claim(member: JSONObject, id: String): JSONObject? = member.getJSONObject("ssClaims").getJSONObject("claims").optJSONObject(id)
    private fun operation(member: JSONObject, kind: String, requester: String): JSONObject? = member.getJSONObject("liveboardState").getJSONArray("operations").let { list -> (0 until list.length()).map { list.getJSONObject(it) }.singleOrNull { it.getString("kind") == kind && it.getString("requesterId") == requester && it.getBoolean("canApprove") } }
    private fun decision(id: String): String = JSONObject().put("requestId", id).put("accepted", true).toString()

    private fun awaitMember(ready: (JSONObject) -> Boolean): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 120_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = member()
            if (ready(state)) return state
            pull()
        }
        error("G4 member condition deadline")
    }

    private fun awaitPhase(actor: String, wanted: String): JSONObject {
        phase = wanted
        val deadline = SystemClock.elapsedRealtime() + 120_000
        while (SystemClock.elapsedRealtime() < deadline) {
            events.firstOrNull { it.getString("actor") == actor && it.getString("phase") == wanted }?.let { return it }
            pull()
        }
        error("G4 phase deadline")
    }

    private fun pull() {
        val response = http("/phase?runId=${URLEncoder.encode(runId, "UTF-8")}&after=$cursor")
        check(response.getString("runId") == runId)
        val batch = response.getJSONArray("events")
        for (i in 0 until batch.length()) {
            val event = batch.getJSONObject(i)
            check(event.getLong("cursor") > cursor)
            cursor = event.getLong("cursor")
            check(event.getString("status") == "ready") { "G4 peer failed" }
            events.add(event)
        }
    }

    private fun emit(name: String, ids: Map<String, String> = emptyMap(), checks: Map<String, Boolean> = emptyMap(), failed: Boolean = false) {
        phase = name
        http("/phase", JSONObject().put("runId", runId).put("actor", "A").put("phase", name).put("status", if (failed) "failed" else "ready").put("ids", JSONObject(ids)).put("checks", JSONObject(checks)))
    }

    private fun http(path: String, body: JSONObject? = null): JSONObject {
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 25_000
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode == 200) { "G4 coordination HTTP failed" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            check(text.length <= 1_048_576)
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    /** Preserve Core u64 JSON tokens: never route a source through floating point or re-encoding. */
    private fun rawField(json: String, key: String): String {
        var index = 0
        fun white() { while (index < json.length && json[index].isWhitespace()) index++ }
        fun value(): String {
            white()
            val start = index
            var depth = 0
            var quoted = false
            var escaped = false
            while (index < json.length) {
                val ch = json[index]
                if (quoted) {
                    if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
                } else when (ch) {
                    '"' -> quoted = true
                    '{', '[' -> depth++
                    '}', ']' -> if (depth == 0) break else depth--
                    ',' -> if (depth == 0) break
                }
                index++
            }
            return json.substring(start, index).trim()
        }
        white()
        check(json[index++] == '{')
        while (index < json.length) {
            white()
            if (json[index] == '}') break
            val start = index++
            while (index < json.length && json[index] != '"') { if (json[index] == '\\') index++; index++ }
            index++
            val name = JSONObject("{${json.substring(start, index)}:0}").keys().next()
            white()
            check(json[index++] == ':')
            val raw = value()
            if (name == key) return raw
            white()
            if (json[index] == ',') index++ else break
        }
        error("G4 missing required JSON field")
    }
}
