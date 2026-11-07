package models.apiModels.liveboard

import core.metaSecretCore.liveboard.LiveboardNativeFixtures
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import models.apiModels.AppStateModel
import models.apiModels.DeviceUiCategory
import models.apiModels.JsonConfig
import models.apiModels.UserMemberFullInfo
import models.apiModels.VaultFullInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveboardMemberSnapshotTest {
    @Test
    fun testLegacyMemberWithoutMetadataStillDecodes() {
        val state = AppStateModel.fromJson(LiveboardNativeFixtures.member(liveboardState = null))

        assertTrue(state.success)
        val member = assertIs<VaultFullInfo.Member>(state.getVaultFullInfo()).member
        assertEquals(LiveboardNativeFixtures.currentDeviceId, state.getCurrentDeviceId())
        assertEquals(LiveboardNativeFixtures.vaultName, state.getCurrentVaultName())
        assertEquals(listOf("pass-a", "pass-b"), member.member.vault.secrets.map { it.id })
        assertEquals(listOf("Same name", "Same name"), member.member.vault.secrets.map { it.name })
    }

    @Test
    fun testMemberRoundTripRetainsLiveboardMetadataFromSameSnapshot() {
        val state = AppStateModel.fromJson(LiveboardNativeFixtures.member())
        assertTrue(state.success)
        val member = assertIs<VaultFullInfo.Member>(state.getVaultFullInfo()).member
        assertEquals(LiveboardNativeFixtures.currentDeviceId, state.getCurrentDeviceId())
        assertEquals(listOf("pass-a", "pass-b"), member.member.vault.secrets.map { it.id })

        val actualMember = JsonConfig.json.encodeToJsonElement(UserMemberFullInfo.serializer(), member)
        val expected = JsonConfig.json.parseToJsonElement(LiveboardNativeFixtures.metadata)

        assertEquals(expected, actualMember.jsonObject["liveboardState"], "The same native Member must retain Liveboard metadata")
    }

    @Test
    fun testProjectionJoinsNamesPermissionsAndExactClaimFromOneMember() {
        val state = AppStateModel.fromJson(LiveboardNativeFixtures.member())
        val snapshot = assertNotNull(LiveboardStateDecoder.decode(state))

        assertEquals(LiveboardNativeFixtures.vaultName, snapshot.identity.vaultName)
        assertEquals(LiveboardNativeFixtures.currentDeviceId, snapshot.identity.currentDeviceId)
        assertEquals("copies", snapshot.permissions.storageMode)
        assertTrue(snapshot.permissions.canCreateSecret)
        assertEquals(listOf("device-b"), snapshot.permissions.removableDeviceIds)
        assertEquals(listOf("pass-a", "pass-b"), snapshot.secrets.map { it.identity.passId })
        assertEquals(listOf("Same name", "Same name"), snapshot.secrets.map { it.identity.name })
        assertEquals("showLocal", snapshot.secrets[0].primary.action)
        assertEquals("showRecovered", snapshot.secrets[1].primary.action)
        assertEquals(LiveboardNativeFixtures.claimId, snapshot.secrets[1].primary.claimId)
        assertFalse(snapshot.secrets[1].canDelete)
        assertEquals("seedPhrase", snapshot.secrets[1].identity.type)
        assertEquals(12, snapshot.secrets[1].identity.wordCount)
        assertEquals("Current phone", snapshot.devices.single { it.current }.name)
        assertEquals(DeviceUiCategory.Iphone, snapshot.devices.single { it.current }.category)
        assertTrue(snapshot.devices.single { !it.current }.canRemove)
        assertEquals("device-b", snapshot.operations.single().status.responderId)
        assertEquals("verified", snapshot.operations.single().status.resolutionSource)
        assertEquals(emptyList(), snapshot.operations.single().permissions.eligiblePeerIds)
        assertFalse(snapshot.operations.single().permissions.canApprove)
    }

    @Test
    fun testMissingNullOrFailedMetadataNeverUsesLegacyCountsAsPermission() {
        assertNotNull(LiveboardStateDecoder.decode(LiveboardNativeFixtures.member()))
        for (metadata in listOf(null, "null", "[]", "{}")) {
            val state = AppStateModel.fromJson(LiveboardNativeFixtures.member(metadata))
            assertTrue(state.success)
            assertIs<VaultFullInfo.Member>(state.getVaultFullInfo())
            assertNull(LiveboardStateDecoder.decode(state))
        }
        assertNull(LiveboardStateDecoder.decode(LiveboardNativeFixtures.member().replace("\"success\":true", "\"success\":false")))
    }

    @Test
    fun testUnknownSchemaRequiredEnumsAndWrongScalarTypesAreUnavailable() {
        val changes = listOf(
            "\"schemaVersion\":1" to "\"schemaVersion\":2",
            "\"storageMode\":\"copies\"" to "\"storageMode\":\"future\"",
            "\"primaryAction\":\"showLocal\"" to "\"primaryAction\":\"future\"",
            "\"clientStatus\":\"accepted\"" to "\"clientStatus\":\"future\"",
            "\"resolutionSource\":\"verified\"" to "\"resolutionSource\":\"future\"",
            "\"kind\":\"recover\"" to "\"kind\":\"future\"",
            "\"readiness\":\"ready\"" to "\"readiness\":\"future\"",
            "\"canCreateSecret\":true" to "\"canCreateSecret\":\"true\"",
            "\"recoveryClaimId\":\"${LiveboardNativeFixtures.claimId}\"" to "\"recoveryClaimId\":null",
        )
        for ((from, to) in changes) assertMetadataUnavailable(LiveboardNativeFixtures.metadata.replace(from, to))
    }

    @Test
    fun testMismatchedIdentityMissingSecretAndMissingExactClaimAreUnavailable() {
        val changes = listOf(
            "\"vaultName\":\"parser-vault\"" to "\"vaultName\":\"other-vault\"",
            "\"currentDeviceId\":\"device-a\"" to "\"currentDeviceId\":\"absent-device\"",
            "\"secretId\":\"pass-b\"" to "\"secretId\":\"absent-pass\"",
            "\"recoveryClaimId\":\"${LiveboardNativeFixtures.claimId}\"," to "",
        )
        for ((from, to) in changes) assertMetadataUnavailable(LiveboardNativeFixtures.metadata.replace(from, to))
    }

    @Test
    fun testDuplicateSecretOperationAndPeerIdentitiesAreUnavailable() {
        val original = JsonConfig.json.parseToJsonElement(LiveboardNativeFixtures.metadata).jsonObject
        for (field in listOf("secrets", "operations", "removableDeviceIds")) {
            val values = original.getValue(field).jsonArray
            val duplicate = JsonObject(original + (field to JsonArray(values + values.first())))
            assertMetadataUnavailable(duplicate.toString())
        }
        val duplicatedEligible = LiveboardNativeFixtures.metadata.replace("\"eligiblePeerIds\":[]", "\"eligiblePeerIds\":[\"device-b\",\"device-b\"]")
        assertMetadataUnavailable(duplicatedEligible)
    }

    @Test
    fun testRawDuplicateAndEscapedEquivalentKeysDisableOnlyLiveboard() {
        val changed = listOf(
            LiveboardNativeFixtures.member().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            LiveboardNativeFixtures.member().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"\\u0073chemaVersion\":1"),
            LiveboardNativeFixtures.member().replace("\"success\":true", "\"success\":true,\"success\":true"),
            LiveboardNativeFixtures.member().replace("\"deviceName\":\"Current phone\"", "\"deviceName\":\"Current phone\",\"deviceName\":\"Current phone\""),
        )
        for (raw in changed) {
            assertNotNull(LiveboardStateDecoder.decode(LiveboardNativeFixtures.member()))
            val state = AppStateModel.fromJson(raw)
            assertTrue(state.success)
            assertIs<VaultFullInfo.Member>(state.getVaultFullInfo())
            assertEquals(LiveboardNativeFixtures.currentDeviceId, state.getCurrentDeviceId())
            assertNull(LiveboardStateDecoder.decode(state))
            assertNull(LiveboardStateDecoder.decode(raw))
        }
    }

    private fun assertMetadataUnavailable(metadata: String) {
        assertNotNull(LiveboardStateDecoder.decode(LiveboardNativeFixtures.member()))
        assertFalse(metadata == LiveboardNativeFixtures.metadata, "Fixture mutation must change the actual input")
        val raw = LiveboardNativeFixtures.member(metadata)
        assertTrue(AppStateModel.fromJson(raw).success)
        assertNull(LiveboardStateDecoder.decode(raw))
    }

    @Test
    fun testQuotedNativeSuccessCannotEnableLiveboardThroughLegacyCoercion() {
        val control = LiveboardNativeFixtures.member()
        assertNotNull(LiveboardStateDecoder.decode(AppStateModel.fromJson(control)))
        val raw = control.replace("\"success\":true", "\"success\":\"true\"")
        assertFalse(raw == control)
        val enabled = mutableListOf<String>()

        assertRawLegacyMemberBlocked("quotedSuccess", raw, enabled)

        assertEquals(emptyList(), enabled, "A quoted envelope Boolean must not enable the new projection")
    }

    @Test
    fun testNumericLegacyJoinIdentitiesCannotMatchStringMetadataAfterCoercion() {
        val device = LiveboardNativeFixtures.member().replace("\"device-a\"", "\"101\"")
        val vault = LiveboardNativeFixtures.member().replace("\"parser-vault\"", "\"202\"")
        val secret = LiveboardNativeFixtures.member().replace("\"pass-a\"", "\"303\"")
        val variants = listOf(
            Triple("numericDeviceId", device, mutateLegacyScalar(device, "deviceId", "101", JsonPrimitive(101))),
            Triple("numericVaultName", vault, mutateLegacyScalar(vault, "vaultName", "202", JsonPrimitive(202))),
            Triple("numericSecretId", secret, mutateLegacyScalar(secret, "id", "303", JsonPrimitive(303))),
        )
        val enabled = mutableListOf<String>()

        for ((variant, control, raw) in variants) {
            assertNotNull(LiveboardStateDecoder.decode(AppStateModel.fromJson(control)), variant)
            assertFalse(raw == control, variant)
            assertRawLegacyMemberBlocked(variant, raw, enabled)
        }

        assertEquals(emptyList(), enabled, "Matching string metadata must not hide numeric legacy identities")
    }

    @Test
    fun testLegacyLabelsAndOptionalSecretScalarsCannotCoerceForLiveboard() {
        val control = LiveboardNativeFixtures.member()
        val variants = listOf(
            "numericDeviceName" to mutateLegacyScalar(control, "deviceName", "Current phone", JsonPrimitive(101)),
            "numericSecretName" to mutateLegacyScalar(control, "name", "Same name", JsonPrimitive(202)),
            "numericSecretType" to mutateLegacyScalar(control, "type", "seedPhrase", JsonPrimitive(303)),
            "quotedWordCount" to mutateLegacyScalar(control, "wordCount", "12", JsonPrimitive("12")),
            "bareDeviceName" to control.replace("\"deviceName\":\"Current phone\"", "\"deviceName\":BareLabel"),
        )
        val enabled = mutableListOf<String>()

        for ((variant, raw) in variants) {
            assertNotNull(LiveboardStateDecoder.decode(AppStateModel.fromJson(control)), variant)
            assertFalse(raw == control, variant)
            assertRawLegacyMemberBlocked(variant, raw, enabled)
        }

        assertEquals(emptyList(), enabled, "Legacy label/scalar coercion must not enter the new projection")
    }

    private fun assertRawLegacyMemberBlocked(variant: String, raw: String, enabled: MutableList<String>) {
        val legacy = JsonConfig.json.decodeFromString(AppStateModel.serializer(), raw)
        assertTrue(legacy.success, "$variant must reach the permissive legacy Member path")
        val legacyMember = assertIs<VaultFullInfo.Member>(legacy.getVaultFullInfo()).member
        val actual = AppStateModel.fromJson(raw)
        assertEquals(legacy.success, actual.success, variant)
        val actualMember = assertIs<VaultFullInfo.Member>(actual.getVaultFullInfo()).member
        assertEquals(legacyMember.member, actualMember.member, "$variant must retain all legacy fields")
        val stateEnabled = LiveboardStateDecoder.decode(actual) != null
        val rawEnabled = LiveboardStateDecoder.decode(raw) != null
        println("RAW_SCALAR_CASE $variant legacyMember=true stateEnabled=$stateEnabled rawEnabled=$rawEnabled")
        if (stateEnabled) enabled += "$variant.state"
        if (rawEnabled) enabled += "$variant.raw"
    }

    private fun mutateLegacyScalar(raw: String, field: String, original: String, replacement: JsonElement): String {
        fun visit(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.mapValues { (key, child) ->
                when {
                    key == "liveboardState" -> child
                    key == field && child is JsonPrimitive && child.content == original -> replacement
                    else -> visit(child)
                }
            })
            is JsonArray -> JsonArray(value.map(::visit))
            else -> value
        }
        return visit(JsonConfig.json.parseToJsonElement(raw)).toString()
    }
}
