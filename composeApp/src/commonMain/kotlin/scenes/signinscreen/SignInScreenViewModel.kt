package scenes.signinscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sharedData.MetaSecretCoreInterface
import storage.KeyValueStorage
import storage.LoginInfo

class SignInScreenViewModel(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    val signInStatus: StateFlow<Boolean> =  MutableStateFlow(false)

    fun completeSignIn(name: String) {
        val masterKey = metaSecretCoreInterface.generateMasterKey()
        println("masterKey: $masterKey")
    }

    fun saveUser(inputText: String) {
        keyValueStorage.signInInfo = LoginInfo(username = inputText, password = "12345")
    }
}