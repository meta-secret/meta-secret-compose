package models.appInternalModels

data class DeviceCellModel(
    val id: String,
    val status: DeviceStatus,
    val secretsCount: Int,
    val devicesCount: Int,
    val vaultName: String
)

enum class DeviceStatus(val value: String) {
    Member("Member"),
    Pending("Pending"),
    Unknown("Unknown"),
}