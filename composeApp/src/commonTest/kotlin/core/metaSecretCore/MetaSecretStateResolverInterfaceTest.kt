package core.metaSecretCore

import kotlin.test.Test
import kotlin.test.assertEquals
import models.apiModels.DeviceData
import models.apiModels.OpenBox
import models.apiModels.UserData
import models.apiModels.UserDataOutsider
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo

class MetaSecretStateResolverInterfaceTest {

    @Test
    fun `mapVaultAvailability returns AVAILABLE for NotExists`() {
        val result = mapVaultAvailability(VaultFullInfo.NotExists(userData("vault-a")))
        assertEquals(VaultAvailability.AVAILABLE, result)
    }

    @Test
    fun `mapVaultAvailability returns EXISTS for Outsider`() {
        val outsider = VaultFullInfo.Outsider(
            outsider = UserDataOutsider(
                userData = userData("vault-b"),
                status = UserDataOutsiderStatus.NON_MEMBER
            )
        )
        val result = mapVaultAvailability(outsider)
        assertEquals(VaultAvailability.EXISTS, result)
    }

    @Test
    fun `mapVaultAvailability returns AVAILABLE for Member`() {
        val member = VaultFullInfo.Member(
            member = models.apiModels.UserMemberFullInfo(
                member = models.apiModels.VaultMember(
                    member = models.apiModels.UserDataMember(userData("vault-c")),
                    vault = models.apiModels.VaultData(vaultName = "vault-c")
                )
            )
        )
        val result = mapVaultAvailability(member)
        assertEquals(VaultAvailability.AVAILABLE, result)
    }

    @Test
    fun `mapVaultAvailability returns null for null input`() {
        val result = mapVaultAvailability(null)
        assertEquals(null, result)
    }

    private fun userData(vaultName: String): UserData {
        return UserData(
            device = DeviceData(
                deviceId = "device-id",
                deviceName = "device-name",
                deviceType = "CLI",
                keys = OpenBox(dsaPk = "dsa", transportPk = "transport")
            ),
            vaultName = vaultName
        )
    }
}
