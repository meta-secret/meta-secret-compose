package scenes.mainscreen

import androidx.lifecycle.ViewModel
import sharedData.MetaSecretAppManager
import storage.KeyValueStorage
import ui.TabStateHolder

class MainScreenViewModel(
    keyValueStorage: KeyValueStorage,
    private val appManager: MetaSecretAppManager
) : ViewModel() {
    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
    
    // Метод для проверки инициализации appManager
//    fun isAppManagerInitialized(): Boolean {
////        return appManager.isInitialized
//    }
    
    // Пример использования MetaSecretAppManager
    // Любой метод, который вам нужен, можно вызвать через экземпляр appManager
    // Например:
    // fun someAppManagerMethod() {
    //     appManager.someMethod()
    // }
}
