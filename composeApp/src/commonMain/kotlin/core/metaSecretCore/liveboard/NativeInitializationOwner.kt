package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Owns the identity of the existing native instance; no second runtime is created. */
class NativeInitializationOwner(
    private val core: MetaSecretCoreInterface,
    private val environment: NativeReadEnvironment,
) {
    private val admission = NativeAdmissionTracker()
    fun capture(): NativeOwnerToken = admission.capture()
    fun isCurrent(owner: NativeOwnerToken): Boolean = admission.owns(owner)
    internal fun currentScope(): NativeReadScope? = admission.scope()

    suspend fun <T> invoke(expected: NativeReadScope, native: () -> T): NativeInvocation<T> = withContext(
        environment.io) {
        val lease = admission.enter(expected) ?: return@withContext NativeInvocation.Retired
        try {
            invokeEntered(expected, native)
        } finally {
            lease.release()
        }
    }

    /** The physical reader releases this actual-entry lease only after unlocking its read mutex. */
    internal suspend fun <T> invokeRead(
        expected: NativeReadScope,
        acquired: (NativeInvocationLease) -> Unit,
        native: () -> T,
    ): NativeInvocation<T> = withContext(environment.io) {
        val lease = admission.enter(expected) ?: return@withContext NativeInvocation.Retired
        acquired(lease)
        invokeEntered(expected, native)
    }

    private fun <T> invokeEntered(expected: NativeReadScope, native: () -> T): NativeInvocation<T> {
        return try {
            NativeInvocation.Value(expected, native())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NativeInvocation.Failure(expected, failure)
        }
    }

    internal suspend fun beginTransition(expected: NativeOwnerToken, retire: Boolean) = admission.begin(expected,
        retire)
    internal fun owns(transition: NativeAdmissionTracker.Transition) = admission.owns(transition)
    internal fun retire(transition: NativeAdmissionTracker.Transition) = admission.retire(transition)
    internal fun finish(transition: NativeAdmissionTracker.Transition, epoch: NativeOwnerEpoch?) = admission.finish(
        transition, epoch)
    internal suspend fun initialize(transition: NativeAdmissionTracker.Transition, masterKey: String): String {
        return withContext(environment.io) {
            check(owns(transition))
            core.initAppManager(masterKey)
        }
    }

    /** Only a drained exclusive reservation may use this nonpublishing native route. */
    internal suspend fun invokeExclusive(
        transition: NativeAdmissionTracker.Transition,
        native: () -> String,
    ): String? = withContext(environment.io) {
        if (!owns(transition) || !transition.drained.isCompleted) return@withContext null
        native().takeIf { owns(transition) }
    }

    fun invalidate() = admission.invalidate()
}
