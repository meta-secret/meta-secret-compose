package scenes.signinscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sharedData.BiometricAuthenticator
import sharedData.MetaSecretCoreInterface
import storage.KeyValueStorage
import storage.LoginInfo

class SignInScreenViewModel(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyValueStorage: KeyValueStorage,
) : ViewModel() {

    // Properties
    private val _signInStatus = MutableStateFlow(false)
    val signInStatus: StateFlow<Boolean> = _signInStatus

    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    fun completeSignIn(name: String) {
        viewModelScope.launch {
            _signInStatus.value = true
        }
    }
}