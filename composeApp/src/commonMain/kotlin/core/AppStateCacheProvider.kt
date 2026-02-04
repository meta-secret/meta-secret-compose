package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import models.apiModels.AppStateModel

class AppStateCacheProvider(
    private val logger: DebugLoggerInterface
) : AppStateCacheProviderInterface {

    private val _appState = MutableStateFlow<AppStateModel?>(null)
    override val appState: StateFlow<AppStateModel?> = _appState.asStateFlow()

    override fun updateCache(state: AppStateModel) {
        _appState.value = state
        logger.log(LogTag.AppStateCacheProvider.Message.CacheUpdated, "success=${state.success}", success = true)
    }

    override fun clearCache() {
        _appState.value = null
        logger.log(LogTag.AppStateCacheProvider.Message.CacheCleared, success = true)
    }
}
