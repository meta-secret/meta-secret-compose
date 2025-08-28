package ui.scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.BiometricAuthenticatorInterface
import core.KeyValueStorage
import core.BiometricState
import core.KeyChainInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.BackupCoordinatorInterface

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyChain: KeyChainInterface,
    private val backupCoordinatorInterface: BackupCoordinatorInterface
) : ViewModel(), CommonViewModel {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

    // Public API for View
    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SplashViewEvents) {
            when (event) {
                SplashViewEvents.ON_APPEAR -> biometricRoutine()
                SplashViewEvents.BIOMETRIC_SUCCEEDED -> biometricSucceeded()
                SplashViewEvents.BIOMETRIC_NEEDS_REGISTRATION -> TODO()
            }
        }
    }

    // All biometric routine
    private fun biometricRoutine() {
        viewModelScope.launch {
            keyChain.clearAll(isCleanDB = true)

            if (checkBiometricAvailability()) {
                // Try restore DB before auth flow
                backupCoordinatorInterface.restoreIfNeeded()
                authenticateWithBiometrics()
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

    private fun authenticateWithBiometrics() {
        biometricAuthenticator.authenticate(
            onSuccess = {
                println("\uD83E\uDEC6 SplashVM: Biometric is approved")
                _biometricState.value = BiometricState.Success
            },
            onError = {
                println("\uD83E\uDEC6 SplashVM: Biometric is failed")
                _biometricState.value = BiometricState.Error("")
            },
            onFallback = {
                println("\uD83E\uDEC6 SplashVM: Biometric is prohibited")
                _biometricState.value = BiometricState.Error("")
            }
        )
    }

    private fun biometricSucceeded() {
        when (isOnboardingComplete()) {
            OnboardingState.COMPLETED -> {
                viewModelScope.launch {
                    when (checkAuth()) {
                        AuthState.COMPLETED -> {
                            println("\uD83D\uDC49 SplashVM: Move to Main")
                            _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                        }
                        AuthState.NOT_YET_COMPLETED -> {
                            println("\uD83D\uDC49 SplashVM: Move to Sign up")
                            _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                        }
                    }
                }
            }
            OnboardingState.NOT_YET_COMPLETED -> {
                println("\uD83D\uDC49 SplashVM: Move to Onboarding")
                _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
            }
        }
        println("✅BiometricState Success")
    }

    // Different statuses check
    private fun isOnboardingComplete(): OnboardingState {
        return if (keyValueStorage.isOnboardingCompleted) { OnboardingState.COMPLETED } else { OnboardingState.NOT_YET_COMPLETED }
    }

    private suspend fun checkAuth(): AuthState {
        println("✅SplashVM: Auth check")
        return metaSecretAppManager.checkAuth()
    }
}

enum class SplashNavigationEvent {
    Idle,
    NavigateToMain,
    NavigateToSignUp,
    NavigateToOnboarding
}

enum class SplashViewEvents: CommonViewModelEventsInterface {
    ON_APPEAR,
    BIOMETRIC_SUCCEEDED,
    BIOMETRIC_NEEDS_REGISTRATION
}

private enum class OnboardingState {
    COMPLETED,
    NOT_YET_COMPLETED
}

