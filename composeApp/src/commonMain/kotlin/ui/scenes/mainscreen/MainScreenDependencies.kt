package ui.scenes.mainscreen

import core.AlertCoordinatorInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.liveboard.NativeKeyResult
import core.metaSecretCore.liveboard.NativeLocalReset
import core.metaSecretCore.liveboard.NativeOwnerToken
import kotlinx.coroutines.flow.MutableStateFlow

/** Local removal presentation progress is separate from native/key ownership. */
class MainScreenDependencies(
    val stats: VaultStatsProviderInterface,
    val alerts: AlertCoordinatorInterface,
    private val reset: NativeLocalReset,
) {
    private val removal = MutableStateFlow<NativeOwnerToken?>(null)
    fun beginRemoval(): NativeOwnerToken? {
        val owner = reset.capture()
        return owner.takeIf { removal.compareAndSet(null, owner) }
    }
    fun sameGeneration(owner: NativeOwnerToken): Boolean = reset.capture() === owner
    suspend fun clearRemoval(owner: NativeOwnerToken): NativeKeyResult<Boolean>? = reset.reset(owner)
    fun isCurrent(result: NativeKeyResult<Boolean>): Boolean = reset.isCurrent(result)
    fun dismiss(result: NativeKeyResult<Boolean>) {
        if (!isCurrent(result)) return
        alerts.dismissJoinRequest()
        if (isCurrent(result)) alerts.dismissRecoveryRequest()
    }
    fun finishRemoval(owner: NativeOwnerToken, result: NativeKeyResult<Boolean>?) {
        if (result == null || !isCurrent(result)) removal.compareAndSet(owner, null)
    }
    fun consumeRemoval() { removal.value = null }
}
