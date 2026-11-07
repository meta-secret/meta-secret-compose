package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import models.apiModels.liveboard.LiveboardSnapshot
import models.apiModels.liveboard.LiveboardStateDecoder
import testutils.FakeStringProvider
import ui.liveboard.LiveboardRootTestFixture

/** Wire mutations and existing real services only; no copied production completion predicate. */
internal class LiveboardRecoverCompletionFixture(private val phase: String = "unknown") {
    val f = LiveboardRootTestFixture()
    lateinit var session: LiveboardSession
    var replyClaim = firstClaim
    private val native = object : MetaSecretCoreInterface by f.core {
        override fun recoverChecked(secretId: String): String {
            f.core.recoverChecked(secretId) // Record the actual selected native boundary.
            return LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(
                kind = "recover", phase = phase, claim = replyClaim))
        }
    }

    suspend fun start() {
        f.core.state.value = raw("empty")
        f.start()
        val environment = NativeReadEnvironment(Dispatchers.IO, f.main)
        val checked = NativeLiveboardOperations(LiveboardCoreAdapter(native), f.operations, environment)
        val factory = LiveboardSessionFactory(checked, f.reads, f.operations, environment,
            LiveboardClientPorts(f.privacy, f.auth, f.socket, f.notifications, FakeStringProvider()))
        session = assertNotNull(factory.open(assertNotNull(f.reads.frames.value)))
        f.idle()
    }

    suspend fun issue(): Pair<LiveboardRecoverIntent, NativeLiveboardOutcome<LiveboardCheckedReceipt>> {
        val intent = assertNotNull(session.actions.recoverIntent("pass-b"))
        val outcome = assertIs<NativeLiveboardOutcome.Entered<LiveboardCheckedReceipt>>(session.actions.recover(intent))
        val receipt = assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(outcome.result).value
        assertEquals(replyClaim, receipt.identity.claimId)
        assertEquals(if (phase == "unknown") LiveboardPhase.Unknown else LiveboardPhase.Queued, receipt.phase)
        session.awaitSettled()
        return intent to outcome
    }

    suspend fun proof(mode: String = "valid"): NativeReadFrame {
        f.core.state.value = raw(mode)
        val frame = f.proof(); f.idle()
        LiveboardStateDecoder.decode(requireNotNull(frame.state))?.let { session.observeCanonical(frame, it) }
        return frame
    }

    suspend fun coveredDoneWithoutObservation(): Pair<NativeReadFrame, LiveboardSnapshot> {
        session.inbox.dispose() // Only observers.cancel(); Session/native owner stay live until the test invalidates them.
        f.core.state.value = raw()
        val frame = f.proof(); f.idle()
        val member = assertNotNull(LiveboardStateDecoder.decode(requireNotNull(frame.state)))
        assertSame(frame, f.reads.frames.value)
        assertEquals(session.scope, frame.scope); assertEquals(NativeReadOutcome.SUCCESS, frame.outcome)
        assertEquals(this.member(), member); assertEquals(member, session.currentMember())
        assertTrue(frame.coveredRequirements.size >= 2, "Actual journal demand plus explicit physical proof demand")
        for (required in frame.coveredRequirements) {
            assertEquals(session.scope, required.scope); assertSame(frame, required.completion.await())
        }
        assertEquals(1, selected().size, "Valid covered Done has not been observed by this paused Session")
        return frame to member
    }

    fun member(mode: String = "valid"): LiveboardSnapshot = assertNotNull(LiveboardStateDecoder.decode(raw(mode)))

    fun raw(mode: String = "valid"): String {
        var raw = Json.parseToJsonElement(RecoveredRevealFixture.member(true, firstClaim, proof = null))
        raw = RecoveredRevealFixture.edit(raw, metadataPath) { item ->
            val fields = item.jsonObject.toMutableMap()
            val operation = fields.getValue("operations").jsonArray.single().jsonObject.toMutableMap()
            when (mode) {
                "wrong-kind" -> operation["kind"] = JsonPrimitive("create")
                "wrong-claim" -> operation["requestId"] = JsonPrimitive(secondClaim)
                "wrong-secret" -> operation["secretId"] = JsonPrimitive("pass-a")
                "wrong-requester" -> operation["requesterId"] = JsonPrimitive("device-b")
                "target" -> operation["targetDeviceId"] = JsonPrimitive("device-b")
                "pending", "accepted", "unknown" -> operation["clientStatus"] = JsonPrimitive(mode)
                "unverified" -> operation["resolutionSource"] = JsonPrimitive("unknown")
                "canonical" -> operation["resolutionSource"] = JsonPrimitive("canonical")
                "contradictory" -> operation["outcome"] = JsonPrimitive("declined")
                "permission" -> operation["canApprove"] = JsonPrimitive(true)
                "decline-permission" -> operation["canDecline"] = JsonPrimitive(true)
                "not-waiting" -> operation["readiness"] = JsonPrimitive("ready")
            }
            fields["operations"] = JsonArray(when (mode) {
                "empty" -> emptyList()
                "duplicate" -> listOf(JsonObject(operation), JsonObject(operation))
                else -> listOf(JsonObject(operation))
            })
            fields["secrets"] = JsonArray(fields.getValue("secrets").jsonArray.map { element ->
                if (element.jsonObject["secretId"] != JsonPrimitive("pass-b")) element else {
                    val secret = element.jsonObject.toMutableMap()
                    when (mode) {
                        "can-delete" -> secret["canDelete"] = JsonPrimitive(true)
                        "deleting" -> secret["deletionRequestId"] = JsonPrimitive("40bb5e25-598a-4d9b-8cb7-2f942596090a")
                        "active-claim" -> secret["recoveryClaimId"] = JsonPrimitive(secondClaim)
                        "not-ready" -> secret["readiness"] = JsonPrimitive("waiting")
                    }
                    JsonObject(secret)
                }
            })
            JsonObject(fields)
        }
        if (mode in listOf("changed-name", "changed-type", "changed-words")) raw = RecoveredRevealFixture.edit(raw,
            listOf("message", "state", "vault", "member", "member", "vault", "secrets")) { secrets ->
            JsonArray(secrets.jsonArray.map { item ->
                if (item.jsonObject["id"] != JsonPrimitive("pass-b")) item else JsonObject(item.jsonObject + when (mode) {
                    "changed-name" -> "name" to JsonPrimitive("Changed frozen label")
                    "changed-type" -> "type" to JsonPrimitive("password")
                    else -> "wordCount" to JsonPrimitive(24)
                })
            })
        }
        return raw.toString().let { value -> when (mode) {
            "foreign-vault" -> value.replace("parser-vault", "foreign-vault")
            "foreign-device" -> value.replace("device-a", "device-z")
            else -> value
        } }
    }

    fun selected() = session.actions.presentation().filter { it.target == LiveboardActionTarget.Recover("pass-b") }
    suspend fun assertRetained(original: Pair<LiveboardRecoverIntent, NativeLiveboardOutcome<LiveboardCheckedReceipt>>) {
        val calls = f.core.calls.value; val reads = f.core.reads.value; val auth = f.auth.calls
        assertEquals(1, selected().size)
        assertNull(session.actions.recoverIntent("pass-b"))
        assertSame(original.second, session.actions.recover(original.first))
        assertEquals(calls, f.core.calls.value); assertEquals(reads, f.core.reads.value); assertEquals(auth, f.auth.calls)
    }
    suspend fun close() { if (::session.isInitialized) { session.awaitSettled(); session.dispose() }; f.close() }

    companion object {
        const val firstClaim = "next-recover-independent-original-claim"
        const val secondClaim = "next-recover-independent-later-claim"
        private val metadataPath = listOf("message", "state", "vault", "member", "liveboardState")
    }
}
