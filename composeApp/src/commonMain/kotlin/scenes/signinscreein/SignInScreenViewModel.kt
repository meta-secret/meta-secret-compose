package scenes.signinscreein

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import storage.KeyValueStorage

class SignInScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()))
    }


    private val signInState = MutableStateFlow(0)
    val signInStatus: StateFlow<Int> = signInState

    fun completeSignIn() {
        keyValueStorage.isSignInCompleted = true
        signInState.update {-1}
    }
}