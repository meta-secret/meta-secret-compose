package core

interface DeviceInfoProviderInterface {
    fun getAppVersion(): String
    fun getDeviceMake(): String
    fun getDeviceId(): String
}


