package scenes.secretsscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import sharedData.DeviceRepository
import sharedData.SecretRepository
import sharedData.WarningStateHolder
import storage.KeyValueStorage

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorage

) : ViewModel() {
    val sizeDevices = DeviceRepository(keyValueStorage).devices.size
    val sizeSecrets = SecretRepository(keyValueStorage).secrets.size

    val isWarningVisible: StateFlow<Boolean> = WarningStateHolder.isWarningVisible

    fun getNickName(): String? {
        return keyValueStorage.signInInfo?.username
    }

    fun addDevice(): Boolean {
        //TODO("Not yet implemented")
        return true
    }

    fun getSecretsCount(): Int {
        //TODO("Not yet implemented")
        return 1
    }
}
