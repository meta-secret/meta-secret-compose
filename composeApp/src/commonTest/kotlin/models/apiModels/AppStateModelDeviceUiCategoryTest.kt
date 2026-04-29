package models.apiModels

import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateModelDeviceUiCategoryTest {

    @Test
    fun `getVaultSummary keeps device ui category from state json`() {
        val state = buildStateModel(
            currentUiCategory = DeviceUiCategory.Android,
            webUiCategory = DeviceUiCategory.Web
        )

        val summary = requireNotNull(state.getVaultSummary())
        assertEquals(DeviceUiCategory.Android, summary.users["current"]?.deviceUiCategory)
        assertEquals(DeviceUiCategory.Web, summary.users["web1"]?.deviceUiCategory)
    }

    @Test
    fun `getVaultSummary derives device ui category from deviceType when uiCategory is absent`() {
        val state = buildStateModel(
            currentUiCategory = null,
            webUiCategory = null
        )

        val summary = requireNotNull(state.getVaultSummary())
        assertEquals(DeviceUiCategory.Android, summary.users["current"]?.deviceUiCategory)
        assertEquals(DeviceUiCategory.Web, summary.users["web1"]?.deviceUiCategory)
    }

    private fun buildStateModel(
        currentUiCategory: DeviceUiCategory?,
        webUiCategory: DeviceUiCategory?
    ): AppStateModel {
        fun deviceData(id: String, name: String, type: String, uiCategory: DeviceUiCategory?) = DeviceData(
            deviceId = id,
            deviceName = name,
            deviceType = type,
            uiCategory = uiCategory,
            keys = OpenBox(dsaPk = "d", transportPk = "t")
        )

        val currentUserData = UserData(
            device = deviceData("current", "Google sdk_gphone16k_arm64", "Android", currentUiCategory),
            vaultName = "v"
        )
        val webUserData = UserData(
            device = deviceData("web1", "Chrome on MacIntel", "Web", webUiCategory),
            vaultName = "v"
        )

        val users = mapOf(
            "current" to UserMembership(member = UserDataMember(currentUserData)),
            "web1" to UserMembership(member = UserDataMember(webUserData))
        )

        val vaultInfo = VaultFullInfo.Member(
            UserMemberFullInfo(
                member = VaultMember(
                    member = UserDataMember(currentUserData),
                    vault = VaultData(vaultName = "v", users = users, secrets = emptyList())
                )
            )
        )

        return AppStateModel(
            message = Message(state = State.Vault(vault = vaultInfo)),
            success = true
        )
    }
}
