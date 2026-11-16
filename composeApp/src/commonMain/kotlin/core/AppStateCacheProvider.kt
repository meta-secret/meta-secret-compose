package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.update
import models.apiModels.AppStateModel
import core.metaSecretCore.liveboard.NativeReadFrame
import kotlinx.coroutines.withContext

class AppStateCacheProvider(
    private val logger: DebugLoggerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val uiDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate
) : AppStateCacheProviderInterface {

    private val publication = MutableStateFlow(CachePublication(null, null))
    override val canonicalFrames: StateFlow<NativeReadFrame?> = CacheProjection(publication) { it.frame }
    override suspend fun publishCanonical(frame: NativeReadFrame) = withContext(uiDispatcher) {
        val state = frame.state
        if (state == null) publication.value = CachePublication(publication.value.state, frame)
        else updateState(state, frame)
    }
    override fun revokeCanonical() = publish(::revokeProof)

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

    override val appState: StateFlow<AppStateModel?> = CacheProjection(publication) { it.state }

    override fun updateCache(state: AppStateModel) = publish {
        revokeProof()
        updateState(state, null)
    }

    private fun updateState(state: AppStateModel, frame: NativeReadFrame?) {
        if (state.success) {
            val member = state.getVaultFullInfo() as? models.apiModels.VaultFullInfo.Member
            keyValueStorage.syncSecretsFromVault(member?.member?.member?.vault?.secrets.orEmpty())
        }
        publication.value = CachePublication(state, frame)
        logger.log(LogTag.AppStateCacheProvider.Message.CacheUpdated, "success=${state.success}", success = true)
    }

    private fun revokeProof() {
        publication.value = CachePublication(publication.value.state, null)
    }

    // Synchronous publication keeps canonical cache reconciliation before loader dismissal.
    // Only presentation/metadata updates run here; Core/FFI work stays on IO.
    private fun publish(action: () -> Unit) {
        kotlinx.coroutines.runBlocking(uiDispatcher) { action() }
    }

    override fun clearCache() = publish {
        publication.value = CachePublication(null, null)
        logger.log(LogTag.AppStateCacheProvider.Message.CacheCleared, success = true)
    }
}

/** Identity equality adopts the latest instance even when its data is unchanged. */
private class CachePublication(val state: AppStateModel?, val frame: NativeReadFrame?)

/** Projects the current pair without another store, scope or duplicate legacy emissions. */
@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
private class CacheProjection<S, T>(
    private val source: StateFlow<S>,
    private val select: (S) -> T,
) : StateFlow<T> {
    override val value: T get() = select(source.value)
    override val replayCache: List<T> get() = listOf(value)

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        var emitted = false
        var previous: T? = null
        source.collect(object : FlowCollector<S> {
            override suspend fun emit(value: S) {
                val selected = select(value)
                if (!emitted || previous != selected) {
                    emitted = true
                    previous = selected
                    collector.emit(selected)
                }
            }
        })
    }
}
