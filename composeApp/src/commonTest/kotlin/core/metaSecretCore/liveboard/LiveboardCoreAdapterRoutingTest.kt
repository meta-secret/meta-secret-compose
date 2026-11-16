package core.metaSecretCore.liveboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LiveboardCoreAdapterRoutingTest {
    @Test
    fun testCreateAndRecoverUseOnlyTheirExactCheckedPorts() {
        val core = RecordingNativeCore()
        val adapter = LiveboardCoreAdapter(core)
        core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = "create"))
        assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(adapter.clusterDistributionChecked("Same name", "parser-value\nsecond line"))
        core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked())
        val recovered = assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(adapter.recoverChecked("pass-b")).value

        assertEquals(LiveboardNativeFixtures.claimId, recovered.identity.claimId)
        assertEquals(listOf(NativeCall("create", listOf("Same name", "parser-value\nsecond line")),
            NativeCall("recover", listOf("pass-b"))), core.calls)
    }

    @Test
    fun testJoinAndRemoveDecisionsPreserveExactUuidBooleanAndInputBytes() {
        for (accepted in listOf(true, false)) {
            val core = RecordingNativeCore()
            val adapter = LiveboardCoreAdapter(core)
            val json = """ { "accepted":$accepted, "requestId":"${LiveboardNativeFixtures.requestId}" } """
            core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = "join"))
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(adapter.decideJoinRequest(json))
            core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = "removeDevice"))
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(adapter.decideRemoveMember(json))
            assertEquals(listOf(NativeCall("join", listOf(json)), NativeCall("decideRemove", listOf(json))), core.calls)
        }
    }

    @Test
    fun testInvalidDecisionNeverEntersAnyFacadePort() {
        val valid = """{"requestId":"${LiveboardNativeFixtures.requestId}","accepted":true}"""
        for (raw in listOf(
            valid.replace("true", "\"true\""),
            valid.replace("\"accepted\":true", "\"accepted\":true,\"accepted\":false"),
            valid.replace("\"accepted\":true", "\"accepted\":true,\"\\u0061ccepted\":false"),
            valid.replace("\"accepted\":true", "\"accepted\":true,\"targetDeviceId\":\"device-b\""),
            valid.replace(LiveboardNativeFixtures.requestId, "not-a-uuid"),
        )) {
            val control = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = "join")) }
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(LiveboardCoreAdapter(control).decideJoinRequest(valid))
            val core = RecordingNativeCore()
            assertIs<NativeResult.Unavailable>(LiveboardCoreAdapter(core).decideJoinRequest(raw))
            assertTrue(core.calls.isEmpty())
        }
    }

    @Test
    fun testDeleteAndRemoveUseDistinctProducerPortsAndExactSubjects() {
        val core = RecordingNativeCore()
        val adapter = LiveboardCoreAdapter(core)
        core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.producer(kind = "deleteSecret"))
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(adapter.deleteSecretChecked("pass-b"))
        core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.producer())
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(adapter.removeMemberChecked("device-b"))

        assertEquals(listOf(NativeCall("delete", listOf("pass-b")), NativeCall("remove", listOf("device-b"))), core.calls)
    }

    @Test
    fun testReconcileObservesOnceWithoutSubmitContinueOrLegacyFallback() {
        val core = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.producer()) }
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue())
        val receipt = assertIs<NativeResult.Success<LiveboardProducerReceipt>>(
            LiveboardCoreAdapter(core).reconcileProducerReceipt(reference),
        ).value

        assertEquals(LiveboardPhase.Unknown, receipt.state.phase)
        assertEquals("unknown", receipt.state.canonicalState)
        assertEquals(listOf(NativeCall("reconcile", listOf(reference.toJson()))), core.calls)
    }

    @Test
    fun testExplicitContinueUsesSameRemoveReferenceOnce() {
        val decision = LiveboardProducerDecision.Attempted(LiveboardNativeFixtures.sourceValue("9007199254740994"), "unconfirmed")
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue(decision = decision))
        val core = RecordingNativeCore().apply {
            result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision()))
        }
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(LiveboardCoreAdapter(core).continueLocalRemoveChecked(reference))
        assertEquals(listOf(NativeCall("continue", listOf(reference.toJson()))), core.calls)
    }

    @Test
    fun testDeleteReferenceCannotRouteToLocalRemoveContinuation() {
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue(kind = LiveboardKind.DeleteSecret))
        val core = RecordingNativeCore()
        assertIs<NativeResult.Unavailable>(LiveboardCoreAdapter(core).continueLocalRemoveChecked(reference))
        assertTrue(core.calls.isEmpty())
    }

    @Test
    fun testRecoveredShowApproveAndDeclineUseChosenOpaqueClaimWithoutLookup() {
        val core = RecordingNativeCore().apply { result = "opaque-native-result" }
        val adapter = LiveboardCoreAdapter(core)
        val claim = "chosen-claim::same-pass::second"
        assertEquals("opaque-native-result", adapter.showRecovered(claim))
        assertEquals("opaque-native-result", adapter.acceptRecover(claim))
        assertEquals("opaque-native-result", adapter.declineRecover(claim))
        assertEquals(listOf(NativeCall("showRecovered", listOf(claim)), NativeCall("acceptRecover", listOf(claim)),
            NativeCall("declineRecover", listOf(claim))), core.calls)
    }

    @Test
    fun testEnteredMutationMalformedAndThrowAreUncertainWithoutAutomaticRetry() {
        for (throws in listOf(false, true)) {
            val core = RecordingNativeCore().apply {
                result = "PRIVATE-RAW-MARKER"
                if (throws) failure = IllegalStateException("PRIVATE-RAW-MARKER")
            }
            val result = LiveboardCoreAdapter(core).recoverChecked("pass-b")
            assertIs<NativeResult.Uncertain>(result)
            assertFalse(result.toString().contains("PRIVATE-RAW-MARKER"))
            assertEquals(listOf(NativeCall("recover", listOf("pass-b"))), core.calls)
        }
    }

    @Test
    fun testReadOnlyMalformedOrThrowIsUnavailableAndCreatesNoWrite() {
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue())
        for (throws in listOf(false, true)) {
            val core = RecordingNativeCore().apply {
                result = "malformed"
                if (throws) failure = IllegalStateException("private")
            }
            assertIs<NativeResult.Unavailable>(LiveboardCoreAdapter(core).reconcileProducerReceipt(reference))
            assertEquals(listOf(NativeCall("reconcile", listOf(reference.toJson()))), core.calls)
        }
    }

    @Test
    fun testWrongCheckedKindSubjectAndDecisionUuidAreUncertainAfterEntry() {
        val recover = LiveboardNativeFixtures.checked()
        for (changed in listOf(recover.replace("\"pass-b\"", "\"pass-a\""), LiveboardNativeFixtures.checked(kind = "create"))) {
            val control = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(recover) }
            assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(LiveboardCoreAdapter(control).recoverChecked("pass-b"))
            val core = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(changed) }
            assertIs<NativeResult.Uncertain>(LiveboardCoreAdapter(core).recoverChecked("pass-b"))
            assertEquals(listOf(NativeCall("recover", listOf("pass-b"))), core.calls)
        }
        val core = RecordingNativeCore().apply {
            result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked(kind = "join", request = "89369079-1f14-46db-9619-73cda68ded57"))
        }
        val input = """{"requestId":"${LiveboardNativeFixtures.requestId}","accepted":false}"""
        assertIs<NativeResult.Uncertain>(LiveboardCoreAdapter(core).decideJoinRequest(input))
        assertEquals(listOf(NativeCall("join", listOf(input))), core.calls)
    }

    @Test
    fun testObservationCannotChangeOriginalRSubjectKindOrKnownUuid() {
        val original = LiveboardNativeFixtures.producer()
        for (changed in listOf(
            original.replace("\"targetDeviceId\":\"device-b\"", "\"targetDeviceId\":\"device-c\""),
            original.replace(LiveboardNativeFixtures.requestId, "89369079-1f14-46db-9619-73cda68ded57"),
            original.replace(LiveboardNativeFixtures.source(), LiveboardNativeFixtures.source("9007199254740994")),
            original.replace("ab".repeat(32), "cd".repeat(32)),
            LiveboardNativeFixtures.producer(kind = "deleteSecret"),
        )) assertObservationUnavailable(original, changed)
    }

    @Test
    fun testObservationCannotDropOrChangeAlreadyAttemptedU() {
        val original = LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision())
        val decision = LiveboardProducerDecision.Attempted(LiveboardNativeFixtures.sourceValue("9007199254740994"), "unconfirmed")
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue(decision = decision))
        for (changed in listOf(
            LiveboardNativeFixtures.producer(),
            LiveboardNativeFixtures.producer(decision = """{"state":"notAttempted"}"""),
            LiveboardNativeFixtures.producer(decision = LiveboardNativeFixtures.attemptedDecision("9007199254740995")),
        )) assertObservationUnavailable(original, changed, reference)
    }

    @Test
    fun testInitiallyUnknownUuidMayBecomeActualKnownUuidWithoutChangingSource() {
        val reference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue(request = null))
        val core = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.producer()) }
        val result = assertIs<NativeResult.Success<LiveboardProducerReceipt>>(
            LiveboardCoreAdapter(core).reconcileProducerReceipt(reference),
        ).value
        assertEquals(LiveboardNativeFixtures.requestId, result.identity.requestId)
        assertEquals(listOf(NativeCall("reconcile", listOf(reference.toJson()))), core.calls)
    }

    @Test
    fun testExplicitNativeFailureAndIndependentCallerAreNotPersistentAdapterLatch() {
        val core = RecordingNativeCore()
        val adapter = LiveboardCoreAdapter(core)
        assertEquals(NativeFailure.OperationUnavailable, assertIs<NativeResult.Unavailable>(adapter.recoverChecked("pass-b")).code)
        core.result = "malformed"
        assertIs<NativeResult.Uncertain>(adapter.recoverChecked("pass-b"))
        core.result = LiveboardNativeFixtures.envelope(LiveboardNativeFixtures.checked())
        assertIs<NativeResult.Success<LiveboardCheckedReceipt>>(adapter.recoverChecked("pass-b"))
        assertEquals(List(3) { NativeCall("recover", listOf("pass-b")) }, core.calls)
    }

    private fun assertObservationUnavailable(
        original: String,
        changed: String,
        reference: LiveboardProducerReference = LiveboardProducerReference.fromReceipt(LiveboardNativeFixtures.producerValue()),
    ) {
        assertFalse(original == changed, "Fixture mutation must change the actual input")
        val control = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(original) }
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(LiveboardCoreAdapter(control).reconcileProducerReceipt(reference))
        assertIs<NativeResult.Success<LiveboardProducerReceipt>>(LiveboardReceiptDecoder.decodeProducer(LiveboardNativeFixtures.envelope(changed)))
        val core = RecordingNativeCore().apply { result = LiveboardNativeFixtures.envelope(changed) }
        assertIs<NativeResult.Unavailable>(LiveboardCoreAdapter(core).reconcileProducerReceipt(reference))
        assertEquals(listOf(NativeCall("reconcile", listOf(reference.toJson()))), core.calls)
    }
}
