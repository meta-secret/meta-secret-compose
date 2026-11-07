package core.metaSecretCore.liveboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.liveboard.LiveboardSnapshot
import models.apiModels.liveboard.LiveboardSnapshotIdentity
import models.apiModels.liveboard.LiveboardStateDecoder

/** Reuses one keyed Session only after validating genuine current physical Member proof. */
class LiveboardSessionFactory(
    checked: NativeLiveboardOperations,
    reads: NativeCanonicalReadOwner,
    operations: NativeOperationPort,
    environment: NativeReadEnvironment,
    client: LiveboardClientPorts,
) {
    private val dependencies = LiveboardSessionDependencies(checked, reads, operations, environment, client)
    private val active = MutableStateFlow<LiveboardSession?>(null)

    fun open(frame: NativeReadFrame): LiveboardSession? {
        val scope = frame.scope ?: return null
        if (frame.outcome != NativeReadOutcome.SUCCESS || !current(frame, scope)) return null
        val snapshot = frame.state?.let(LiveboardStateDecoder::decode) ?: return null
        while (current(frame, scope)) {
            val previous = active.value
            if (previous != null && previous.matches(scope, snapshot.identity)) {
                return previous.takeIf { !it.isDisposed && current(frame, scope) }
            }
            val session = LiveboardSession(scope, snapshot.identity, dependencies)
            if (active.compareAndSet(previous, session)) {
                if (current(frame, scope) && dependencies.client.privacy.attach(session, frame)) {
                    session.inbox.start()
                    if (current(frame, scope)) {
                        previous?.dispose()
                        return session.takeIf { current(frame, scope) } ?: reject(session)
                    }
                }
                return reject(session)
            }
            session.dispose()
        }
        return null
    }

    private fun reject(candidate: LiveboardSession): LiveboardSession? {
        active.compareAndSet(candidate, null)
        candidate.dispose()
        return null
    }

    private fun current(frame: NativeReadFrame, scope: NativeReadScope): Boolean =
        dependencies.reads.frames.value === frame && dependencies.operations.isCurrent(scope)
}

