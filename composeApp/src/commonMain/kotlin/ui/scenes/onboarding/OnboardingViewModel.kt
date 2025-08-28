package ui.scenes.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import core.LogTags
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.metaSecretCore.AuthState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.KeyValueStorageInterface


class OnboardingViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
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
                println("✅${LogTags.ONBOARDING_VM}: Move to Main")
                _currentPage.update { -2 } // TODO: Need to use enum instead of -2 and -1
            }
            AuthState.NOT_YET_COMPLETED -> {
                println("✅${LogTags.ONBOARDING_VM}: Move to Sign Up")
                _currentPage.update { -1 } // TODO: Need to use enum instead of -2 and -1
            }
        }
    }
}

enum class OnboardingViewEvents: CommonViewModelEventsInterface {
    COMPLETE_ONBOARDING
}