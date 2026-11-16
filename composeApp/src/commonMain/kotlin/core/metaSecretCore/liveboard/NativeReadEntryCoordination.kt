package core.metaSecretCore.liveboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Logical reservation; nullable origin stays null and never adopts a successor. */
internal class NativeReadTurn(val scope: NativeReadScope?, val decision: NativeDecisionRead? = null) {
    val entry: Boolean get() = decision != null
    val granted = CompletableDeferred<Boolean>(parent = null)
}
internal class NativeDecisionRead(
    val target: NativeReadRequirement,
    val observed: Set<NativeReadTurn>,
    val attempt: Long?,
) {
    val closed = CompletableDeferred<Unit>(parent = null)
    val scope: NativeReadScope get() = target.scope
}

/** Live owner-local scheduling metadata, with no scope/failure history or native lease. */
internal class NativeReadEntryCoordination {
    private class Observation(val overlap: Boolean, val active: Set<NativeReadTurn>)
    private class State(
        val active: Set<NativeReadTurn> = emptySet(),
        val pending: List<NativeReadTurn> = emptyList(),
        val decisions: Map<NativeDecisionRead, Observation> = emptyMap(),
        val started: Set<NativeReadTurn> = emptySet(),
        val changed: CompletableDeferred<Unit> = CompletableDeferred(parent = null),
    )
    internal class Closed(val turns: Set<NativeReadTurn>, val changed: CompletableDeferred<Unit>,
        val decisions: Set<NativeDecisionRead>)
    private val state = MutableStateFlow(State())

    private fun associated(turn: NativeReadTurn, scope: NativeReadScope): Boolean =
        !turn.entry && (turn.scope == null || turn.scope == scope)
    fun failed(read: NativeDecisionRead): Boolean = read.closed.isCompleted || read !in state.value.decisions
    fun overlapped(read: NativeDecisionRead): Boolean = state.value.decisions[read]?.overlap == true
    fun owns(turn: NativeReadTurn): Boolean = state.value.let { current -> turn in current.active &&
        (turn.decision == null || turn.decision in current.decisions) }
    fun reserved(turn: NativeReadTurn): Boolean = state.value.let { turn in it.active || turn in it.pending }
    fun started(turn: NativeReadTurn): Boolean = turn in state.value.started && owns(turn)
    fun changed(): CompletableDeferred<Unit> = state.value.changed

    fun reserveRead(scope: NativeReadScope?): NativeReadTurn {
        val turn = NativeReadTurn(scope)
        enqueue(turn)
        return turn
    }
    fun reserveDecision(read: NativeDecisionRead): NativeReadTurn? {
        val turn = NativeReadTurn(read.scope, read)
        return turn.takeIf { enqueue(it) }
    }
    private fun enqueue(turn: NativeReadTurn): Boolean {
        while (true) {
            val old = state.value
            // The same CAS which grants/queues ENTRY verifies its exact live watch.
            if (turn.decision != null && turn.decision !in old.decisions) return false
            val next = advance(State(old.active, old.pending + turn, old.decisions, old.started))
            if (state.compareAndSet(old, next)) { wake(old, next); return true }
        }
    }
    suspend fun await(turn: NativeReadTurn): NativeReadTurn? {
        var acquired = false
        try {
            acquired = turn.granted.await() && owns(turn)
            return turn.takeIf { acquired }
        } finally {
            // A cancelled reservation was never a read start or synchronization counter.
            if (!acquired) release(turn, false)
        }
    }
    fun start(turn: NativeReadTurn): Boolean {
        while (true) {
            val old = state.value
            if (turn.entry || turn !in old.active) return false
            if (turn in old.started) return true
            val decisions = old.decisions.mapValues { (read, observed) ->
                if (associated(turn, read.scope)) Observation(true, observed.active + turn) else observed
            }
            val next = State(old.active, old.pending, decisions, old.started + turn)
            if (state.compareAndSet(old, next)) { wake(old, next); return true }
        }
    }
    fun release(turn: NativeReadTurn, successful: Boolean) {
        while (true) {
            val old = state.value
            if (turn !in old.active && turn !in old.pending) return
            val failed = turn in old.started && !successful
            val decisions = old.decisions.filterNot { (_, observed) -> failed && turn in observed.active }
                .mapValues { (_, observed) -> Observation(observed.overlap, observed.active - turn) }
            val next = advance(State(old.active - turn, old.pending - turn, decisions, old.started - turn))
            if (state.compareAndSet(old, next)) { wake(old, next); return }
        }
    }