/** Owns admitted producer truth independently of a view awaiter; presentation is separately fenced. */
class LiveboardSession internal constructor(
    internal val scope: NativeReadScope,
    private val identity: LiveboardSnapshotIdentity,
    private val dependencies: LiveboardSessionDependencies,
) {
    internal val generation = Any()
    private val disposed = MutableStateFlow(false)
    internal val isDisposed: Boolean get() = disposed.value
    val sensitive = LiveboardSensitiveController(this, dependencies)
    val actions = LiveboardActionController(this, dependencies)
    val inbox = LiveboardInboxController(this, dependencies)
    private class RemoveSelection(val intent: LiveboardRemoveIntent, val privacy: Any)
    private class RemoveRecords(
        val pending: Map<LiveboardRemoveIntent, LiveboardCommandAdmission> = emptyMap(),
        val retained: Map<LiveboardRemoveIntent, NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>> = emptyMap(),
        val selected: Map<String, RemoveSelection> = emptyMap(),
    )
    private val records = MutableStateFlow(RemoveRecords())
    private val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val removeTargets = MutableStateFlow<Map<LiveboardRemoveIntent, NativeReadRequirement>>(emptyMap())
    private val mutablePresentation = MutableStateFlow<LiveboardProducerPresentation?>(null)
    val presentation: StateFlow<LiveboardProducerPresentation?> = mutablePresentation.asStateFlow()

    internal fun matches(expected: NativeReadScope, member: LiveboardSnapshotIdentity): Boolean =
        scope == expected && identity == member

    fun removeDeviceIntent(targetDeviceId: String): LiveboardRemoveIntent? {
        if (!isReadyForEntry()) return null
        if (currentMember()?.devices?.any { it.deviceId == targetDeviceId && it.canRemove && !it.current } != true
            ) return null
        while (true) {
            val previous = records.value
            if (targetDeviceId in previous.selected) return null
            val intent = LiveboardRemoveIntent(generation, targetDeviceId)
            if (records.compareAndSet(previous, RemoveRecords(previous.pending, previous.retained,
                previous.selected + (targetDeviceId to RemoveSelection(intent, sensitive.generation()))))) return intent
        }
    }

    internal fun retainedProducer(intent: LiveboardRemoveIntent): NativeLiveboardOutcome
        .Entered<LiveboardProducerReceipt>? =
        records.value.retained[intent].takeIf { intent.generation === generation }

    internal fun removePresentation(): List<LiveboardActionPresentation> {
        val snapshot = records.value
        return snapshot.selected.values.map { selected ->
            val intent = selected.intent
            val returned = snapshot.retained[intent]?.result
            LiveboardActionPresentation(LiveboardActionTarget.Remove(intent.targetDeviceId),
                liveboardActionPhase(returned, intent in snapshot.pending), liveboardActionRequest(returned))
        }
    }

    /** Readonly diagnostic identities from current selections, never historical released truth. */
    internal fun removeFailureIdentities(): List<LiveboardRemoveIntent> {
        val snapshot = records.value
        return snapshot.selected.values.mapNotNull { selected ->
            val result = snapshot.retained[selected.intent]?.result
            selected.intent.takeIf { result is NativeResult.Unavailable || result === NativeResult.Uncertain }
        }
    }

    suspend fun removeDevice(intent: LiveboardRemoveIntent): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        issueRemove(intent, null)

    internal fun removeSelectionGeneration(intent: LiveboardRemoveIntent): Any? =
        records.value.selected[intent.targetDeviceId]?.takeIf { it.intent === intent }?.privacy

    internal suspend fun removeDeviceAuthenticated(intent: LiveboardRemoveIntent, selection: LiveboardEntrySelection
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        issueRemove(intent, selection)

    private suspend fun issueRemove(intent: LiveboardRemoveIntent, selection: LiveboardEntrySelection?
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> {
        while (true) {
            val previous = records.value
            previous.retained[intent]?.let { return it }
            if (previous.pending[intent] != null) return unavailable()
            if (!isReadyForEntry()) return unavailable()
            if (intent.generation !== generation || previous.selected[intent.targetDeviceId]?.intent !== intent
                ) return unavailable(NativeFailure.InvalidInput)
            val device = currentMember()?.devices?.firstOrNull { it.deviceId == intent.targetDeviceId && it
                .canRemove && !it.current }
            if (device == null) {
                release(intent)
                return unavailable(NativeFailure.InvalidInput)
            }
            val publicationGeneration = previous.selected.getValue(intent.targetDeviceId).privacy
            val entrySelection = selection ?: LiveboardEntrySelection(this, publicationGeneration,
                LiveboardEntrySubject.Device(device))
            val command = LiveboardCommandAdmission(scope, intent, entrySelection)
            if (records.compareAndSet(previous, RemoveRecords(previous.pending + (intent to command), previous
                .retained, previous.selected))) {
                val job = workers.launch(start = CoroutineStart.LAZY) { runCommand(command, publicationGeneration,
                    selection != null) }
                command.job = job
                job.invokeOnCompletion { command.job = null }
                job.start()
                return awaitCommand(command)
            }
        }
    }

    private suspend fun awaitCommand(command: LiveboardCommandAdmission
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        try {
            command.awaitResult()
        } catch (cancelled: CancellationException) {
            cancelCommand(command)
            throw cancelled
        }

    private suspend fun runCommand(command: LiveboardCommandAdmission, publicationGeneration: Any, needsAuth: Boolean) {
        val intent = command.intent
        val result = try {
            val selection = command.entry.selection
            if (command.isCancelled || selection?.isCurrent() == false) unavailable()
            else if (needsAuth && !LiveboardAuthGate(dependencies.client.auth, dependencies.environment) { sensitive
                .conceal(false, closeCreate = false) }.confirm()) unavailable()
            else if (selection?.isCurrent() == false) unavailable()
            else dependencies.checked.removeMemberChecked(scope, intent.targetDeviceId, command)
        } catch (_: Exception) {
            if (command.isAdmitted) NativeLiveboardOutcome.Entered(scope, NativeResult.Uncertain)
            else unavailable()
        }
        // Real native exit and lease release precede private truth and every observable callback.
        try {
            if (result is NativeLiveboardOutcome.Entered) {
                records.update { RemoveRecords(it.pending, it.retained + (intent to result), it.selected) }
                dependencies.reads.requireFreshRead(scope)?.let { target -> removeTargets.update { it + (
                    intent to target) } }
            } else { command.cancelQueued(); release(intent) }
        } finally {
            command.settleNative()
        }
        try {
            withContext(dependencies.environment.ui) {
                if (ownsNativeScope() && currentMember() != null && sensitive.isCurrent(publicationGeneration) &&
                    command.entry.selection?.isCurrent() != false && result is NativeLiveboardOutcome.Entered) {
                    mutablePresentation.value = LiveboardProducerPresentation(intent.targetDeviceId, result.result)
                }
            }
        } finally {
            command.complete(result)
            records.update { old -> if (old.pending[intent] === command) RemoveRecords(old.pending - intent, old
                .retained, old.selected) else old }
        }
    }

    /** Actual issued work settlement is separate from readiness or native success. */
    internal suspend fun awaitSettled() {
        actions.awaitSettled()
        while (true) {
            val pending = records.value.pending.values.toList()
            if (pending.isEmpty()) return
            pending.forEach { it.awaitNativeSettled() }
            // Settled native work can still own a queued UI delivery; do not await that publication.
            if (records.value.pending.values.all { it in pending }) return
        }
    }

    internal fun currentMember(): LiveboardSnapshot? = currentMemberObserved(null)

    internal fun currentMemberObserved(trace: LiveboardDecisionRejectionTrace?): LiveboardSnapshot? {
        if (isDisposed.observedCurrentnessRejection(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_DISPOSED) ||
            (!dependencies.operations.isCurrent(scope)).observedCurrentnessRejection(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_OWNER_INVALID)) return null
        val frame = dependencies.reads.frames.value.observedCurrentnessValue(trace,
            LiveboardDecisionRejectionSource.POST_CAS_MEMBER_FRAME_ABSENT) ?: return null
        if ((frame.scope != scope).observedCurrentnessRejection(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_SCOPE_MISMATCH) ||
            (frame.outcome != NativeReadOutcome.SUCCESS).observedCurrentnessRejection(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_OUTCOME_NOT_SUCCESS)) return null
        val snapshot = frame.state.observedCurrentnessValue(trace,
            LiveboardDecisionRejectionSource.POST_CAS_MEMBER_STATE_ABSENT)?.let {
            LiveboardStateDecoder.decode(it).observedCurrentnessValue(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_DECODE_UNAVAILABLE)
        } ?: return null
        return snapshot.takeIf {
            (it.identity == identity).observedCurrentness(trace,
                LiveboardDecisionRejectionSource.POST_CAS_MEMBER_IDENTITY_MISMATCH) &&
                (dependencies.reads.frames.value === frame).observedCurrentness(trace,
                    LiveboardDecisionRejectionSource.POST_CAS_MEMBER_FRAME_REPLACED) &&
                dependencies.operations.isCurrent(scope).observedCurrentness(trace,
                    LiveboardDecisionRejectionSource.POST_CAS_MEMBER_OWNER_RECHECK_INVALID)
        }
    }

    /** Disposes observation/transients without cancelling an already admitted native operation. */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        inbox.dispose()
        sensitive.conceal(clearDraft = true)
        dependencies.client.privacy.detach(this)
    }

    internal fun ownsNativeScope(): Boolean = ownsNativeScopeObserved(null)
    internal fun ownsNativeScopeObserved(trace: LiveboardDecisionRejectionTrace?): Boolean =
        (!isDisposed).observedCurrentness(trace, LiveboardDecisionRejectionSource.POST_CAS_INBOX_DISPOSED) &&
            dependencies.operations.isCurrent(scope).observedCurrentness(trace,
                LiveboardDecisionRejectionSource.POST_CAS_INBOX_OWNER_INVALID)

    internal fun isReadyForEntry(): Boolean = ownsNativeScope() && inbox.isReady() &&
        sensitive.isCurrent(sensitive.generation())

    internal fun isFrameCurrent(frame: NativeReadFrame): Boolean = !isDisposed &&
        dependencies.reads.frames.value === frame && frame.scope == scope && dependencies.operations.isCurrent(scope)

    internal fun revokeQueuedForPrivacy(technical: Boolean = false) {
        records.value.pending.values.forEach { it.cancelQueued() }
        records.update { old -> RemoveRecords(old.pending, old.retained, old.selected.filterValues {
            it.intent in old.pending || it.intent in old.retained
        }) }
        actions.revokeQueued(technical)
    }
    internal fun settleQueuedForPrivacy(technical: Boolean = false) {
        records.value.pending.values.forEach { settleCancelled(it) }
        actions.settleRevoked(technical)
    }

    private fun cancelCommand(command: LiveboardCommandAdmission) {
        if (command.cancelQueued()) settleCancelled(command)
    }
    private fun settleCancelled(command: LiveboardCommandAdmission) {
        if (!command.isCancelled) return
        release(command.intent)
        command.job?.cancel()
        command.settleNative()
        command.complete(unavailable())
        records.update { old -> if (old.pending[command.intent] === command) RemoveRecords(old.pending - command
            .intent, old.retained, old.selected) else old }
    }

    internal fun observeCanonical(frame: NativeReadFrame, member: LiveboardSnapshot) {
        if (frame.scope != scope || member.identity != identity) return
        actions.observe(frame, member)
        removeTargets.value.forEach { (intent, target) ->
            val receipt = (records.value.retained[intent]?.result as? NativeResult.Success)?.value ?: return@forEach
            if (!liveboardRequirementResolved(dependencies.reads, scope, target, frame)) return@forEach
            if (member.operations.any { it.identity.kind == LiveboardKind.RemoveDevice &&
                it.identity.requestId == receipt.identity.requestId && it.identity.targetDeviceId == intent
                    .targetDeviceId &&
                it.identity.requesterId == identity.currentDeviceId && it.status.readiness in listOf("done",
                    "settled") &&
                it.status.resolutionSource in listOf("canonical", "verified") }) release(intent)
        }
    }
    internal fun releaseResolvedRemove(intent: LiveboardRemoveIntent) { release(intent) }

    private fun release(intent: LiveboardRemoveIntent) {
        records.update { old ->
            if (old.selected[intent.targetDeviceId]?.intent === intent) RemoveRecords(old.pending, old.retained, old
                .selected - intent.targetDeviceId) else old
        }
    }

    private fun unavailable(code: NativeFailure = NativeFailure.OperationUnavailable
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        NativeLiveboardOutcome.NotEntered(scope, code)
}
