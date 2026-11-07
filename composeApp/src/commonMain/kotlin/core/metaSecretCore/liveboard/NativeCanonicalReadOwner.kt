package core.metaSecretCore.liveboard

import core.AppStateCacheProviderInterface
import core.DebugLoggerInterface
import core.LogFormatterInterface
import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel

class NativeReadDependencies(
    val core: MetaSecretCoreInterface,
    val logger: DebugLoggerInterface,
    val cache: AppStateCacheProviderInterface,
    val logFormatter: LogFormatterInterface?,
)

class NativeReadRuntime(
    val initializationOwner: NativeInitializationOwner,
    val environment: NativeReadEnvironment,
    val privacy: LiveboardPrivacyOwner,
)

/** Serializes physical reads and certifies only requirements present at actual entry. */
class NativeCanonicalReadOwner(
    private val dependencies: NativeReadDependencies,
    private val runtime: NativeReadRuntime,
) {
    private val physicalReadMutex = Mutex()
    private val coordination = NativeReadEntryCoordination()
    private val pendingRequirements = MutableStateFlow<Set<NativeReadRequirement>>(emptySet())
    private var nextAttempt = 0L
    private var publishedAttempt = 0L
    val frames: StateFlow<NativeReadFrame?> = dependencies.cache.canonicalFrames
    val synchronization: StateFlow<core.VaultSynchronization> = dependencies.cache.synchronization

    fun requireFreshRead(): NativeReadRequirement? = runtime.initializationOwner.currentScope()?.let(::requireFreshRead)

    fun requireFreshRead(expected: NativeReadScope): NativeReadRequirement? {
        if (runtime.initializationOwner.currentScope() != expected) return null
        val requirement = NativeReadRequirement(expected)
        pendingRequirements.update { it + requirement }
        return if (runtime.initializationOwner.currentScope() == expected) requirement else {
            pendingRequirements.update { it - requirement }
            requirement.retire(NativeReadFrame(publishedAttempt, expected, emptySet(), NativeReadOutcome.RETIRED, null))
            null
        }
    }

    internal fun beforeForeground(scope: NativeReadScope?) { runtime.privacy.beforeForeground(scope) }
    internal fun beforeBackground(scope: NativeReadScope?) { runtime.privacy.beforeBackground(scope) }
    internal fun beforeKey(owner: NativeOwnerToken) {
        val closed = coordination.closeOwner(owner)
        try { runtime.privacy.beforeKey(owner) } finally { coordination.wakeClosed(closed) }
    }

    internal fun reserveRead(expected: NativeReadScope?): NativeReadTurn? {
        if (expected != null && runtime.initializationOwner.currentScope() != expected) return null
        val turn = coordination.reserveRead(expected)
        return if (reservationCurrent(turn)) turn else {
            coordination.release(turn, false)
            null
        }
    }
    internal fun reservationCurrent(turn: NativeReadTurn): Boolean = !turn.entry && coordination.reserved(turn) &&
        (turn.scope == null || runtime.initializationOwner.currentScope() == turn.scope)
    internal suspend fun awaitRead(turn: NativeReadTurn): NativeReadTurn? {
        val acquired = coordination.await(turn) ?: return null
        return if (reservationCurrent(acquired)) acquired else {
            coordination.release(acquired, false)
            null
        }
    }
    internal fun startRead(turn: NativeReadTurn): Boolean = reservationCurrent(turn) && coordination.start(turn)
    internal suspend fun beginRead(expected: NativeReadScope?): NativeReadTurn? {
        val turn = reserveRead(expected) ?: return null
        val acquired = awaitRead(turn)
        return if (acquired == null) null else if (startRead(acquired)) acquired else {
            coordination.release(acquired, false)
            null
        }
    }
    internal fun finishRead(turn: NativeReadTurn, successful: Boolean) = coordination.release(turn, successful)
    internal fun ownsRead(turn: NativeReadTurn): Boolean = !turn.entry && coordination.started(turn) &&
        (turn.scope == null || runtime.initializationOwner.currentScope() == turn.scope)

    internal fun registerDecision(scope: NativeReadScope): NativeDecisionRead? {
        if (runtime.initializationOwner.currentScope() != scope) return null
        // Only metadata is reserved before journal claim; no demand is published yet.
        return coordination.watch(NativeReadRequirement(scope), frames.value?.attempt)
    }
    internal fun demandDecision(read: NativeDecisionRead): Boolean {
        if (!decisionCurrent(read)) return false
        pendingRequirements.update { it + read.target }
        return if (decisionCurrent(read)) true else {
            releaseDecision(read)
            false
        }
    }
    internal fun releaseDecision(read: NativeDecisionRead) {
        coordination.unwatch(read)
        pendingRequirements.update { it - read.target }
        read.target.retire(NativeReadFrame(publishedAttempt, read.scope, emptySet(), NativeReadOutcome.RETIRED, null))
    }
    private fun decisionCurrent(read: NativeDecisionRead): Boolean =
        runtime.initializationOwner.currentScope() == read.scope && !coordination.failed(read)
    internal suspend fun awaitDecisionReads(read: NativeDecisionRead): Boolean =
        decisionCurrent(read) && coordination.awaitObservedReads(read) && decisionCurrent(read)
    internal fun decisionNeedsRead(read: NativeDecisionRead): Boolean = coordination.overlapped(read) &&
        frames.value?.let { liveboardRequirementResolved(this, read.scope, read.target, it) } != true
    internal suspend fun awaitDecisionProof(read: NativeDecisionRead): Boolean {
        while (decisionCurrent(read)) {
            val changed = coordination.changed()
            if (frames.value?.let { liveboardRequirementResolved(this, read.scope, read.target, it) } == true
                ) return true
            if (read.target.completion.isCompleted) return false
            select<Unit> { read.target.completion.onAwait { }; changed.onAwait { } }
        }
        return false
    }
    internal suspend fun beginDecision(read: NativeDecisionRead): NativeReadTurn? {
        if (!decisionCurrent(read)) return null
        val reserved = coordination.reserveDecision(read) ?: return null
        val turn = coordination.await(reserved) ?: return null
        val frame = frames.value
        val proof = if (coordination.overlapped(read)) frame?.let {
            liveboardRequirementResolved(this, read.scope, read.target, it)
        } == true else frame?.attempt == read.attempt && frame?.outcome == NativeReadOutcome.SUCCESS
        if (decisionCurrent(read) && coordination.owns(turn) && proof) return turn
        coordination.release(turn, false)
        return null
    }
    internal fun finishDecision(turn: NativeReadTurn) = coordination.release(turn, true)

    fun invalidate() = dependencies.cache.revokeCanonical()

    suspend fun read(): NativeReadFrame {
        return when (val call = readAttempt()) {
            is NativeReadCall.Value -> call.frame
            is NativeReadCall.Failure -> throw call.cause
        }
    }

    /** Legacy consumption is an actual current attempt, not a claim of canonical proof. */
    fun mayConsumeActualRead(call: NativeReadCall): Boolean =
        call.frame.outcome != NativeReadOutcome.RETIRED && call.frame.scope != null &&
            call.frame.scope == runtime.initializationOwner.currentScope() &&
            call.frame.attempt == publishedAttempt

    suspend fun readAttempt(expected: NativeReadScope? = runtime.initializationOwner.currentScope()): NativeReadCall =
        readAttempt(expected, null)

    internal suspend fun readAttempt(expected: NativeReadScope?, inherited: NativeReadTurn?): NativeReadCall {
        val turn = inherited ?: beginRead(expected)
        if (turn == null || turn.scope != expected || !ownsRead(turn)) return NativeReadCall.Value(NativeReadFrame(
            publishedAttempt, expected, emptySet(), NativeReadOutcome.RETIRED, null))
        var successful = false
        return try {
            readOwnedAttempt(expected).also { successful = it.frame.outcome == NativeReadOutcome.SUCCESS }
        } finally {
            if (inherited == null) finishRead(turn, successful)
        }
    }

    private suspend fun readOwnedAttempt(expected: NativeReadScope?): NativeReadCall {
        var enteredLease: NativeInvocationLease? = null
        val call = try {
            physicalReadMutex.withLock { readPhysicalAttempt(expected) { enteredLease = it } }
        } finally {
            // Draining can resume Reset inline, so release only after the physical mutex too.
            enteredLease?.release()
        }
        // Observers may Reset and await native work; no publication owns the physical fence.
        val frame = call.frame
        withContext(runtime.environment.ui) {
            val scope = frame.scope
            if (frame.outcome != NativeReadOutcome.RETIRED && scope != null &&
                scope == runtime.initializationOwner.currentScope() && frame.attempt > publishedAttempt) {
                // Privacy and observers own no native/physical/lifecycle fence.
                runtime.privacy.beforeCanonical(frame)
                if (scope != runtime.initializationOwner.currentScope() || frame.attempt <= publishedAttempt
                    ) return@withContext
                publishedAttempt = frame.attempt
                if (call is NativeReadCall.Failure) invalidate()
                else dependencies.cache.publishCanonical(frame)
                if (frame.outcome == NativeReadOutcome.SUCCESS) {
                    frame.coveredRequirements.forEach { target ->
                        if (scope != runtime.initializationOwner.currentScope()) retireScope(scope)
                        else if (frames.value === frame) {
                            target.fulfill(frame)
                            pendingRequirements.update { it - target }
                        }
                    }
                }
            }
        }
        return call
    }

    suspend fun retireScope(scope: NativeReadScope) = withContext(runtime.environment.ui) {
        val closed = coordination.closeScope(scope)
        try {
            val retired = removeScope(scope)
            if (frames.value?.scope == scope) invalidate()
            // Completing one target may synchronously Reset again; none owns a native fence.
            retired.forEach { target ->
                target.retire(NativeReadFrame(publishedAttempt, scope, emptySet(), NativeReadOutcome.RETIRED, null))
            }
        } finally { coordination.wakeClosed(closed) }
    }

    private fun removeScope(scope: NativeReadScope): Set<NativeReadRequirement> {
        while (true) {
            val old = pendingRequirements.value
            val matching = old.filter { it.scope == scope }.toSet()
            if (pendingRequirements.compareAndSet(old, old - matching)) return matching
        }
    }

    private suspend fun readPhysicalAttempt(
        scope: NativeReadScope?,
        acquired: (NativeInvocationLease) -> Unit,
    ): NativeReadCall {
        val attempt = ++nextAttempt
        fun retired() = NativeReadCall.Value(NativeReadFrame(attempt, scope, emptySet(), NativeReadOutcome.RETIRED,
            null))
        if (scope == null) return retired()
        return when (val entered = runtime.initializationOwner.invokeRead(scope, acquired) {
            val covered = pendingRequirements.value.filter { it.scope == scope }.toSet()
            val state = AppStateModel.fromJson(
                dependencies.core.getAppState(), dependencies.logger, dependencies.logFormatter,
            )
            NativeReadFrame(
                attempt, scope, if (state.success) covered else emptySet(),
                if (state.success) NativeReadOutcome.SUCCESS else NativeReadOutcome.FAILED,
                state,
            )
        }) {
            is NativeInvocation.Value -> NativeReadCall.Value(entered.value)
            is NativeInvocation.Failure -> NativeReadCall.Failure(
                NativeReadFrame(attempt, scope, emptySet(), NativeReadOutcome.FAILED, null), entered.cause,
            )
            NativeInvocation.Retired -> retired()
        }
    }
}
