package scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.StateType
import sharedData.BiometricAuthenticatorInterface
import storage.KeyValueStorage
import sharedData.BiometricState
import sharedData.KeyChainInterface
import sharedData.metaSecretCore.InitResult
import sharedData.metaSecretCore.MetaSecretAppManager
import sharedData.metaSecretCore.MetaSecretCoreInterface

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val metaSecretAppManager: MetaSecretAppManager,
    private val keyChain: KeyChainInterface
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

    fun onAppear() {
        viewModelScope.launch {
//            keyChain.clearAll()

            if (checkBiometricAvailability()) {
                authenticateWithBiometrics(
                    onSuccess = {
                        when {
                            isOnboardingComplete() -> {
                                viewModelScope.launch {
                                    if (checkAuth()) {
                                        println("\uD83D\uDC49 Move to Main")
                                        _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                                    } else {
                                        println("\uD83D\uDC49 Move to Sign up")
                                        _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                                    }
                                }
                            }
                            else -> {
                                println("\uD83D\uDC49 Move to Onboarding")
                                _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
                            }
                        }
                        println("✅BiometricState Success")
                        _biometricState.value = BiometricState.Success
                    }
                )
            } else {
                // TODO: #48 Set pin code
                println("✅BiometricState NeedRegistration")
                _biometricState.value = BiometricState.NeedRegistration
            }
        }
    }

    private fun checkBiometricAvailability(): Boolean {
        println("\uD83E\uDEC6 SplashVM: Biometric is available? ${biometricAuthenticator.isBiometricAvailable()}")
        return biometricAuthenticator.isBiometricAvailable()
    }

    private fun authenticateWithBiometrics(
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onFallback: (() -> Unit)? = null
    ) {
        biometricAuthenticator.authenticate(
            onSuccess = {
                println("\uD83E\uDEC6 SplashVM: Biometric is approved")
                onSuccess?.invoke()
            },
            onError = {
                println("\uD83E\uDEC6 SplashVM: Biometric is failed")
                onError?.invoke(it)
            },
            onFallback = {
                println("\uD83E\uDEC6 SplashVM: Biometric is prohibited")
                onFallback?.invoke()
            }
        )
    }

    private fun isOnboardingComplete(): Boolean {
        return keyValueStorage.isOnboardingCompleted
    }

    private suspend fun checkAuth(): Boolean {
        println("✅SplashVM: Auth check")
        return when (metaSecretAppManager.initWithSavedKey()) {
            is InitResult.Success -> {
                metaSecretAppManager.getState() == StateType.MEMBER
            }
            else -> false
        }
    }
}

enum class SplashNavigationEvent {
    Idle,
    NavigateToMain,
    NavigateToSignUp,
    NavigateToOnboarding
}