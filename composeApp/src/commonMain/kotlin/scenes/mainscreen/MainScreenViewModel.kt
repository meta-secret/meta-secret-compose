package scenes.mainscreen

import androidx.lifecycle.ViewModel
import sharedData.metaSecretCore.MetaSecretAppManager
import storage.KeyValueStorage
import ui.TabStateHolder

class MainScreenViewModel(
    keyValueStorage: KeyValueStorage,
    private val appManager: MetaSecretAppManager
) : ViewModel() {
    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
}
