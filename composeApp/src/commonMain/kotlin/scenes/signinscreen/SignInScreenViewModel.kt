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
    private val biometricAuthenticator: BiometricAuthenticator
) : ViewModel() {
    private val _signInStatus = MutableStateFlow(false)
    val signInStatus: StateFlow<Boolean> = _signInStatus

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

    private val _biometricAvailable = MutableStateFlow(false)
    private val biometricAvailable: StateFlow<Boolean> = _biometricAvailable

    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    fun completeSignIn(name: String) {
        val masterKey = metaSecretCoreInterface.generateMasterKey()
        println("masterKey: $masterKey")
        viewModelScope.launch {
            _signInStatus.value = true
        }
    }

    fun saveUser(inputText: String) {
        keyValueStorage.signInInfo = LoginInfo(username = inputText, password = "12345")
        keyValueStorage.isSignInCompleted = true
    }

    fun checkBiometricAvailability() {
        val isAvailable = biometricAuthenticator.isBiometricAvailable()
        viewModelScope.launch {
            _biometricAvailable.value = isAvailable
        }
    }

    fun openAppSettings() {
        biometricAuthenticator.openAppSettings()
    }

    fun isBiometricAvailable(): Boolean {
        return biometricAuthenticator.isBiometricAvailable()
    }

    fun authenticateWithBiometrics(
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onFallback: (() -> Unit)? = null
    ) {
        biometricAuthenticator.authenticate(
            onSuccess = {
                onBiometricSuccess()
                onSuccess?.invoke()
            },
            onError = {
                onBiometricError(it)
                onError?.invoke(it)
            },
            onFallback = {
                onFallback?.invoke()
            }
        )
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            _biometricState.value = BiometricState.Success
            if (keyValueStorage.signInInfo != null) {
                completeSignIn("")
                _signInStatus.value = true
            } else {
                _biometricState.value = BiometricState.NeedRegistration
            }
        }
    }

    fun onBiometricError(errorMessage: String) {
        viewModelScope.launch {
            _biometricState.value = BiometricState.Error(errorMessage)
        }
    }

    fun resetBiometricState() {
        viewModelScope.launch {
            _biometricState.value = BiometricState.Idle
        }
    }

    fun isBiometricEnabled(): Boolean {
        return keyValueStorage.isBiometricEnabled
    }

    fun setBiometricEnabled(enabled: Boolean) {
        keyValueStorage.isBiometricEnabled = enabled
    }
}

sealed class BiometricState {
    object Idle : BiometricState()
    object Success : BiometricState()
    object NeedRegistration : BiometricState()
    data class Error(val message: String) : BiometricState()
}