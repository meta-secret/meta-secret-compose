package scenes.devicesscreen

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage

class DevicesScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun getNickName():String? {
        return keyValueStorage.signInInfo?.username
    }
    fun addDevice(): Boolean {
        //TODO("Not yet implemented")
         return true
    }
//    fun getDevicesCount(): Int {
//        //TODO("Not yet implemented")
//        return 2
//    }
        fun getSecretsCount(): Int {
        //TODO("Not yet implemented")
        return 1
    }
}
