package scenes.mainscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import storage.KeyValueStorage

class MainScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    private val _currentScreen = MutableStateFlow(0)
    val currentScreen: StateFlow<Int> = _currentScreen
    }
