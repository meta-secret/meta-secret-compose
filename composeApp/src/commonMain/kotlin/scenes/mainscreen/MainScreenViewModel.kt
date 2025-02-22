package scenes.mainscreen

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage
import ui.TabStateHolder

class MainScreenViewModel(
     keyValueStorage: KeyValueStorage
) : ViewModel() {
    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
}