    fun watch(target: NativeReadRequirement, attempt: Long?): NativeDecisionRead {
        while (true) {
            val old = state.value
            val active = old.started.filter { associated(it, target.scope) }.toSet()
            val read = NativeDecisionRead(target, active, attempt)
            val observed = Observation(active.isNotEmpty(), active)
            if (state.compareAndSet(old, State(old.active, old.pending, old.decisions + (read to observed),
                old.started, old.changed))) return read
        }
    }
    fun unwatch(read: NativeDecisionRead) {
        while (true) {
            val old = state.value
            val removed = (old.active + old.pending).filter { it.decision === read }.toSet()
            val next = advance(State(old.active - removed, old.pending - removed, old.decisions - read,
                old.started - removed))
            if (state.compareAndSet(old, next)) { wake(old, next); break }
        }
        read.closed.complete(Unit)
    }
    suspend fun awaitObservedReads(read: NativeDecisionRead): Boolean {
        val observed = read.observed + state.value.decisions[read]?.active.orEmpty()
        while (true) {
            val current = state.value
            if (failed(read)) return false
            if (observed.none { it in current.active }) return true
            current.changed.await()
        }
    }

    /** Removal is synchronous metadata truth; deferred notifications follow privacy effects. */
    fun closeOwner(owner: NativeOwnerToken): Closed = close(null, owner)
    fun closeScope(scope: NativeReadScope): Closed = close(scope, null)
    private fun close(scope: NativeReadScope?, owner: NativeOwnerToken?): Closed {
        while (true) {
            val old = state.value
            val removed = (old.active + old.pending).filter {
                if (scope != null) it.scope == scope else it.scope?.owner === owner
            }.toSet()
            val ended = old.decisions.keys.filter {
                if (scope != null) it.scope == scope else it.scope.owner === owner
            }.toSet()
            val decisions = (old.decisions - ended).mapValues { (_, observed) ->
                Observation(observed.overlap, observed.active - removed)
            }
            val next = State(old.active - removed, old.pending - removed, decisions, old.started - removed)
            if (state.compareAndSet(old, next)) return Closed(removed, old.changed, ended)
        }
    }
    fun wakeClosed(closed: Closed) {
        closed.decisions.forEach { it.closed.complete(Unit) }
        closed.turns.forEach { it.granted.complete(false) }
        closed.changed.complete(Unit)
        while (true) {
            val old = state.value
            val next = advance(old)
            if (next === old || state.compareAndSet(old, next)) {
                if (next !== old) wake(old, next)
                return
            }
        }
    }

    private fun advance(current: State): State {
        val pending = current.pending.filter { it.decision == null || it.decision in current.decisions }
        val ready = if (pending.size == current.pending.size) current else State(current.active, pending,
            current.decisions, current.started)
        if (ready.active.any { it.entry } || pending.isEmpty()) return ready
        val first = pending.first()
        return if (first.entry && ready.active.isNotEmpty()) ready else {
            val granted = if (first.entry) listOf(first) else pending.takeWhile { !it.entry }
            State(ready.active + granted, pending.drop(granted.size), ready.decisions, ready.started)
        }
    }
    private fun wake(old: State, next: State) {
        (old.decisions.keys - next.decisions.keys).forEach { it.closed.complete(Unit) }
        (next.active - old.active).forEach { it.granted.complete(true) }
        (old.pending - next.pending.toSet() - next.active).forEach { it.granted.complete(false) }
        old.changed.complete(Unit)
    }
}
