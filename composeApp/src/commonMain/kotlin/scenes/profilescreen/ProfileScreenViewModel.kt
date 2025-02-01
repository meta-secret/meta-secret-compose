package scenes.profilescreen

import androidx.lifecycle.ViewModel
import sharedData.Repository
import storage.KeyValueStorage

class ProfileScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun completeSignIn(state: Boolean) {
        keyValueStorage.isSignInCompleted = state
    }
    fun getNickName():String? {
        return keyValueStorage.signInInfo?.username
    }
    fun getSecretsCount(): Int {
        val result = Repository(keyValueStorage).secrets.size
        return result
    }
    fun getDevicesCount(): Int {
        val result = Repository(keyValueStorage).devices.size
        return result
    }
}


