package core.metaSecretCore.liveboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveboardProducerReferenceTest {
    @Test
    fun testDeleteReferenceContainsOnlyOriginalRAndKnownUuid() {
        val receipt = LiveboardNativeFixtures.producerValue(kind = LiveboardKind.DeleteSecret)
        val encoded = LiveboardProducerReference.fromReceipt(receipt).toJson()
        val value = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(setOf("version", "kind", "requestId", "requestStage"), value.keys)
        assertEquals(LiveboardNativeFixtures.requestId, value.getValue("requestId").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(LiveboardNativeFixtures.source()), value["requestStage"])
        assertFalse(encoded.contains("observation"))
        assertFalse(encoded.contains("readiness"))
        assertFalse(encoded.contains("secretId"))
        assertFalse(encoded.contains("phase"))
    }

    @Test
    fun testNotAttemptedRemoveOmitsUAndUnknownUuid() {
        val receipt = LiveboardNativeFixtures.producerValue(
            request = null,
            decision = LiveboardProducerDecision.NotAttempted,
        )
        val value = Json.parseToJsonElement(LiveboardProducerReference.fromReceipt(receipt).toJson()).jsonObject

        assertEquals(setOf("version", "kind", "requestStage"), value.keys)
        assertEquals("removeDevice", value.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun testAttemptedRemoveEmitsExactOriginalUWithoutObservation() {
        val source = LiveboardNativeFixtures.sourceValue("9007199254740994")
        val receipt = LiveboardNativeFixtures.producerValue(
            decision = LiveboardProducerDecision.Attempted(source, "rawAcknowledged"),
        )
        val encoded = LiveboardProducerReference.fromReceipt(receipt).toJson()
        val value = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(setOf("version", "kind", "requestId", "requestStage", "decisionStage"), value.keys)
        assertEquals(Json.parseToJsonElement(LiveboardNativeFixtures.source("9007199254740994")), value["decisionStage"])
        assertFalse(encoded.contains("rawAcknowledged"))
        assertFalse(encoded.contains("targetDeviceId"))
    }

    @Test
    fun testReferencePreservesFullU64DecimalTokensAsNumbers() {
        for (curr in listOf("9007199254740993", "18446744073709551615")) {
            val receipt = LiveboardNativeFixtures.producerValue(curr = curr)
            val encoded = LiveboardProducerReference.fromReceipt(receipt).toJson()
            val sequence = Json.parseToJsonElement(encoded).jsonObject.getValue("requestStage")
                .jsonObject.getValue("eventId").jsonObject.getValue("id").jsonObject

            assertEquals(curr, sequence.getValue("curr").jsonPrimitive.content)
            assertFalse(sequence.getValue("curr").jsonPrimitive.isString)
            assertEquals((curr.toULong() - 1u).toString(), sequence.getValue("prev").jsonPrimitive.content)
            assertFalse(sequence.getValue("prev").jsonPrimitive.isString)
            assertTrue(encoded.encodeToByteArray().size <= 4096)
        }
    }

    @Test
    fun testReferenceCapturedBeforeCallerCopiesCannotAdoptDifferentIdentity() {
        val original = LiveboardNativeFixtures.producerValue()
        val reference = LiveboardProducerReference.fromReceipt(original)
        val before = reference.toJson()
        val changed = original.copy(identity = original.identity.copy(requestId = "89369079-1f14-46db-9619-73cda68ded57"))

        assertEquals(before, reference.toJson())
        assertFalse(LiveboardProducerReference.fromReceipt(changed).toJson() == before)
    }
}
