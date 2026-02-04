package core

import kotlinx.coroutines.flow.StateFlow
import models.apiModels.AppStateModel

interface AppStateCacheProviderInterface {
    val appState: StateFlow<AppStateModel?>
    fun updateCache(state: AppStateModel)
    fun clearCache()
}
