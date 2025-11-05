package core

import java.util.Locale

class DatabasePathProviderAndroid(
    private val keyValueStorage: KeyValueStorageInterface,
    private val deviceInfoProvider: DeviceInfoProviderInterface
) : DatabasePathProviderInterface {
    
    override fun getDatabaseFileName(): String {
        val deviceId = keyValueStorage.cachedDeviceId 
            ?: deviceInfoProvider.getDeviceId()
        return "meta-secret-${deviceId.uppercase(Locale.ROOT)}.db"
    }
}

