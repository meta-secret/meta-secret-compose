package core.metaSecretCore.liveboard

import core.AppStateCacheProviderInterface

/** Presentation cleanup is separate from the already-drained, completed key transition. */
class NativeLocalReset(
    private val initializationOwner: NativeInitializationOwner,
    private val keyLifecycle: NativeKeyLifecycle,
    private val cache: AppStateCacheProviderInterface,
) {
    fun capture(): NativeOwnerToken = initializationOwner.capture()
    fun isCurrent(result: NativeKeyResult<Boolean>): Boolean = initializationOwner.isCurrent(result.owner)

    suspend fun reset(expectedOwner: NativeOwnerToken): NativeKeyResult<Boolean>? {
        val result = keyLifecycle.clearProfile(expectedOwner) ?: return null
        if (isCurrent(result)) cache.clearCache()
        return result
    }
}
