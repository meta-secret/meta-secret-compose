package models.appInternalModels

import models.apiModels.DeviceUiCategory

data class DeviceCellModel(
    val id: String,
    val status: DeviceStatus,
    val secretsCount: Int,
    val devicesCount: Int,
    val vaultName: String,
    val deviceName: String = "",
    val deviceType: String = "Other",
    val deviceUiCategory: DeviceUiCategory? = null,
)

enum class DeviceStatus(val value: String) {
    Current("Current"),
    Member("Member"),
    Pending("Pending"),
    Declined("Declined"),
}
