package scenes.profilescreen

import androidx.lifecycle.ViewModel
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
        //TODO("Not yet implemented")
        return 12
    }
    fun getDevicesCount(): Int {
        //TODO("Not yet implemented")
        return 2
    }
}


