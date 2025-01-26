package scenes.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*
import storage.KeyValueStorage


class OnboardingViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    val pages = listOf(
        OnBoardingPage.First,
        OnBoardingPage.Second,
        OnBoardingPage.Third
    )

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    fun completeOnboarding() {
        keyValueStorage.isOnboardingCompleted = true
        _currentPage.update { -1 }
    }
}