package core.metaSecretCore.liveboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow

/** One atomic native admission fence; work and completion callbacks run outside it. */
internal class NativeAdmissionTracker {
    internal class Transition(
        val priorScope: NativeReadScope?,
        var owner: NativeOwnerToken,
    ) {
        private val drainSource = CompletableDeferred<Unit>(parent = null)
        private val finishSource = CompletableDeferred<Unit>(parent = null)
        val drained: Deferred<Unit> = drainSource
        val finished: Deferred<Unit> = finishSource
        fun signalDrained() { drainSource.complete(Unit) }
        fun signalFinished() { finishSource.complete(Unit) }
    }

    private class State(
        val owner: NativeOwnerToken,
        val epoch: NativeOwnerEpoch?,
        val entered: Int = 0,
        val transition: Transition? = null,
    ) {
        fun scope(): NativeReadScope? = epoch?.let { NativeReadScope(owner, it) }
    }

    private val state = MutableStateFlow(State(NativeOwnerToken(), null))
    fun capture(): NativeOwnerToken = state.value.owner
    fun scope(): NativeReadScope? = state.value.let { if (it.transition == null) it.scope() else null }
    fun owns(owner: NativeOwnerToken): Boolean = state.value.let { it.owner === owner && it.transition == null }

    fun enter(expected: NativeReadScope): NativeInvocationLease? {
        while (true) {
            val old = state.value
            if (old.transition != null || old.scope() != expected) return null
            if (state.compareAndSet(old, State(old.owner, old.epoch, old.entered + 1))) {
                return NativeInvocationLease(::leave)
            }
        }
    }

    private fun leave() {
        while (true) {
            val old = state.value
            check(old.entered > 0)
            val next = State(old.owner, old.epoch, old.entered - 1, old.transition)
            if (state.compareAndSet(old, next)) {
                if (next.entered == 0) next.transition?.signalDrained()
                return
            }
        }
    }

    suspend fun begin(expected: NativeOwnerToken, retire: Boolean): Transition? {
        while (true) {
            val old = state.value
            if (old.owner !== expected) return null
            if (old.transition != null) {
                old.transition.finished.await()
                continue
            }
            val transition = Transition(old.scope(), if (retire) NativeOwnerToken() else old.owner)
            val next = State(transition.owner, if (retire) null else old.epoch, old.entered, transition)
            if (state.compareAndSet(old, next)) {
                if (next.entered == 0) transition.signalDrained()
                return transition
            }
        }
    }

    fun owns(transition: Transition): Boolean {
        val current = state.value
        return current.transition === transition && current.owner === transition.owner
    }

    fun retire(transition: Transition): Boolean {
        while (true) {
            val old = state.value
            if (old.transition !== transition || old.owner !== transition.owner) return false
            val token = NativeOwnerToken()
            if (state.compareAndSet(old, State(token, null, old.entered, transition))) {
                transition.owner = token
                return true
            }
        }
    }

    fun finish(transition: Transition, epoch: NativeOwnerEpoch?): NativeOwnerToken? {
        while (true) {
            val old = state.value
            if (old.transition !== transition) return null
            val authorized = old.owner === transition.owner
            val next = State(old.owner, if (authorized) epoch else old.epoch, old.entered)
            if (state.compareAndSet(old, next)) {
                // No observer, native lease or transition lock is held while a waiter resumes.
                transition.signalFinished()
                return next.owner.takeIf { authorized }
            }
        }
    }

    fun invalidate() {
        while (true) {
            val old = state.value
            if (state.compareAndSet(old, State(NativeOwnerToken(), null, old.entered, old.transition))) return
        }
    }
}

internal class NativeInvocationLease(private val releaseNative: () -> Unit) {
    private val released = MutableStateFlow(false)
    fun release() { if (released.compareAndSet(false, true)) releaseNative() }
}
