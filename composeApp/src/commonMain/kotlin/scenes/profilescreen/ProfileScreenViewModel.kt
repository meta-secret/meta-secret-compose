package scenes.profilescreen

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage

class ProfileScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun completeSignIn(state: Boolean) {
        keyValueStorage.isSignInCompleted = state
    }
}
