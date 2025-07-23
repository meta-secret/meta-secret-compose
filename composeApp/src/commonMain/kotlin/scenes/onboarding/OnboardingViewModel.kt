package scenes.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import scenes.common.CommonViewModel
import scenes.common.CommonViewModelEventsInterface
import sharedData.metaSecretCore.AuthState
import sharedData.metaSecretCore.MetaSecretAppManagerInterface
import storage.KeyValueStorage


class OnboardingViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val metaSecretAppManager: MetaSecretAppManagerInterface
) : ViewModel(), CommonViewModel {
    val pages = listOf(
        OnBoardingPage.First,
        OnBoardingPage.Second,
        OnBoardingPage.Third
    )

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is OnboardingViewEvents) {
            when (event) {
                OnboardingViewEvents.COMPLETE_ONBOARDING -> {
                    viewModelScope.launch {
                        completeOnboarding()
                    }
                }
            }
        }
    }

    private suspend fun completeOnboarding() {
        keyValueStorage.isOnboardingCompleted = true
        when (metaSecretAppManager.checkAuth()) {
            AuthState.COMPLETED -> {
                println("\uD83D\uDC49 Move to Main")
                _currentPage.update { -2 } // TODO: Need to use enum instead of -2 and -1
            }
            AuthState.NOT_YET_COMPLETED -> {
                println("\uD83D\uDC49 OnboardingVM: Move to Sign Up")
                _currentPage.update { -1 } // TODO: Need to use enum instead of -2 and -1
            }
        }
    }
}

enum class OnboardingViewEvents: CommonViewModelEventsInterface {
    COMPLETE_ONBOARDING
}