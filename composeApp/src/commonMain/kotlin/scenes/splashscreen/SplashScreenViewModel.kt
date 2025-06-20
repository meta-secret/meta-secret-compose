package scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sharedData.BiometricAuthenticatorInterface
import storage.KeyValueStorage
import sharedData.BiometricState
import sharedData.KeyChainInterface

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val keyChainInterface: KeyChainInterface,
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

    fun onAppear() {
        viewModelScope.launch {
            keyChainInterface.clearAll()

            if (checkBiometricAvailability()) {
                authenticateWithBiometrics(
                    onSuccess = {
                        when {
                            isOnboardingComplete() -> {
                                viewModelScope.launch {
                                    if (checkAuth()) {
                                        _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                                    } else {
                                        _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                                    }
                                }
                            }
                            else -> {
                                _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
                            }
                        }
                        _biometricState.value = BiometricState.Success
                    }
                )
            } else {
                // TODO: Set pin code
                _biometricState.value = BiometricState.NeedRegistration
            }
        }
    }

    private fun checkBiometricAvailability(): Boolean {
        println("\uD83E\uDEC6 Biometric is available? ${biometricAuthenticator.isBiometricAvailable()}")
        return biometricAuthenticator.isBiometricAvailable()
    }

    private fun authenticateWithBiometrics(
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onFallback: (() -> Unit)? = null
    ) {
        biometricAuthenticator.authenticate(
            onSuccess = {
                println("\uD83E\uDEC6 Biometric is approved")
                onSuccess?.invoke()
            },
            onError = {
                println("\uD83E\uDEC6 Biometric is failed")
                onError?.invoke(it)
            },
            onFallback = {
                println("\uD83E\uDEC6 Biometric is prohibited")
                onFallback?.invoke()
            }
        )
    }

    private fun isOnboardingComplete(): Boolean {
        return keyValueStorage.isOnboardingCompleted
    }

    private suspend fun checkAuth(): Boolean {
        val masterKey = keyChainInterface.getString("master_key")
        println("Master key is: $masterKey")
        return !masterKey.isNullOrEmpty()
    }
}

enum class SplashNavigationEvent {
    Idle,
    NavigateToMain,
    NavigateToSignUp,
    NavigateToOnboarding
}