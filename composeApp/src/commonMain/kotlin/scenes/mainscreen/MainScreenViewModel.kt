package scenes.mainscreen

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage

class MainScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun clean() {
        keyValueStorage.cleanStorage()
    }
}
