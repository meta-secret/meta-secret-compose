package scenes.devicesscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import sharedData.DeviceRepository
import sharedData.WarningStateHolder
import storage.KeyValueStorage

class DevicesScreenViewModel(
    private val keyValueStorage: KeyValueStorage


) : ViewModel() {
    val data = DeviceRepository.devices.size
    val isWarningVisible: StateFlow<Boolean> = WarningStateHolder.isWarningVisible

    fun setVisibility() {
        WarningStateHolder.setVisibility(false)
    }

    fun getNickName(): String? {
        return keyValueStorage.signInInfo?.username
    }

    fun addDevice(): Boolean {
        //TODO("Not yet implemented")
        return true
    }

    fun getDevice(index: Int): DeviceRepository.Device {
        return DeviceRepository.devices[index]
    }

    fun getSecretsCount(): Int {
        //TODO("Not yet implemented")
        return 1
    }
}
