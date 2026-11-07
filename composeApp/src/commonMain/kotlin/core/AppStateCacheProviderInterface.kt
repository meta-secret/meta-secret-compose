package core

import kotlinx.coroutines.flow.StateFlow
import models.apiModels.AppStateModel
import core.metaSecretCore.liveboard.NativeReadFrame

interface AppStateCacheProviderInterface {
    val canonicalFrames: StateFlow<NativeReadFrame?> get() = idleCanonicalFrames
    suspend fun publishCanonical(frame: NativeReadFrame) { frame.state?.let(::updateCache) }
    fun revokeCanonical() {}
    val synchronization: StateFlow<VaultSynchronization> get() = idleVaultSynchronization
    fun beginSynchronization() {}
    fun finishSynchronization(success: Boolean) {}
    val appState: StateFlow<AppStateModel?>
    fun updateCache(state: AppStateModel)
    fun clearCache()
}


data class VaultSynchronization(val loading: Boolean = false, val failed: Boolean = false, val activeRefreshes: Int = 0)
private val idleVaultSynchronization = kotlinx.coroutines.flow.MutableStateFlow(VaultSynchronization())
private val idleCanonicalFrames = kotlinx.coroutines.flow.MutableStateFlow<NativeReadFrame?>(null)
