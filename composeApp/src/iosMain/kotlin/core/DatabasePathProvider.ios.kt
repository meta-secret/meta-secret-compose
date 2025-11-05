package core

class DatabasePathProviderIos(
    private val keyValueStorage: KeyValueStorageInterface,
    private val deviceInfoProvider: DeviceInfoProviderInterface
) : DatabasePathProviderInterface {
    
    override fun getDatabaseFileName(): String {
        val deviceId = keyValueStorage.cachedDeviceId 
            ?: deviceInfoProvider.getDeviceId()
        return "meta-secret-${deviceId.uppercase()}.db"
    }
}

