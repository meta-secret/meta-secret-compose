package core.metaSecretCore.liveboard

import core.AppStateCacheProviderInterface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NativeRefreshOrigin internal constructor(
    val expectedScope: NativeReadScope?,
    internal val generation: Any,
)

/** A scheduled origin and its exact flight; neither callback grants native permission. */
class NativeRefreshRequest internal constructor(
    val expectedScope: NativeReadScope?,
    private val current: () -> Boolean,
    private val record: (NativeReadCall) -> Boolean,
) {
    internal var turn: NativeReadTurn? = null
    fun isCurrent(): Boolean = current()
    fun recordActualRead(call: NativeReadCall): Boolean = record(call)
}

/** Owns the existing Socket refresh scheduling; actual reads still use the shared owner. */
class NativeRefreshLane(
    private val scope: CoroutineScope,
    private val cache: AppStateCacheProviderInterface,
    private val refresh: suspend (NativeRefreshRequest) -> Boolean,
    private val readOwner: NativeCanonicalReadOwner,
) {
    private class Flight(val intent: NativeRefreshOrigin, val turn: NativeReadTurn) {
        val job = MutableStateFlow<Job?>(null)
        val begun = CompletableDeferred<Boolean>(parent = null)
        val settled = CompletableDeferred<Unit>(parent = null)
        val attempt = MutableStateFlow<Long?>(null)
        var successful = false
    }
    private data class Visibility(
        val generation: Any = Any(),
        val foreground: Boolean = false,
        val hidden: Boolean = false,
        val paused: Boolean = false,
    )
    private class Pending(val intent: NativeRefreshOrigin, val turn: NativeReadTurn?)
    private data class State(
        val visibility: Visibility = Visibility(),
        val flight: Flight? = null,
        val pending: Pending? = null,
    )
    private val state = MutableStateFlow(State())
    val isForeground: Boolean get() = state.value.visibility.foreground
    val isPaused: Boolean get() = state.value.visibility.paused

    fun capture(expected: NativeReadScope?): NativeRefreshOrigin = NativeRefreshOrigin(expected, state.value
        .visibility.generation)

    fun foreground(origin: NativeRefreshOrigin) {
        update {
            if (it.visibility.generation !== origin.generation) it
            else it.copy(visibility = it.visibility.copy(foreground = true, hidden = false))
        }
        request(origin)
    }

    fun background(): () -> Boolean {
        val generation = Any()
        val prior = update {
            it.copy(visibility = it.visibility.copy(generation = generation, foreground = false, hidden = true),
                pending = null)
        }
        discard(prior.pending)
        // Keep the flight until actual Job/native exit and synchronization settlement.
        prior.flight?.job?.value?.cancel()
        return { state.value.visibility.let { it.generation === generation && it.hidden } }
    }

    fun request(origin: NativeRefreshOrigin) {
        while (true) {
            val old = state.value
            if (old.visibility.generation !== origin.generation) return
            val runnable = !old.visibility.hidden && !old.visibility.paused
            val pending = preparePending(origin, old.pending, runnable) ?: return
            val flight = if (runnable && old.flight == null) {
                val turn = pending.turn ?: return
                Flight(origin, turn)
            } else null
            val next = if (flight == null) old.copy(pending = pending)
                else old.copy(flight = flight, pending = null)
            if (state.compareAndSet(old, next)) {
                discard(old.pending, pending.turn)
                if (flight != null) start(flight)
                return
            }
            // Only a newly reserved, never-started token belongs to this losing CAS.
            if (pending.turn !== old.pending?.turn) discard(pending)
        }
    }

    private fun preparePending(origin: NativeRefreshOrigin, old: Pending?, runnable: Boolean): Pending? {
        if (!runnable) return Pending(origin, null)
        val same = old?.intent?.let { it.expectedScope == origin.expectedScope &&
            it.generation === origin.generation } == true
        val retained = old?.turn?.takeIf { same && readOwner.reservationCurrent(it) }
        val turn = retained ?: readOwner.reserveRead(origin.expectedScope)
        return if (turn == null) null else Pending(origin, turn)
    }
    private fun discard(pending: Pending?, retained: NativeReadTurn? = null) {
        pending?.turn?.takeIf { it !== retained }?.let { readOwner.finishRead(it, false) }
    }

    fun pause() {
        val prior = update { it.copy(visibility = it.visibility.copy(paused = true),
            pending = it.pending?.let { pending -> Pending(pending.intent, null) }) }
        discard(prior.pending)
    }

    suspend fun pauseAndAwait() {
        val prior = update { it.copy(visibility = it.visibility.copy(paused = true), pending = null) }
        discard(prior.pending)
        prior.flight?.let { flight ->
            flight.job.value?.cancel()
            flight.settled.await()
        }
    }

    fun resume(origin: NativeRefreshOrigin) {
        update {
            if (it.visibility.generation !== origin.generation) it
            else it.copy(visibility = it.visibility.copy(paused = false))
        }
        if (isForeground) request(origin)
    }

    fun mayConsume(call: NativeReadCall): Boolean {
        val current = state.value
        val flight = current.flight ?: return false
        return isCurrent(current, flight) && flight.intent.expectedScope == call.frame.scope &&
            flight.attempt.value == call.frame.attempt
    }

    private fun isCurrent(current: State, flight: Flight): Boolean =
        current.flight === flight && current.visibility.generation === flight.intent.generation &&
            !current.visibility.hidden && !current.visibility.paused

    private fun isCurrent(flight: Flight): Boolean = isCurrent(state.value, flight)

    private fun start(flight: Flight) {
        val request = NativeRefreshRequest(flight.intent.expectedScope, { isCurrent(flight) }) { call ->
            if (!isCurrent(flight) || call.frame.scope != flight.intent.expectedScope) false
            else {
                flight.attempt.value = call.frame.attempt
                isCurrent(flight)
            }
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val turn = readOwner.awaitRead(flight.turn) ?: return@launch
            request.turn = turn
            var enteredBegin = false
            try {
                if (isCurrent(flight) && readOwner.startRead(turn)) {
                    enteredBegin = true
                    cache.beginSynchronization()
                }
                flight.begun.complete(enteredBegin)
                if (enteredBegin && isCurrent(flight)) flight.successful = refresh(request)
            } finally {
                flight.begun.complete(enteredBegin)
            }
        }
        flight.job.value = job
        job.invokeOnCompletion { cause ->
            // A lazy body need not ever start; settlement still owns its exact token/flight.
            flight.begun.complete(false)
            scope.launch(NonCancellable) { settle(flight, cause == null && flight.successful) }
        }
        if (!isCurrent(flight)) job.cancel() else job.start()
    }

    private suspend fun settle(flight: Flight, successful: Boolean) = withContext(NonCancellable) {
        val completed = successful && isCurrent(flight)
        var finished = false
        try {
            if (flight.begun.await()) cache.finishSynchronization(completed)
            finished = completed
        } finally {
            // Finish observers still see A owned; they can only queue a fresh successor.
            while (true) {
                val current = state.value
                if (current.flight !== flight || state.compareAndSet(current, current.copy(flight = null))) break
            }
            readOwner.finishRead(flight.turn, finished)
            flight.settled.complete(Unit)
            startPending()
        }
    }

    private fun startPending() {
        while (true) {
            val current = state.value
            if (current.visibility.hidden || current.visibility.paused || current.flight != null) return
            val pending = current.pending ?: return
            if (pending.intent.generation !== current.visibility.generation) {
                if (state.compareAndSet(current, current.copy(pending = null))) {
                    discard(pending)
                    return
                }
            } else {
                // A parked origin becomes runnable here or through the existing resume/foreground request.
                val prepared = preparePending(pending.intent, pending, true) ?: run {
                    if (state.compareAndSet(current, current.copy(pending = null))) discard(pending)
                    return
                }
                val turn = prepared.turn ?: return
                val flight = Flight(prepared.intent, turn)
                if (state.compareAndSet(current, current.copy(flight = flight, pending = null))) {
                    discard(pending, turn)
                    start(flight)
                    return
                }
                if (turn !== pending.turn) discard(prepared)
            }
        }
    }

    /** Returns the exact prior state; no observer/native effect is permitted inside this CAS. */
    private fun update(transform: (State) -> State): State {
        while (true) {
            val old = state.value
            if (state.compareAndSet(old, transform(old))) return old
        }
    }
}
