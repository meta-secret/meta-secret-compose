package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import models.apiModels.AppStateModel

class AppStateCacheProvider(
    private val logger: DebugLoggerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val uiDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate
) : AppStateCacheProviderInterface {

    private val _synchronization = MutableStateFlow(VaultSynchronization(loading = true))
    override val synchronization: StateFlow<VaultSynchronization> = _synchronization
    override fun beginSynchronization() = publish {
        _synchronization.update { it.copy(loading = true, failed = false, activeRefreshes = it.activeRefreshes + 1) }
    }
    override fun finishSynchronization(success: Boolean) = publish {
        _synchronization.update {
            val remaining = (it.activeRefreshes - 1).coerceAtLeast(0)
            VaultSynchronization(loading = remaining > 0, failed = !success, activeRefreshes = remaining)
        }
    }

    private val _appState = MutableStateFlow<AppStateModel?>(null)
    override val appState: StateFlow<AppStateModel?> = _appState.asStateFlow()

    override fun updateCache(state: AppStateModel) = publish {
        if (state.success) {
            val member = state.getVaultFullInfo() as? models.apiModels.VaultFullInfo.Member
            keyValueStorage.syncSecretsFromVault(member?.member?.member?.vault?.secrets.orEmpty())
        }
        _appState.value = state
        logger.log(LogTag.AppStateCacheProvider.Message.CacheUpdated, "success=${state.success}", success = true)
    }

    // Synchronous publication keeps canonical cache reconciliation before loader dismissal.
    // Only presentation/metadata updates run here; Core/FFI work stays on IO.
    private fun publish(action: () -> Unit) {
        kotlinx.coroutines.runBlocking(uiDispatcher) { action() }
    }

    override fun clearCache() = publish {
        _appState.value = null
        logger.log(LogTag.AppStateCacheProvider.Message.CacheCleared, success = true)
    }
}
