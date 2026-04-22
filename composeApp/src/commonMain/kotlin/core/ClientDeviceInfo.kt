package core

data class ClientDeviceInfo(
    val deviceName: String,
    val deviceType: String,
)

interface ClientDeviceInfoProviderInterface {
    fun current(): ClientDeviceInfo
}
