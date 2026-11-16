package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class LiveboardNativeReceiptTest {
    @Test
    fun testCheckedNativeObjectRetainsOpaqueClaimAndUnknownPhase() {
        val queued = checked(LiveboardNativeFixtures.checked())
        assertEquals(LiveboardKind.Recover, queued.kind)
        assertEquals("pass-b", queued.identity.secretId)
        assertEquals(LiveboardNativeFixtures.claimId, queued.identity.claimId)
        assertEquals(LiveboardPhase.Queued, queued.phase)
        assertEquals(LiveboardPhase.Unknown, checked(LiveboardNativeFixtures.checked(phase = "unknown")).phase)
    }

    @Test
    fun testAllCheckedKindsAcceptTheirActualRequiredIdentity() {
        val kinds = mapOf("create" to LiveboardKind.Create, "recover" to LiveboardKind.Recover,
            "join" to LiveboardKind.Join, "removeDevice" to LiveboardKind.RemoveDevice)
        for ((wire, kind) in kinds) assertEquals(kind, checked(LiveboardNativeFixtures.checked(kind = wire)).kind)
    }

    @Test
    fun testProducerVersionAndSeparateRequestDecisionSourcesAreRetained() {
        val message = LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision())
        val receipt = producer(message)
        assertEquals(1, receipt.version)
        assertEquals(LiveboardKind.RemoveDevice, receipt.identity.kind)
        assertEquals("device-b", receipt.identity.targetDeviceId)
        assertEquals("9007199254740993", receipt.requestStage.source.eventId.id.curr)
        assertEquals("9007199254740994", assertIs<LiveboardProducerDecision.Attempted>(receipt.decisionStage).source.eventId.id.curr)
        assertEquals(LiveboardPhase.Unknown, receipt.state.phase)
        assertEquals("unknown", receipt.state.canonicalState)
        assertEquals("synchronizing", receipt.state.readiness)
        assertEquals("notStarted", receipt.state.materialStage)
    }

    @Test
    fun testFixedNativeFailuresAreSafeAndNeverSuccess() {
        for (code in listOf(NativeFailure.NotInitialized, NativeFailure.OperationUnavailable,
            NativeFailure.InvalidDecisionInput, NativeFailure.InvalidProducerReference)) {
            val raw = """{"success":false,"errorCode":"${code.name}"}"""
            assertEquals(code, assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(raw)).code)
            assertEquals(code, assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(raw)).code)
        }
        val privateMarker = "PRIVATE-RAW-MARKER"
        for (raw in listOf("""{"success":false,"errorCode":"$privateMarker"}""", "not-json-$privateMarker")) {
            val checked = assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(raw))
            val producer = assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(raw))
            assertFalse(checked.toString().contains(privateMarker))
            assertFalse(producer.toString().contains(privateMarker))
        }
    }

    @Test
    fun testBareWebLegacyStringWrongEnvelopeAndCrossDecoderAreRejected() {
        val checked = LiveboardNativeFixtures.checked()
        val producer = LiveboardNativeFixtures.producer()
        for (raw in listOf(checked, """{"success":true,"message":"ok"}""", """{"success":"true","message":$checked}""",
            """{"success":true,"message":null}""", """{"success":true,"message":[]}""")) {
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(LiveboardReceiptDecoder.decodeChecked(LiveboardNativeFixtures.envelope(checked)))
            assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(raw))
        }
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(producer)))
        assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(LiveboardNativeFixtures.envelope(producer)))
        assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(checked)))
        assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(producer))
    }

    @Test
    fun testCheckedMissingIdentityUnknownPhaseAndExplicitNullAreRejected() {
        val valid = LiveboardNativeFixtures.checked()
        val changes = listOf(
            valid.replace("\"secretId\":\"pass-b\",", ""),
            valid.replace("\"claimId\":\"${LiveboardNativeFixtures.claimId}\"", "\"claimId\":null"),
            valid.replace("\"phase\":\"queued\"", "\"phase\":\"applied\""),
            valid.replace("\"kind\":\"recover\"", "\"kind\":\"future\""),
            LiveboardNativeFixtures.checked(kind = "join", request = "not-a-uuid"),
        )
        for (changed in changes) {
            checked(valid)
            assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(LiveboardNativeFixtures.envelope(changed)))
        }
    }

    @Test
    fun testDuplicateRawEnvelopeReceiptAndEscapedEquivalentKeysAreRejected() {
        val valid = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked())
        val changes = listOf(
            valid.replace("\"success\":true", "\"success\":true,\"success\":true"),
            valid.replace("\"success\":true", "\"success\":true,\"\\u0073uccess\":true"),
            valid.replace("\"kind\":\"recover\"", "\"kind\":\"recover\",\"kind\":\"recover\""),
            valid.replace("\"claimId\":", "\"claimId\":null,\"claimId\":"),
        )
        for (changed in changes) {
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(LiveboardReceiptDecoder.decodeChecked(valid))
            assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeChecked(changed))
        }
    }

    @Test
    fun testProducerFullU64AndNotAttemptedAreAcceptedWithoutRounding() {
        for (curr in listOf("9007199254740993", "18446744073709551615")) {
            val receipt = producer(LiveboardNativeFixtures.producer(curr = curr, request = null, decision = """{"state":"notAttempted"}"""))
            assertEquals(curr, receipt.requestStage.source.eventId.id.curr)
            assertEquals((curr.toULong() - 1u).toString(), receipt.requestStage.source.eventId.id.prev)
            assertIs<LiveboardProducerDecision.NotAttempted>(receipt.decisionStage)
            assertEquals(null, receipt.identity.requestId)
        }
    }

    @Test
    fun testProducerInvalidNumericTokensAndSourceGrammarAreRejected() {
        val valid = LiveboardNativeFixtures.producer()
        val changes = listOf("18446744073709551616", "-1", "1.5", "9.007199254740993e15", "\"9007199254740993\"")
            .map { valid.replace("\"curr\":9007199254740993", "\"curr\":$it") } + listOf(
            valid.replace("\"prev\":9007199254740992", "\"prev\":1"),
            valid.replace("ab".repeat(32), "AB".repeat(32)),
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"canonicalState\":\"unknown\"", "\"canonicalState\":\"future\""),
            valid.replace("\"curr\":9007199254740993", "\"curr\":9007199254740993,\"curr\":9007199254740993"),
            valid.replace("\"objType\":\"DeviceLog\"", "\"objType\":\"DeviceLog\",\"\\u006fbjType\":\"DeviceLog\""),
        )
        for (changed in changes) {
            producer(valid)
            assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(changed)))
        }
    }

    @Test
    fun testDeleteProducerOmitsDecisionAndRequiresCanonicalUuid() {
        val valid = LiveboardNativeFixtures.producer(kind = "deleteSecret")
        assertEquals(LiveboardKind.DeleteSecret, producer(valid).identity.kind)
        for (changed in listOf(
            LiveboardNativeFixtures.producer(kind = "deleteSecret", decision = "null"),
            LiveboardNativeFixtures.producer(kind = "deleteSecret", decision = """{"state":"notAttempted"}"""),
            LiveboardNativeFixtures.producer(kind = "deleteSecret", request = null),
            LiveboardNativeFixtures.producer(kind = "deleteSecret", request = "NOT-CANONICAL"),
            valid.replace("\"requestStage\":", "\"forbiddenCallerAuthority\":true,\"requestStage\":"),
        )) {
            producer(valid)
            assertIs<NativeResult.Unavailable>(LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(changed)))
        }
    }

    private fun checked(message: String) = assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(
        LiveboardReceiptDecoder.decodeChecked(LiveboardNativeFixtures.envelope(message)),
    ).value

    private fun producer(message: String) = assertIs<NativeResult.Success<LiveboardProducerReceipt>>(
        LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(message)),
    ).value
}
