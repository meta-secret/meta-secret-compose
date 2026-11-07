package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.serialization.json.*
import models.apiModels.liveboard.LiveboardStateDecoder

class LiveboardRevealMetadataTest {
    private val f = RecoveredRevealFixture

    @Test
    fun testValidAcceptedAndSemanticDoneKeepExistingProjectionUsable() {
        for (done in listOf(false, true)) {
            val decoded = assertNotNull(LiveboardStateDecoder.decode(f.member(done)), "R32_RAW_CONTEXT_VALID/$done")
            assertEquals(3, decoded.devices.size)
            assertEquals(if (done) "recover" else "showRecovered", decoded.secrets.single { it.identity.passId == "pass-b" }.primary.action)
            assertEquals(if (done) "done" else "accepted", decoded.operations.single().status.clientStatus)
            assertFalse(decoded.toString().contains("ab".repeat(32)), "Receipt/context representations must be redacted")
        }
    }

    @Test
    fun testAbsentLegacyContextRemainsUsableButNullAndUnknownProofDoNot() {
        assertNotNull(LiveboardStateDecoder.decode(f.member(proof = null)))
        assertNull(LiveboardStateDecoder.decode(f.member(proof = JsonNull)))
        assertNull(LiveboardStateDecoder.decode(f.member(proof = f.field(f.context(), "unknown", JsonPrimitive(true)))))
    }

    @Test
    fun testMalformedNestedProofsNeverProduceLiveboardActions() {
        val bad = mutableListOf<JsonElement>(JsonPrimitive("proof"), JsonArray(emptyList()))
        for (version in listOf(JsonPrimitive(0), JsonPrimitive(2), JsonPrimitive("1"), JsonPrimitive(1.0)))
            bad.add(f.field(f.context(), "version", version))
        for (path in listOf(listOf("vaultSource", "eventId"), listOf("vaultSource", "eventId", "fqdn"),
            listOf("vaultSource", "eventId", "id"), listOf("localDistributionSource"))) {
            bad.add(f.edit(f.context(), path) { f.field(it, "unknown", JsonPrimitive(1)) })
        }
        for (kind in listOf("DeviceLog", "vault", "SsRecovery"))
            bad.add(f.field(f.context(), "vaultSource", f.source(kind, "vault-source")))
        for (commitment in listOf("AB".repeat(32), "a".repeat(63), "g".repeat(64)))
            bad.add(f.field(f.context(), "claimScopeCommitment", JsonPrimitive(commitment)))
        bad.forEachIndexed { index, proof -> assertNull(LiveboardStateDecoder.decode(f.member(proof = proof)), "invalid proof/$index") }
    }

    @Test
    fun testContextCannotAttachToForeignOperationOrSnapshot() {
        for ((field, value) in listOf("kind" to "join", "requestId" to "another-claim", "secretId" to "pass-a", "requesterId" to "device-b")) {
            val changed = f.edit(Json.parseToJsonElement(f.member()), listOf("message", "state", "vault", "member", "liveboardState", "operations")) {
                JsonArray(listOf(f.field(it.jsonArray.single(), field, JsonPrimitive(value))))
            }
            assertNull(LiveboardStateDecoder.decode(changed.toString()), "foreign operation/$field")
        }
        for (field in listOf("vaultName", "currentDeviceId"))
            assertNull(LiveboardStateDecoder.decode(f.member(proof = f.field(f.context(), field, JsonPrimitive("foreign")))))
    }

    @Test
    fun testUtf8OrderingAndExactSafeSequenceHavePositiveControls() {
        val earlier = f.source("SsRecovery", "\uE000", "9")
        val later = f.source("SsRecovery", "\uD800\uDC00", "9007199254740991")
        val sorted = f.field(f.context(), "recoverySources", JsonArray(listOf(earlier, later)))
        assertNotNull(LiveboardStateDecoder.decode(f.member(proof = sorted)), "R32_UTF8_SORTED_SAFE53_VALID")
        val sameIdOtherBody = f.field(earlier, "bodyCommitment", JsonPrimitive("34".repeat(32)))
        for (sources in listOf(emptyList(), listOf(later, earlier), listOf(earlier, earlier), listOf(earlier, sameIdOtherBody), listOf(earlier, later, later)))
            assertNull(LiveboardStateDecoder.decode(f.member(proof = f.field(f.context(), "recoverySources", JsonArray(sources)))))
        val numeric = listOf(f.source("SsRecovery", "same-instance", "2"), f.source("SsRecovery", "same-instance", "10"))
        assertNotNull(LiveboardStateDecoder.decode(f.member(proof = f.field(f.context(), "recoverySources", JsonArray(numeric)))))
        assertNull(LiveboardStateDecoder.decode(f.member(proof = f.field(f.context(), "recoverySources", JsonArray(numeric.reversed())))))
        val unsafe = f.field(f.context(), "localDistributionSource", f.source("SsDistribution", "local-source", "9007199254740992"))
        assertNull(LiveboardStateDecoder.decode(f.member(proof = unsafe)))
        for (token in listOf(JsonPrimitive("1"), JsonPrimitive(-1), JsonPrimitive(0), JsonPrimitive(1.0))) {
            val wrong = f.edit(f.context(), listOf("vaultSource", "eventId", "id")) { f.field(it, "curr", token) }
            assertNull(LiveboardStateDecoder.decode(f.member(proof = wrong)))
        }
        val wrongPrev = f.edit(f.context(), listOf("vaultSource", "eventId", "id")) { f.field(it, "prev", JsonPrimitive(1)) }
        assertNull(LiveboardStateDecoder.decode(f.member(proof = wrongPrev)))
    }

    @Test
    fun testStrictUtf8ScalarAndCompactContextByteBounds() {
        fun sourceId(id: String) = f.field(f.context(), "vaultSource", f.source("Vault", id))
        val valid = "\uD83D\uDE42".repeat(256)
        assertEquals(1024, valid.encodeToByteArray().size)
        assertNotNull(LiveboardStateDecoder.decode(f.member(proof = sourceId(valid))), "R32_ID_1024_UTF8_VALID")
        assertNull(LiveboardStateDecoder.decode(f.member(proof = sourceId(valid + "x"))))
        assertNull(LiveboardStateDecoder.decode(f.member(proof = sourceId(""))))
        assertNotNull(LiveboardStateDecoder.decode(f.member(proof = f.contextSized(8192))), "R32_CONTEXT_8192_VALID")
        assertNull(LiveboardStateDecoder.decode(f.member(proof = f.contextSized(8193))))
    }

    @Test
    fun testRawDuplicatesEscapedKeysAndInvalidUnicodeCannotBecomeProof() {
        val raw = f.member()
        val ambiguous = listOf(
            raw.replace("\"version\":1", "\"version\":1,\"version\":1"),
            raw.replace("\"version\":1", "\"version\":1,\"\\u0076ersion\":1"),
            raw.replace("\"curr\":1", "\"curr\":1,\"curr\":1"),
            raw.replace("vault-source", "\\ud800"),
            raw.replace("vault-source", "\\udfff"),
        )
        ambiguous.forEachIndexed { index, value -> assertNull(LiveboardStateDecoder.decode(value), "raw ambiguity/$index") }
    }
}
