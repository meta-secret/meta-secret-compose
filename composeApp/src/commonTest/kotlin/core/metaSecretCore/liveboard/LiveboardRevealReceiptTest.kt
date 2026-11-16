package core.metaSecretCore.liveboard

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import ui.liveboard.LiveboardRootTestFixture

class LiveboardRevealReceiptTest {
    private val wire = RecoveredRevealFixture

    @Test
    fun testMalformedPresentRecoveredReceiptCannotPublishValue() = runBlocking {
        val malformed = mutableListOf<JsonElement>(JsonNull, JsonPrimitive("receipt"))
        for (version in listOf(JsonPrimitive(0), JsonPrimitive(2), JsonPrimitive("1")))
            malformed.add(wire.field(wire.receipt(), "version", version))
        malformed.add(wire.field(wire.receipt(), "unknown", JsonPrimitive(1)))
        malformed.add(wire.field(wire.receipt(), "requesterId", JsonPrimitive("foreign-requester")))
        malformed.add(wire.field(wire.receipt(), "revealContext", JsonNull))
        malformed.add(JsonObject(wire.receipt().filterKeys { it != "recoveryClaimId" }))
        val accepted = mutableListOf<Int>()
        for ((index, proof) in malformed.withIndex()) {
            if (show(wire.response(proof)).first) accepted.add(index)
        }
        assertEquals(emptyList(), accepted, "R32_MALFORMED_RECEIPT_MUST_NOT_PUBLISH")
    }

    @Test
    fun testRawReceiptDuplicateAndInvalidUnicodeAreRejected() = runBlocking {
        val raw = wire.response()
        val bad = listOf(raw.replace("\"version\":1", "\"version\":1,\"\\u0076ersion\":1"),
            raw.replace("vault-source", "\\ud800"), raw.replace("recovery-source", "\\udfff"))
        val accepted = mutableListOf<Int>()
        for ((index, value) in bad.withIndex()) if (show(value).first) accepted.add(index)
        assertEquals(emptyList(), accepted, "R32_RAW_RECEIPT_AMBIGUITY_OR_INVALID_UNICODE")
    }

    @Test
    fun testMetadataBoundsDoNotBoundWholeLegacyPlaintextEnvelope() = runBlocking {
        val large = "synthetic-legacy-plaintext:" + "\uD83D\uDE42".repeat(5000)
        val result = show(wire.response(proof = null, plaintext = large))
        assertTrue(result.first)
        assertEquals(large, result.second)
        assertTrue(wire.response(plaintext = large).encodeToByteArray().size > 12288)
        val proved = show(wire.response(plaintext = large))
        assertTrue(proved.first, "A valid optional metadata object does not limit plaintext")
        assertEquals(large, proved.second)
    }

    @Test
    fun testCompactReceiptBoundHasExactPositiveAndNegativeControls() = runBlocking {
        val valid = wire.receiptSized(12288)
        assertEquals(12288, valid.second.toString().encodeToByteArray().size)
        assertTrue(show(wire.response(valid.second), valid.first).first, "R32_RECEIPT_12288_VALID")
        val invalid = wire.receiptSized(12289)
        assertEquals(12289, invalid.second.toString().encodeToByteArray().size)
        assertFalse(show(wire.response(invalid.second), invalid.first).first, "R32_RECEIPT_12289_REJECTED")
    }

    private suspend fun show(raw: String, id: String = "pass-b"): Pair<Boolean, String?> {
        val f = LiveboardRootTestFixture()
        var session: LiveboardSession? = null
        return try {
            // Legacy first Show has no required selected context; present malformed receipt still fails closed.
            if (id != "pass-b") f.core.state.value = wire.replacePass(f.core.state.value, id)
            f.core.recoveredFixture = RecoveredFixtureReply(FeatureIds.claim, raw, null)
            f.start()
            val selected = assertNotNull(f.factory.open(assertNotNull(f.reads.frames.value))).also { session = it }
            val beforeReads = f.core.reads.value
            val shown = selected.sensitive.show(id)
            assertEquals(1, f.auth.calls)
            assertEquals(listOf(NativeCall("recovered", listOf(FeatureIds.claim))), f.core.calls.value)
            assertEquals(beforeReads, f.core.reads.value, "Show must not manufacture another physical read")
            shown to selected.sensitive.copyValue(id)
        } finally { session?.dispose(); f.close() }
    }
}
