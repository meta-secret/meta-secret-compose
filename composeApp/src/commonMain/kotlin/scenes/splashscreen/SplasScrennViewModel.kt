package scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import storage.KeyValueStorage
import storage.KeyValueStorageImpl

class SplashScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    fun onAppear() {
        viewModelScope.launch {
            delay(3000)

            when {
                isOnboardingComplete() -> {
                    if (checkAuth()) {
                        _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                    } else {
                        _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                    }
                }
                else -> {
                    _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
                }
            }
        }
    }

    private fun isOnboardingComplete(): Boolean {
        return keyValueStorage.isOnboardingCompleted
    }

    private fun checkAuth(): Boolean {
        return keyValueStorage.isSignInCompleted
    }
}

enum class SplashNavigationEvent {
    Idle,
    NavigateToMain,
    NavigateToSignUp,
    NavigateToOnboarding
}