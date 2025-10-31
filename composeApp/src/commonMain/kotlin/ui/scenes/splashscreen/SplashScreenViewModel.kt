package ui.scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import core.LogTags
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.BiometricAuthenticatorInterface
import core.BiometricState
import core.KeyChainInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.BackupCoordinatorInterface
import core.KeyValueStorageInterface
import core.ScreenMetricsProviderInterface
import core.VaultStatsProviderInterface

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyChain: KeyChainInterface,
    private val backupCoordinatorInterface: BackupCoordinatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface
) : ViewModel(), CommonViewModel {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

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
//            println("🔄${LogTags.SPLASH_VM}: Starting complete data cleanup")
//            val clearResult = keyChain.clearAll(isCleanDB = true)
//            println("🔄${LogTags.SPLASH_VM}: Data cleanup completed with result: $clearResult")

            if (checkBiometricAvailability()) {
                backupCoordinatorInterface.restoreIfNeeded()
                authenticateWithBiometrics()
            } else {
                // TODO: #48 Set pin code
                println("✅${LogTags.SPLASH_VM}: BiometricState NeedRegistration")
                _biometricState.value = BiometricState.NeedRegistration
            }
        }
    }

    private fun checkBiometricAvailability(): Boolean {
        println("✅${LogTags.SPLASH_VM}: Biometric is available? ${biometricAuthenticator.isBiometricAvailable()}")
        return biometricAuthenticator.isBiometricAvailable()
    }

    private fun authenticateWithBiometrics() {
        biometricAuthenticator.authenticate(
            onSuccess = {
                println("✅${LogTags.SPLASH_VM}: Biometric is approved")
                _biometricState.value = BiometricState.Success
            },
            onError = {
                println("❌${LogTags.SPLASH_VM}: Biometric is failed")
                _biometricState.value = BiometricState.Error("")
            },
            onFallback = {
                println("❌${LogTags.SPLASH_VM}: Biometric is prohibited")
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
                            // Preload vault stats before navigating to Main
                            try {
                                vaultStatsProvider.refresh()
                            } catch (_: Throwable) {}
                            println("✅${LogTags.SPLASH_VM}: Move to Main")
                            _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                        }
                        AuthState.NOT_YET_COMPLETED -> {
                            println("✅${LogTags.SPLASH_VM}: Move to Sign up")
                            _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                        }
                    }
                }
            }
            OnboardingState.NOT_YET_COMPLETED -> {
                println("✅${LogTags.SPLASH_VM}: Move to Onboarding")
                _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
            }
        }
        println("✅${LogTags.SPLASH_VM}: BiometricState Success")
    }

    // Different statuses check
    private fun isOnboardingComplete(): OnboardingState {
        return if (keyValueStorage.isOnboardingCompleted) { OnboardingState.COMPLETED } else { OnboardingState.NOT_YET_COMPLETED }
    }

    private suspend fun checkAuth(): AuthState {
        println("✅${LogTags.SPLASH_VM}: Auth check")
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

