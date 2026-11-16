package core.metaSecretCore.liveboard

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.liveboard.LiveboardOperationIdentity
import models.apiModels.liveboard.LiveboardSnapshot

/** Closed typed commands share fixed admission and private truth, never transient disclosure. */
class LiveboardActionController internal constructor(
    private val session: LiveboardSession,
    private val dependencies: LiveboardSessionDependencies,
) {
    private val auth = LiveboardAuthGate(dependencies.client.auth, dependencies.environment) { session.sensitive
        .conceal(false, closeCreate = false) }
    private val issued = MutableStateFlow<Map<String, Any>>(emptyMap())
    private val admissions = MutableStateFlow<Map<Any, LiveboardEntryAdmission>>(emptyMap())
    private val journal = LiveboardCommandJournal(session.scope, dependencies.reads, ::release)
    private val retryEvidence = LiveboardDecisionRetryEvidence(session, dependencies.reads)
    // Diagnostic delivery identities only; results/payloads and native ownership stay in the same journal.
    private val consumedErrors = MutableStateFlow<Set<Any>>(emptySet())
    internal val hasUnresolvedCreate: Boolean get() = issued.value.values.filterIsInstance<LiveboardCreateIntent>()
        .any {
        admissions.value[it]?.isAdmitted == true || journal.hasOutcome(it) || journal.isPending(it)
    }

    fun presentation(): List<LiveboardActionPresentation> = issued.value.values.mapNotNull { intent ->
        val target = when (intent) {
            is LiveboardCreateIntent -> LiveboardActionTarget.Create
            is LiveboardRecoverIntent -> LiveboardActionTarget.Recover(intent.secretId)
            is LiveboardDeleteIntent -> LiveboardActionTarget.Delete(intent.secretId)
            is LiveboardDecisionIntent -> LiveboardActionTarget.Decision(intent.identity)
            is LiveboardContinueIntent -> LiveboardActionTarget.Continue(intent.original.targetDeviceId)
            else -> return@mapNotNull null
        }
        val returned = journal.retained<Any>(intent)?.result
        LiveboardActionPresentation(target, if (retryEvidence.recognized(returned)) LiveboardActionPhase.Unknown
            else liveboardActionPhase(returned, journal.isPending(intent)),
            liveboardActionRequest(returned))
    } + session.removePresentation()

    internal fun publishEnteredFailures() {
        if (!session.ownsNativeScope()) return
        val failures = issued.value.values.filter { intent ->
            val result = journal.retained<Any>(intent)?.result
            result is NativeResult.Unavailable || result === NativeResult.Uncertain || retryEvidence.recognized(result)
        }.toSet() + session.removeFailureIdentities()
        if (failures.isEmpty()) return
        while (true) {
            val old = consumedErrors.value
            val claimed = failures - old
            if (claimed.isEmpty()) return
            if (!consumedErrors.compareAndSet(old, old + claimed)) continue
            // Claim before callbacks; manual dismissal/remount cannot replay an old error.
            val previousError = session.inbox.errorPublicationStamp()
            if (!dependencies.client.privacy.commandError(session, dependencies.client.strings.errorInternal())) {
                consumedErrors.update { it - claimed }
            } else session.inbox.publishedErrorEvent(previousError)?.let { event ->
                publishRetryFailureWave(claimed, journal, retryEvidence, { consumedErrors.value }, event)
            }
            return
        }
    }

    fun createPresentation(): LiveboardCreatePresentation {
        val current = presentation().firstOrNull { it.target == LiveboardActionTarget.Create }?.phase
        val phase = when (current) {
            LiveboardActionPhase.Busy -> LiveboardCreatePhase.Busy
            LiveboardActionPhase.Submitted -> LiveboardCreatePhase.Submitted
            LiveboardActionPhase.Unknown -> LiveboardCreatePhase.Unknown
            else -> LiveboardCreatePhase.Idle
        }
        val allowed = session.currentMember()?.permissions?.canCreateSecret == true
        return LiveboardCreatePresentation(phase, allowed, allowed && session.isReadyForEntry() && !hasUnresolvedCreate)
    }

    fun createIntent(): LiveboardCreateIntent? {
        if (session.currentMember()?.permissions?.canCreateSecret != true || hasUnresolvedCreate) return null
        val intent = session.sensitive.selectDraft() ?: return null
        val admission = session.sensitive.selectedAdmission(intent) ?: return null
        return select("create:${intent.name}", intent, admission)
    }
    suspend fun create(intent: LiveboardCreateIntent): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        perform(intent, true) { dependencies.checked.createSelected(session.scope, it) }

    fun recoverIntent(secretId: String): LiveboardRecoverIntent? {
        val secret = session.currentMember()?.secrets?.firstOrNull { it.identity.passId == secretId } ?: return null
        return if (secret.primary.action != "recover" || secret.primary.readiness != "ready") null
        else select("recover:$secretId", LiveboardRecoverIntent(session.generation, secretId),
            admission(LiveboardEntrySubject.Secret(secret, false)))
    }
    suspend fun recover(intent: LiveboardRecoverIntent): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        recoverObserved(intent, null)
    internal suspend fun recoverObserved(intent: LiveboardRecoverIntent, trace: LiveboardDecisionRejectionTrace?
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        perform(intent, false, trace) { dependencies.checked.recoverChecked(session.scope, intent.secretId, it) }

    fun deleteIntent(secretId: String): LiveboardDeleteIntent? {
        val secret = session.currentMember()?.secrets?.firstOrNull { it.identity.passId == secretId && it.canDelete
            } ?: return null
        return select("delete:$secretId", LiveboardDeleteIntent(session.generation, secretId),
            admission(LiveboardEntrySubject.Secret(secret, true)))
    }
    suspend fun delete(intent: LiveboardDeleteIntent): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        perform(intent, true) { gate ->
            withContext(dependencies.environment.ui) { session.sensitive.hide(intent.secretId) }
            if (gate.selection?.isCurrent() != true) unavailable()
            else dependencies.checked.deleteSecretChecked(session.scope, intent.secretId, gate)
        }

    fun decisionIntent(identity: LiveboardOperationIdentity, accepted: Boolean): LiveboardDecisionIntent? {
        val operation = session.currentMember()?.operations?.firstOrNull { it.identity == identity } ?: return null
        if (!(if (accepted) operation.permissions.canApprove else operation.permissions.canDecline)) return null
        return select("decision:${identity.kind}:${identity.requestId}", LiveboardDecisionIntent(session.generation,
            identity, accepted, session.sensitive.decisionStamp()),
            admission(LiveboardEntrySubject.Decision(identity, accepted)))
    }
    suspend fun decide(intent: LiveboardDecisionIntent): NativeLiveboardOutcome<LiveboardDecisionAttempt> =
        decideObserved(intent, null)
    internal suspend fun decideObserved(
        intent: LiveboardDecisionIntent,
        trace: LiveboardDecisionRejectionTrace?,
    ): NativeLiveboardOutcome<LiveboardDecisionAttempt> = performDecision(intent, trace) { gate ->
        val request = JsonObject(mapOf("requestId" to JsonPrimitive(intent.identity.requestId),
            "accepted" to JsonPrimitive(intent.accepted))).toString()
        when (intent.identity.kind) {
            LiveboardKind.Join -> checkedDecision(dependencies.checked.decideJoinRequest(session.scope, request, gate))
            LiveboardKind.RemoveDevice -> checkedDecision(dependencies.checked.decideRemoveMember(session.scope,
                request, gate))
            LiveboardKind.DeleteSecret, LiveboardKind.Recover -> returnedDecision(intent,
                dependencies.checked.decideSelectedObserved(session.scope, intent, request, gate, trace))
            else -> unavailable(NativeFailure.InvalidDecisionInput)
        }
    }

    suspend fun remove(intent: LiveboardRemoveIntent): NativeLiveboardOutcome<LiveboardProducerReceipt> {
        session.retainedProducer(intent)?.let { return it }
        val device = session.currentMember()?.devices?.firstOrNull { it.deviceId == intent.targetDeviceId && it
            .canRemove && !it.current }
            ?: return unavailable()
        val generation = session.removeSelectionGeneration(intent) ?: return unavailable()
        // SAME old command reserves issuing ownership before its worker opens the OS prompt.
        return session.removeDeviceAuthenticated(intent,
            LiveboardEntrySelection(session, generation, LiveboardEntrySubject.Device(device)))
    }

    suspend fun reconcile(intent: LiveboardProducerIntent): NativeLiveboardOutcome<LiveboardProducerReceipt> {
        val original = producer(intent) ?: return unavailable(NativeFailure.InvalidProducerReference)
        val reference = reference(original) ?: return unavailable(NativeFailure.InvalidProducerReference)
        if (session.currentMember() == null) return unavailable()
        val observed = dependencies.checked.reconcileProducerReceipt(session.scope, reference)
        val value = (observed as? NativeLiveboardOutcome.Entered)?.result as? NativeResult.Success
        if (value != null && value.value.state.readiness in listOf("done", "settled") &&
            value.value.state.canonicalState in listOf("applied", "declined")) {
            // Strict adapter correlation already checked the SAME immutable reference.
            when (intent) {
                is LiveboardRemoveIntent -> session.releaseResolvedRemove(intent)
                is LiveboardDeleteIntent -> release(intent)
            }
        }
        return observed
    }
    fun continueRemoveIntent(original: LiveboardRemoveIntent): LiveboardContinueIntent? {
        if (session.currentMember() == null) return null
        val receipt = session.retainedProducer(original) ?: return null
        val reference = reference(receipt) ?: return null
        return select("continue:${original.targetDeviceId}", LiveboardContinueIntent(session.generation, original,
            reference),
            admission(LiveboardEntrySubject.Continue(original)))
    }
    suspend fun continueRemove(intent: LiveboardContinueIntent): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        perform(intent, true) { dependencies.checked.continueLocalRemoveChecked(session.scope, intent.reference, it) }

    private fun admission(subject: LiveboardEntrySubject) = LiveboardEntryAdmission(session.scope,
        LiveboardEntrySelection(session, session.sensitive.generation(), subject))
    private fun <T : Any> select(key: String, intent: T, gate: LiveboardEntryAdmission): T? {
        if (gate.selection?.isCurrent() != true) { gate.cancelQueued(); return null }
        while (true) {
            val old = issued.value
            if (key in old) { gate.cancelQueued(); return null }
            if (issued.compareAndSet(old, old + (key to intent))) {
                admissions.update { it + (intent to gate) }
                return intent
            }
        }
    }
    private suspend fun <T> perform(
        intent: Any,
        needsAuth: Boolean,
        trace: LiveboardDecisionRejectionTrace? = null,
        native: suspend (LiveboardEntryAdmission) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> {
        journal.retained<T>(intent)?.let { return it }
        val gate = admissions.value[intent] ?: run {
            trace?.direct(LiveboardDecisionRejectionSource.ACTION_ADMISSION_MISSING)
            return unavailable()
        }
        if (session.isDisposed || issued.value.values.none { it === intent }) {
            trace?.direct(LiveboardDecisionRejectionSource.ACTION_SESSION_OR_INTENT_RETIRED)
            return unavailable()
        }
        return journal.issueObserved(intent, gate, trace) { admission ->
            val cancelled = admission.isCancelled
            if (cancelled || admission.selection?.isCurrentObserved(trace) != true) {
                trace.noteWorkerRejection(cancelled)
                unavailable()
            } else if (needsAuth && !auth.confirm()) {
                trace?.note(LiveboardDecisionRejectionSource.AUTH_DECLINED)
                unavailable()
            } else if (admission.selection?.isCurrentObserved(trace) != true) {
                trace?.note(LiveboardDecisionRejectionSource.POST_AUTH_SELECTION_INVALID)
                unavailable()
            } else native(admission)
        }
    }
    private suspend fun <T> performDecision(
        intent: LiveboardDecisionIntent,
        trace: LiveboardDecisionRejectionTrace?,
        native: suspend (LiveboardEntryAdmission) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> {
        journal.retained<T>(intent)?.let { return it }
        val original = admissions.value[intent] ?: run {
            trace?.direct(LiveboardDecisionRejectionSource.ACTION_ADMISSION_MISSING)
            return unavailable()
        }
        if (session.isDisposed || issued.value.values.none { it === intent }) {
            trace?.direct(LiveboardDecisionRejectionSource.ACTION_SESSION_OR_INTENT_RETIRED)
            return unavailable()
        }
        return journal.issueDecision(intent, original, trace) { command ->
            val read = command.read
            if (read == null || !preparingDecisionCurrent(intent, command)) {
                trace?.note(LiveboardDecisionRejectionSource.WORKER_CANCELLED_OR_SELECTION_INVALID)
                return@issueDecision unavailable()
            }
            if (!auth.confirm()) {
                trace?.note(LiveboardDecisionRejectionSource.AUTH_DECLINED)
                return@issueDecision unavailable()
            }
            if (!preparingDecisionCurrent(intent, command) || !awaitDecisionRead(read)
                ) return@issueDecision unavailable()
            enterDecision(intent, command, original, native)
        }
    }
    private fun preparingDecisionCurrent(intent: LiveboardDecisionIntent, command: LiveboardCommandEntry): Boolean =
        !command.isCancelled && session.sensitive.isDecisionStamp(intent.hardStamp) &&
            issued.value.values.any { it === intent }
    private suspend fun awaitDecisionRead(read: NativeDecisionRead): Boolean {
        if (!dependencies.reads.awaitDecisionReads(read)) return false
        return if (!dependencies.reads.decisionNeedsRead(read)) true else {
            // An already-entered physical read may have missed this exact demand.
            dependencies.client.socket.refreshAppState(read.target)
            dependencies.reads.awaitDecisionProof(read)
        }
    }
    private suspend fun <T> enterDecision(
        intent: LiveboardDecisionIntent,
        command: LiveboardCommandEntry,
        original: LiveboardEntryAdmission,
        native: suspend (LiveboardEntryAdmission) -> NativeLiveboardOutcome<T>,
    ): NativeLiveboardOutcome<T> {
        val read = command.read ?: return unavailable()
        val turn = dependencies.reads.beginDecision(read) ?: return unavailable()
        try {
            if (!preparingDecisionCurrent(intent, command)) return unavailable()
            val fresh = admission(LiveboardEntrySubject.Decision(intent.identity, intent.accepted))
            if (fresh.selection?.isCurrent() != true) {
                fresh.cancelQueued()
                return unavailable()
            }
            if (!command.bind(fresh)) return unavailable()
            admissions.update { old -> if (old[intent] === original) old + (intent to fresh) else old }
            if (command.isCancelled) return unavailable()
            return native(fresh)
        } finally {
            // The existing facade has unwound and released its actual native lease first.
            dependencies.reads.finishDecision(turn)
        }
    }

    private fun release(intent: Any) {
        issued.update { old -> old.filterValues { it !== intent } }
        admissions.update { it - intent }
        if (intent is LiveboardCreateIntent) session.sensitive.releaseDraft(intent)
    }
    internal fun revokeQueued(technical: Boolean = false) {
        journal.revokeQueued(technical)
        admissions.value.values.forEach { it.cancelQueued() }
    }
    internal fun settleRevoked(technical: Boolean = false) {
        journal.settleRevoked(technical)
        admissions.value.forEach { (intent, gate) ->
            if (gate.isCancelled && !journal.hasOutcome(intent) && !journal.isPending(intent)) release(intent)
        }
    }
    internal fun revokeDecisions() {
        journal.revokeDecisions()
        admissions.value.filterKeys { it is LiveboardDecisionIntent }.values.forEach { it.cancelQueued() }
    }
    internal fun settleDecisions() {
        journal.settleDecisions()
        admissions.value.filterKeys { it is LiveboardDecisionIntent }.forEach { (intent, gate) ->
            if (gate.isCancelled && !journal.hasOutcome(intent) && !journal.isPending(intent)) release(intent)
        }
    }
    suspend fun awaitSettled() { journal.awaitSettled() }

    internal fun captureDecisionErrors(event: Any): (NativeReadRequirement) -> Unit {
        val captured = issued.value.values.filterIsInstance<LiveboardDecisionIntent>().mapNotNull { intent ->
            val outcome = journal.retained<Any>(intent) ?: return@mapNotNull null
            if (intent in consumedErrors.value && journal.isSettledOutcome(intent, outcome) &&
                retryEvidence.publishedMatches(intent, outcome, event)) intent to outcome else null
        }
        return { required -> captured.forEach { (intent, outcome) ->
            if (intent in consumedErrors.value && journal.isSettledOutcome(intent, outcome))
                retryEvidence.acknowledge(intent, outcome, required, event)
        } }
    }

    internal fun observe(frame: NativeReadFrame, member: LiveboardSnapshot) {
        issued.value.values.forEach { intent ->
            if (!journal.isResolvedBy(intent, frame)) return@forEach
            val retained = journal.retained<Any>(intent) ?: return@forEach
            if (retryEvidence.recognized(retained.result)) {
                if (intent is LiveboardDecisionIntent && journal.isSettledOutcome(intent, retained) &&
                    retryEvidence.resolved(intent, retained, frame, member)) release(intent)
                return@forEach
            }
            val outcome = retained.result as? NativeResult.Success ?: return@forEach
            val terminal = when (val receipt = outcome.value) {
                is LiveboardCheckedReceipt -> when (intent) {
                    is LiveboardCreateIntent -> receipt.identity.secretId?.let { id ->
                        member.secrets.any { it.identity.passId == id && it.primary.readiness == "ready" }
                    } == true
                    is LiveboardRecoverIntent -> LiveboardRecoverCompletion.terminal(
                        LiveboardRecoverProof(session, frame, member), intent, admissions.value[intent], retained)
                    else -> false
                }
                is LiveboardProducerReceipt -> member.operations.any { operation ->
                    operation.identity.kind == receipt.identity.kind && operation.identity.requestId == receipt
                        .identity.requestId &&
                        operation.identity.requesterId == member.identity.currentDeviceId &&
                        operation.identity.secretId == receipt.identity.secretId && operation.identity
                            .targetDeviceId == receipt.identity.targetDeviceId &&
                        operation.status.readiness in listOf("done", "settled") && operation.status
                            .resolutionSource in listOf("canonical", "verified")
                }
                is LiveboardDecisionAttempt -> intent is LiveboardDecisionIntent && member.operations.any {
                    it.identity == intent.identity && it.status.readiness in listOf("done", "settled") &&
                        it.status.resolutionSource in listOf("canonical", "verified")
                }
                else -> false
            }
            if (terminal) {
                release(intent)
                if (intent is LiveboardCreateIntent) session.sensitive.completedCreate(intent)
            }
        }
    }
    private fun producer(intent: LiveboardProducerIntent): NativeLiveboardOutcome
        .Entered<LiveboardProducerReceipt>? = when (intent) {
        is LiveboardRemoveIntent -> session.retainedProducer(intent)
        is LiveboardDeleteIntent -> journal.retained(intent)
    }
    private fun reference(outcome: NativeLiveboardOutcome.Entered<LiveboardProducerReceipt>
        ): LiveboardProducerReference? {
        val receipt = (outcome.result as? NativeResult.Success)?.value ?: return null
        return try { LiveboardProducerReference.fromReceipt(receipt) } catch (_: Exception) { null }
    }
    private fun checkedDecision(outcome: NativeLiveboardOutcome<LiveboardCheckedReceipt>
        ): NativeLiveboardOutcome<LiveboardDecisionAttempt> = when (outcome) {
        is NativeLiveboardOutcome.NotEntered -> outcome
        is NativeLiveboardOutcome.Entered -> NativeLiveboardOutcome.Entered(outcome.scope, when (val result =
            outcome.result) {
            is NativeResult.Success -> NativeResult.Success(LiveboardDecisionAttempt.Checked(result.value))
            is NativeResult.Unavailable -> result
            NativeResult.Uncertain -> NativeResult.Uncertain
        })
    }
    private fun returnedDecision(intent: LiveboardDecisionIntent, invocation: NativeInvocation<String>
        ): NativeLiveboardOutcome<LiveboardDecisionAttempt> = retryEvidence.decode(intent, invocation)
    private fun unavailable(code: NativeFailure = NativeFailure.OperationUnavailable) = NativeLiveboardOutcome
        .NotEntered(session.scope, code)
}
