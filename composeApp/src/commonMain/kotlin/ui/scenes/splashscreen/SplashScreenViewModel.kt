package ui.scenes.splashscreen

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import core.LogTag
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
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.biometric_description

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyChain: KeyChainInterface,
    private val backupCoordinatorInterface: BackupCoordinatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface
) : CommonViewModel() {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState

    init {
        logger.setLoggerVisibility()
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SplashViewEvents) {
            when (event) {
                SplashViewEvents.ON_APPEAR -> {
                    viewModelScope.launch {
//                        clearAll()
                    }
                    authenticateWithBiometrics()
                }
                SplashViewEvents.BIOMETRIC_SUCCEEDED -> biometricSucceeded()
            }
        }
    }

    private suspend fun clearAll() {
        logger.log(LogTag.SplashVM.Message.DataCleanupStart)
        val clearResult = keyChain.clearAll(isCleanDB = true)
        logger.log(LogTag.SplashVM.Message.DataCleanupCompleted, " $clearResult")
    }

    private fun authenticateWithBiometrics() {
        biometricAuthenticator.authenticate(
            onSuccess = {
                logger.log(LogTag.SplashVM.Message.BiometricApproved, success = true)
                _biometricState.value = BiometricState.Success
            },
            onError = { error ->
                logger.log(LogTag.SplashVM.Message.BiometricFailed, error, success = false)
                _biometricState.value = BiometricState.Error(error)
            },
            onFallback = {
                logger.log(LogTag.SplashVM.Message.BiometricProhibited, success = false)
                _biometricState.value = BiometricState.Error(Res.string.biometric_description.toString())
            }
        )
    }

    private fun biometricSucceeded() {
        viewModelScope.launch {
            proceedWithNavigation()
        }
    }

    private suspend fun proceedWithNavigation() {
        backupCoordinatorInterface.restoreIfNeeded()
        val hasBackupDb = backupCoordinatorInterface.hasDatabaseFile()
        logger.setBackupDbExists(hasBackupDb)

        when (isOnboardingComplete()) {
            OnboardingState.COMPLETED -> {
                when (checkAuth()) {
                    AuthState.COMPLETED -> {
                        try {
                            vaultStatsProvider.refresh()
                        } catch (_: Throwable) {}
                        logger.log(LogTag.SplashVM.Message.MoveToMain, success = true)
                        _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                    }
                    AuthState.NOT_YET_COMPLETED -> {
                        logger.log(LogTag.SplashVM.Message.MoveToSignUp, success = true)
                        _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                    }
                }
            }
            OnboardingState.NOT_YET_COMPLETED -> {
                logger.log(LogTag.SplashVM.Message.MoveToOnboarding, success = true)
                _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
            }
        }
        logger.log(LogTag.SplashVM.Message.BiometricStateSuccess, success = true)
    }

    private fun isOnboardingComplete(): OnboardingState {
        return if (keyValueStorage.isOnboardingCompleted) { OnboardingState.COMPLETED } else { OnboardingState.NOT_YET_COMPLETED }
    }

    private suspend fun checkAuth(): AuthState {
        logger.log(LogTag.SplashVM.Message.AuthCheck)
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
    BIOMETRIC_SUCCEEDED
}

private enum class OnboardingState {
    COMPLETED,
    NOT_YET_COMPLETED
}

