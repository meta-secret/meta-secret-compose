package models.apiModels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientStatusSerializationTest {

    private fun claimObjectJson(clientStatusValue: String?): String {
        val clientStatusField = if (clientStatusValue != null) ",\"clientStatus\":\"$clientStatusValue\"" else ""
        return "{\"distClaimId\":{\"id\":\"claim1\",\"passId\":{\"id\":\"pass1\",\"name\":\"secret1\"}}," +
            "\"distributionType\":\"recover\",\"id\":\"claim1\",\"receivers\":[\"device2\"]," +
            "\"sender\":\"device1\",\"status\":{\"statuses\":{}},\"vaultName\":\"vault1\"$clientStatusField}"
    }

    @Test
    fun `parses all clientStatus wire values`() {
        val expected = mapOf(
            "pending" to ClientStatus.PENDING,
            "needApprove" to ClientStatus.NEED_APPROVE,
            "accepted" to ClientStatus.ACCEPTED,
            "declined" to ClientStatus.DECLINED,
            "done" to ClientStatus.DONE,
        )
        expected.forEach { (wireValue, expectedEnum) ->
            val claim = JsonConfig.json.decodeFromString<ClaimObject>(claimObjectJson(wireValue))
            assertEquals(expectedEnum, claim.clientStatus, "wire value '$wireValue'")
        }
    }

    @Test
    fun `clientStatus is null when absent e g for Split claims`() {
        val claim = JsonConfig.json.decodeFromString<ClaimObject>(claimObjectJson(null))
        assertNull(claim.clientStatus)
        assertEquals(DistributionType.RECOVER, claim.distributionType)
    }

    @Test
    fun `SearchClaimModel findClaim response exposes clientStatus via ClaimModel`() {
        val json = "{\"success\":true,\"message\":{\"claim\":${claimObjectJson("accepted")}}}"

        val result = SearchClaimModel.fromJson(json)

        assertEquals(ClientStatus.ACCEPTED, result.claim?.clientStatus)
        assertEquals("claim1", result.claim?.claimId)
    }
}
