package scenes.signinscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import storage.KeyValueStorage

class SignInScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()))
    }


    val signInStatus: StateFlow<Boolean> =  MutableStateFlow(false)

    fun completeSignIn(state: Boolean) {
        keyValueStorage.isSignInCompleted = state
// Returns to Onboarding

//        if (!state) {
//            keyValueStorage.isOnboardingCompleted = state
//        }
    }
}