package core.metaSecretCore.liveboard

import models.apiModels.AppStateModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Opaque local identity exists even while no native manager is initialized. */
class NativeOwnerToken internal constructor()

class NativeOwnerEpoch internal constructor()

data class NativeReadScope(val owner: NativeOwnerToken, val epoch: NativeOwnerEpoch)

class NativeReadRequirement internal constructor(internal val scope: NativeReadScope) {
    private val completionSource = CompletableDeferred<NativeReadFrame>(parent = null)
    val completion: Deferred<NativeReadFrame> = completionSource

    internal fun fulfill(frame: NativeReadFrame) {
        if (frame.outcome == NativeReadOutcome.SUCCESS && frame.scope == scope && this in frame.coveredRequirements) {
            completionSource.complete(frame)
        }
    }

    internal fun retire(frame: NativeReadFrame) {
        if (frame.outcome == NativeReadOutcome.RETIRED && frame.state == null && frame.scope == scope) {
            completionSource.complete(frame)
        }
    }
}

sealed class NativeInvocation<out T> {
    data class Value<T>(val scope: NativeReadScope, val value: T) : NativeInvocation<T>()
    data class Failure(val scope: NativeReadScope, val cause: Exception) : NativeInvocation<Nothing>()
    data object Retired : NativeInvocation<Nothing>()
}

sealed class NativeReadCall {
    abstract val frame: NativeReadFrame
    data class Value(override val frame: NativeReadFrame) : NativeReadCall()
    data class Failure(override val frame: NativeReadFrame, val cause: Exception) : NativeReadCall()
}

enum class NativeReadOutcome { SUCCESS, FAILED, RETIRED }

data class NativeReadFrame(
    val attempt: Long,
    val scope: NativeReadScope?,
    val coveredRequirements: Set<NativeReadRequirement>,
    val outcome: NativeReadOutcome,
    val state: AppStateModel?,
)
