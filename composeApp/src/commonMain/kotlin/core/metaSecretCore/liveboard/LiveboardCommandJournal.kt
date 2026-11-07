package core.metaSecretCore.liveboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Completed entries retain typed metadata only; input/native closures belong to the worker. */
internal class LiveboardCommandJournal(
    private val scope: NativeReadScope,
    private val reads: NativeCanonicalReadOwner,
    private val release: (Any) -> Unit,
) {
    private class Command(val entry: LiveboardCommandEntry, val trace: LiveboardDecisionRejectionTrace?) {
        val result = CompletableDeferred<NativeLiveboardOutcome<*>>(parent = null)
        val settled = CompletableDeferred<Unit>(parent = null)
        val finished = MutableStateFlow(false)
        var job: Job? = null
    }
    private val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Retention(val outcome: NativeLiveboardOutcome.Entered<*>,
        val settled: CompletableDeferred<Unit>)
    private class State(
        val pending: Map<Any, Command> = emptyMap(),
        val retained: Map<Any, Retention> = emptyMap(),
        val targets: Map<Any, NativeReadRequirement> = emptyMap(),
    )
    private val state = MutableStateFlow(State())
    @Suppress("UNCHECKED_CAST")
    fun <T> retained(intent: Any): NativeLiveboardOutcome.Entered<T>? = state.value.retained[intent
        ]?.outcome as? NativeLiveboardOutcome.Entered<T>
    fun isSettledOutcome(intent: Any, outcome: NativeLiveboardOutcome.Entered<*>): Boolean =
        state.value.retained[intent]?.let { it.outcome === outcome && it.settled.isCompleted &&
            !it.settled.isCancelled } == true
    fun hasOutcome(intent: Any): Boolean = intent in state.value.retained
    fun isPending(intent: Any): Boolean = state.value.pending[intent]?.settled?.isCompleted == false
    fun isResolvedBy(intent: Any, frame: NativeReadFrame): Boolean = state.value.targets[intent]?.let {
        liveboardRequirementResolved(reads, scope, it, frame)
    } == true

    suspend fun <T> issue(
        intent: Any,
        admission: LiveboardEntryAdmission,
        native: suspend (LiveboardEntryAdmission) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> = issueObserved(intent, admission, null, native)

    suspend fun <T> issueObserved(
        intent: Any,
        admission: LiveboardEntryAdmission,
        trace: LiveboardDecisionRejectionTrace?,
        native: suspend (LiveboardEntryAdmission) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> = issueCommand(intent, Command(LiveboardCommandEntry(admission), trace)) {
        native(it.admission)
    }

    suspend fun <T> issueDecision(
        intent: LiveboardDecisionIntent,
        admission: LiveboardEntryAdmission,
        trace: LiveboardDecisionRejectionTrace?,
        native: suspend (LiveboardCommandEntry) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> = issueCommand(intent, Command(LiveboardCommandEntry(admission, true), trace), native)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> issueCommand(
        intent: Any,
        command: Command,
        native: suspend (LiveboardCommandEntry) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> {
        val trace = command.trace
        val entry = command.entry
        if (entry.isPreparing) entry.attachRead(reads.registerDecision(scope), reads)
        while (true) {
            val old = state.value
            old.retained[intent]?.let {
                entry.releaseRead(reads)
                return it.outcome as NativeLiveboardOutcome<T>
            }
            if (intent in old.pending) {
                trace?.direct(LiveboardDecisionRejectionSource.JOURNAL_PENDING)
                entry.releaseRead(reads)
                return unavailable()
            }
            if (state.compareAndSet(old, State(old.pending + (intent to command), old.retained, old.targets))) break
        }
        demandPreparing(intent, command)
        if (command.finished.value) {
            entry.releaseRead(reads)
            return command.result.await() as NativeLiveboardOutcome<T>
        }
        val job = workers.launch(start = CoroutineStart.LAZY) {
            val result = try {
                if (entry.isCancelled) {
                    trace?.note(LiveboardDecisionRejectionSource.JOURNAL_WORKER_CANCELLED)
                    unavailable()
                } else native(entry)
            } catch (_: Exception) {
                if (entry.admission.isAdmitted) NativeLiveboardOutcome.Entered(scope, NativeResult.Uncertain
                    ) else {
                    trace?.note(LiveboardDecisionRejectionSource.JOURNAL_EXCEPTION_BEFORE_ADMISSION)
                    unavailable()
                }
            } finally { entry.admission.discardPayload() }
            finish(intent, command, result)
        }
        command.job = job
        job.invokeOnCompletion {
            if (!command.finished.value) finish(intent, command,
                if (entry.admission.isAdmitted) NativeLiveboardOutcome.Entered(scope, NativeResult.Uncertain
                    ) else unavailable(), command.trace?.let {
                    LiveboardDecisionRejectionSource.JOURNAL_COMPLETION_BEFORE_ADMISSION })
            command.job = null
        }
        return suspendCancellableCoroutine { continuation ->
            val observer = command.result.invokeOnCompletion { failure ->
                val result = if (failure == null) runCatching {
                    command.result.getCompleted() as NativeLiveboardOutcome<T>
                } else Result.failure(failure)
                continuation.resumeWith(result)
            }
            continuation.invokeOnCancellation {
                observer.dispose()
                cancel(intent, command)
            }
            // Register synchronous pre-entry revocation before starting this same private worker.
            job.start()
        }
    }

    private fun demandPreparing(intent: Any, command: Command) {
        val entry = command.entry
        if (!entry.isPreparing) return
        val read = entry.read
        read?.closed?.invokeOnCompletion {
            if (!command.finished.value && entry.revoke()) settleCancelled(intent, command)
        }
        if (read == null || !reads.demandDecision(read)) {
            entry.revoke()
            settleCancelled(intent, command)
        }
    }

    private fun finish(intent: Any, command: Command, result: NativeLiveboardOutcome<*>,
        source: LiveboardDecisionRejectionSource? = null) {
        if (!command.finished.compareAndSet(false, true)) return
        command.trace?.finish(result is NativeLiveboardOutcome.Entered, source)
        command.entry.admission.discardPayload()
        if (result !is NativeLiveboardOutcome.Entered) command.entry.revoke()
        command.entry.releaseRead(reads)
        val target = if (result is NativeLiveboardOutcome.Entered) reads.requireFreshRead(scope) else null
        if (result !is NativeLiveboardOutcome.Entered) command.entry.admission.cancelQueued()
        try {
            if (result !is NativeLiveboardOutcome.Entered) release(intent)
            // Registration and completed truth change in one CAS; no completion-window replay.
            state.update { old ->
                if (old.pending[intent] !== command) old
                else State(old.pending - intent,
                    if (result is NativeLiveboardOutcome.Entered) old.retained +
                        (intent to Retention(result, command.settled)) else old.retained,
                    if (target != null) old.targets + (intent to target) else old.targets)
            }
        } finally {
            command.settled.complete(Unit)
            command.result.complete(result)
        }
    }

    private fun cancel(intent: Any, command: Command) {
        if (command.entry.revoke()) settleCancelled(intent, command)
    }
    private fun settleCancelled(intent: Any, command: Command) {
        if (!command.entry.isCancelled) return
        command.job?.cancel()
        finish(intent, command, unavailable(), command.trace?.let {
            LiveboardDecisionRejectionSource.JOURNAL_CANCELLED_SETTLEMENT })
    }
    fun isPreparing(intent: Any): Boolean = state.value.pending[intent]?.entry?.isPreparing == true
    fun revokeQueued(technical: Boolean = false) { state.value.pending.values.forEach { it.entry.revoke(technical) } }
    fun settleRevoked(technical: Boolean = false) {
        state.value.pending.forEach { (intent, command) ->
            if (!technical || !command.entry.isPreparing) settleCancelled(intent, command)
        }
    }
    fun revokeDecisions() { state.value.pending.filterKeys { it is LiveboardDecisionIntent }.values.forEach {
        it.entry.revoke()
    } }
    fun settleDecisions() { state.value.pending.filterKeys { it is LiveboardDecisionIntent }.forEach {
        (intent, command) -> settleCancelled(intent, command)
    } }
    suspend fun awaitSettled() { state.value.pending.values.toList().forEach { it.settled.await() } }
    private fun unavailable(): NativeLiveboardOutcome.NotEntered = NativeLiveboardOutcome.NotEntered(scope,
        NativeFailure.OperationUnavailable)
}

/** Current physical truth may follow the exact successful proof which consumed this target. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun liveboardRequirementResolved(
    reads: NativeCanonicalReadOwner,
    scope: NativeReadScope,
    target: NativeReadRequirement,
    frame: NativeReadFrame,
): Boolean {
    if (reads.frames.value !== frame || frame.scope != scope || frame.outcome != NativeReadOutcome.SUCCESS || frame
        .state?.success != true) return false
    if (target in frame.coveredRequirements) return true
    if (!target.completion.isCompleted || target.completion.isCancelled) return false
    val proof = try { target.completion.getCompleted() } catch (_: Exception) { return false }
    return proof.scope == scope && proof.outcome == NativeReadOutcome.SUCCESS && proof.state?.success == true &&
        target in proof.coveredRequirements && frame.attempt >= proof.attempt
}
