package ui.scenes.profilescreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import core.KeyValueStorage

class ProfileScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    val devicesCount: StateFlow<Int> = keyValueStorage.deviceData.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val secretsCount: StateFlow<Int> = keyValueStorage.secretData.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun completeSignIn(state: Boolean) {
//        keyValueStorage.isSignInCompleted = state
    }

    fun getNickName(): String? {
        return keyValueStorage.signInInfo?.username
    }
}