package scenes.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import models.apiModels.StateType
import sharedData.KeyChainInterface
import sharedData.metaSecretCore.InitResult
import sharedData.metaSecretCore.MetaSecretAppManager
import storage.KeyValueStorage


class OnboardingViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val metaSecretAppManager: MetaSecretAppManager
) : ViewModel() {
    val pages = listOf(
        OnBoardingPage.First,
        OnBoardingPage.Second,
        OnBoardingPage.Third
    )

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    suspend fun completeOnboarding() {
        keyValueStorage.isOnboardingCompleted = true
        if (checkAuth()) {
            println("\uD83D\uDC49 Move to Main")
            _currentPage.update { -2 }
        } else {
            println("\uD83D\uDC49 Move to Sign Up")
            _currentPage.update { -1 }
        }
    }

    private suspend fun checkAuth(): Boolean {
        println("✅Onboarding: Auth check")
        return when (metaSecretAppManager.initWithSavedKey()) {
            is InitResult.Success -> {
                metaSecretAppManager.getState() == StateType.MEMBER
            }
            else -> false
        }
    }
}