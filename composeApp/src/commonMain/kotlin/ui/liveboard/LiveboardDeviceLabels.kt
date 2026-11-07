package ui.liveboard

import models.apiModels.AppStateModel

internal fun deviceLabels(actual: AppStateModel): Map<String, String> {
    val vault = (actual.getVaultFullInfo() as? models.apiModels.VaultFullInfo.Member)?.member?.member?.vault ?:
        return emptyMap()
    return vault.users.values.mapNotNull { user ->
        val device = (user.member?.userData ?: user.outsider?.userData)?.device ?: return@mapNotNull null
        device.deviceId to device.deviceName
    }.toMap()
}

