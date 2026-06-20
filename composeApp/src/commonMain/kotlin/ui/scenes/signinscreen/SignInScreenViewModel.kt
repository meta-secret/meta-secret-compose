package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import core.AppleEmailAuthResult
import core.AppleEmailRequesterInterface
import core.KeyChainInterface
import core.LogTag
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.GoogleEmailAuthResult
import core.GoogleEmailRequesterInterface
import kotlinx.coroutines.launch
import models.appInternalModels.EmailProvider
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class SignInScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val metaSecretAppManager: core.metaSecretCore.MetaSecretAppManagerInterface,
    private val metaSecretCore: core.metaSecretCore.MetaSecretCoreInterface,
    private val metaSecretStateResolver: core.metaSecretCore.MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: core.KeyValueStorageInterface,
    private val socketHandler: core.metaSecretCore.MetaSecretSocketHandlerInterface,
    private val biometricAuthenticator: core.BiometricAuthenticatorInterface,
    private val appleEmailRequester: AppleEmailRequesterInterface,
    private val googleEmailRequester: GoogleEmailRequesterInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    val providerOrder = listOf(
        EmailProvider.APPLE,
        EmailProvider.GOOGLE,
        EmailProvider.MANUAL
    )

    private val _navigationEvent = kotlinx.coroutines.flow.MutableStateFlow<SignInNavigationEvent?>(SignInNavigationEvent.Idle)
    val navigationEvent: kotlinx.coroutines.flow.StateFlow<SignInNavigationEvent?> = _navigationEvent

    private val _emailError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val emailError: kotlinx.coroutines.flow.StateFlow<String?> = _emailError

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SignInViewEvents) {
            when (event) {
                is SignInViewEvents.SelectEmailProvider -> {
                    when (event.provider) {
                        EmailProvider.GOOGLE -> {
                            viewModelScope.launch {
                                requestGoogleEmail()
                            }
                        }

                        EmailProvider.APPLE -> {
                            viewModelScope.launch {
                                requestAppleEmail()
                            }
                        }

                        EmailProvider.MANUAL -> Unit
                    }
                }

                SignInViewEvents.ClearAllData -> {
                    viewModelScope.launch {
                        clearAllData()
                    }
                }
            }
        }
    }

    private suspend fun clearAllData() {
        keyChainManager.clearAll(isCleanDB = true)
    }

    private suspend fun requestGoogleEmail() {
        logger.log(LogTag.SignInVM.Message.GoogleAuthStarted, success = true)
        when (val result = googleEmailRequester.requestGoogleEmail()) {
            is GoogleEmailAuthResult.Success -> {
                logger.log(
                    LogTag.SignInVM.Message.GoogleAuthSuccess,
                    result.email,
                    success = true
                )
                _navigationEvent.value = SignInNavigationEvent.EmailConfirmation(result.email, EmailProvider.GOOGLE)
            }

            GoogleEmailAuthResult.Cancelled -> {
                logger.log(LogTag.SignInVM.Message.GoogleAuthCancelled, success = false)
            }

            is GoogleEmailAuthResult.Error -> {
                logger.log(LogTag.SignInVM.Message.GoogleAuthFailed, success = false)
                _emailError.value = result.message
                _navigationEvent.value = SignInNavigationEvent.ManualSignInScreen
            }
        }
    }

    private suspend fun requestAppleEmail() {
        logger.log(LogTag.SignInVM.Message.AppleAuthStarted, success = true)
        when (val result = appleEmailRequester.requestAppleEmail()) {
            is AppleEmailAuthResult.Success -> {
                _emailError.value = null
                logger.log(
                    LogTag.SignInVM.Message.AppleAuthSuccess,
                    result.email,
                    success = true
                )
                _navigationEvent.value = SignInNavigationEvent.EmailConfirmation(result.email, EmailProvider.APPLE)
            }

            AppleEmailAuthResult.Cancelled -> {
                logger.log(LogTag.SignInVM.Message.AppleAuthCancelled, success = false)
                _navigationEvent.value = SignInNavigationEvent.ManualSignInScreen
            }

            is AppleEmailAuthResult.Error -> {
                logger.log(LogTag.SignInVM.Message.AppleAuthFailed, success = false)
                _emailError.value = result.message
                _navigationEvent.value = SignInNavigationEvent.ManualSignInScreen
            }
        }
    }

    fun consumeNavigationEvent() {
        _navigationEvent.value = SignInNavigationEvent.Idle
    }
}

sealed class SignInViewEvents : CommonViewModelEventsInterface {
    data class SelectEmailProvider(val provider: EmailProvider) : SignInViewEvents()
    data object ClearAllData : SignInViewEvents()
}

sealed class SignInNavigationEvent {
    data object Idle : SignInNavigationEvent()
    data object MainScreen : SignInNavigationEvent()
    data object ManualSignInScreen : SignInNavigationEvent()
    data class EmailConfirmation(val email: String, val provider: EmailProvider) : SignInNavigationEvent()
}
