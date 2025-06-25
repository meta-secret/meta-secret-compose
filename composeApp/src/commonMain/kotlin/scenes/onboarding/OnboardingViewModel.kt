package scenes.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import sharedData.KeyChainInterface
import storage.KeyValueStorage


class OnboardingViewModel(
    private val keyValueStorage: KeyValueStorage,
    private val keyChain: KeyChainInterface
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
            _currentPage.update { -2 }
        } else {
            _currentPage.update { -1 }
        }
    }

    private suspend fun checkAuth(): Boolean {
        val masterKey = keyChain.getString("master_key")
        println("\uD83E\uDEC6 Master key is: $masterKey")
        return !masterKey.isNullOrEmpty()
    }
}