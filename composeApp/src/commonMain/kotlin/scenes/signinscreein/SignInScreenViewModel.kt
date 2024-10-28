package scenes.signinscreein

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage

class SignInScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun isNameError(string: String): Boolean {
        return string == "Dima"
    }
}