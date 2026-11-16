package ui.liveboard

import core.metaSecretCore.isCurrentDeviceRemovalTransition
import core.metaSecretCore.liveboard.LiveboardCreateCompletion
import core.metaSecretCore.liveboard.LiveboardDraft
import core.metaSecretCore.liveboard.observedRejectionSource
import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.LiveboardShowObservationTicket
import core.metaSecretCore.liveboard.NativeKeyResult
import core.metaSecretCore.liveboard.NativeLiveboardOutcome
import core.metaSecretCore.liveboard.NativeResult
import core.metaSecretCore.liveboard.NativeReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import models.apiModels.AppStateModel
import models.apiModels.liveboard.LiveboardStateDecoder
import models.appInternalModels.SocketRequestModel

/** One view attachment projects the existing keyed Session; it never owns admitted native work. */
class LiveboardRootCoordinator(private val dependencies: LiveboardRootDependencies) {
    private val observers = CoroutineScope(SupervisorJob() + dependencies.environment.ui)
    private val views = CoroutineScope(SupervisorJob() + dependencies.environment.io)
    private val mutableState = MutableStateFlow(LiveboardRootPresentation())
    val state: StateFlow<LiveboardRootPresentation> = mutableState.asStateFlow()
    private var expectedOwner = dependencies.services.reset.capture()
    private var originScope = dependencies.reads.frames.value?.scope
    private var retryReceipt: NativeKeyResult<Boolean>? = null
    private val attachment = dependencies.platform.attach(Any(), ::back)
    private val resetting = MutableStateFlow(false)
    private var previous: AppStateModel? = null
    private var closed = false
    private val navigationObservations = LiveboardNavigationObservation(dependencies.platform)
    private var observedSession: LiveboardSession? = null
    private val follows = listOf(SocketRequestModel.GET_STATE, SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)

    init {
        observers.launch(start = CoroutineStart.UNDISPATCHED) {
            dependencies.reads.frames.collect { frame ->
                if (!current() || frame?.outcome != NativeReadOutcome.SUCCESS || frame.scope
                    ?.owner !== expectedOwner) return@collect
                if (originScope == null) originScope = frame.scope
                if (frame.scope != originScope) return@collect
                val actual = frame.state ?: return@collect
                if (isCurrentDeviceRemovalTransition(previous, actual)) clearLocal(removal = true)
                previous = actual
                if (!current()) return@collect
                val session = dependencies.sessions.open(frame) ?: return@collect
                val member = LiveboardStateDecoder.decode(actual) ?: return@collect
                if (!current() || !session.isFrameCurrent(frame)) return@collect
                mutableState.update { it.copy(content = LiveboardRootContent(member, session, deviceLabels(actual)),
                    chrome = it.chrome.copy(revision = Any())) }
                if (observedSession !== session && current()) {
                    observedSession = session
                    observeSettlement(session)
                    observers.launch(start = CoroutineStart.UNDISPATCHED) {
                        // A new attachment never replays an earlier form's completion.
                        var seenCompletion = session.sensitive.state.value.createCompletion
                        session.sensitive.state.collect sensitiveState@ { sensitive ->
                            val completion = sensitive.createCompletion
                            val fresh = completion != null && completion !== seenCompletion
                            seenCompletion = completion
                            if (!current() || state.value.content?.session !== session || session.sensitive.state
                                .value !== sensitive) return@sensitiveState
                            mutableState.update { previous ->
                                if (previous.content?.session !== session || previous.chrome.overlay !=
                                    LiveboardRootOverlay.Create) previous
                                else when {
                                    fresh -> previous.copy(createCompletion = completion, chrome = previous.chrome
                                        .copy(revision = Any()))
                                    !sensitive.createOpen && (completion == null || previous
                                        .createCompletion !== completion) ->
                                        previous.copy(createCompletion = null, chrome = previous.chrome.copy(
                                            overlay = LiveboardRootOverlay.None, revision = Any()))
                                    sensitive.createOpen && previous.createCompletion != null ->
                                        previous.copy(createCompletion = null, chrome = previous.chrome.copy(
                                            revision = Any()))
                                    else -> previous
                                }
                            }
                            showObservationBinding?.collected(session.generation, sensitive)
                        }
                    }
                }
            }
        }
        if (current()) dependencies.services.socket.actionsToFollow(add = follows, exclude = null)
        navigationObservations.ready()
    }

    private fun current(): Boolean = !closed && attachment.isCurrent() &&
        dependencies.services.reset.capture() === expectedOwner &&
        (retryReceipt?.let(dependencies.services.reset::isCurrent) != false)

    private fun session(trace: LiveboardNavigationObservationTicket? = null): LiveboardSession? =
        observeNavigationSession(state.value.content?.session, trace, ::current)
    internal fun navigateSecretsFromRendered(rendered: () -> Boolean) =
        navigationObservations.navigate(rendered) { selectRoute(LiveboardRootRoute.Secrets, it) }
    fun selectRoute(route: LiveboardRootRoute, trace: LiveboardNavigationObservationTicket? = null) {
        val session = session(trace) ?: return
        session.sensitive.routeChanged()
        val owns = current(); trace?.finalCurrent = owns.navigationFlag()
        if (owns) navigationObservations.updateRoute(route, trace) {
            mutableState.update { it.copy(createCompletion = null, chrome = it.chrome.copy(route = route,
                overlay = LiveboardRootOverlay.None, revision = Any())) }
        }
        navigationObservations.flushWrites()
    }

    fun openCreate(): Boolean {
        val session = session() ?: return false
        if (!session.sensitive.openCreatePresentation(state.value.chrome.overlay != LiveboardRootOverlay.Create) ||
            !current() || state.value.content?.session !== session) return false
        mutableState.update { it.copy(createCompletion = null, chrome = it.chrome.copy(overlay =
            LiveboardRootOverlay.Create, revision = Any())) }
        return current()
    }

    fun openInvite(): Boolean {
        if (session()?.currentMember()?.identity?.vaultName.isNullOrBlank()) return false
        mutableState.update { it.copy(createCompletion = null, chrome = it.chrome.copy(overlay =
            LiveboardRootOverlay.Invite, revision = Any())) }
        return current()
    }

    fun editForm(draft: LiveboardDraft) { session()?.sensitive?.editForm(draft) }

    fun submitCreate(): Job {
        val session = session()
        val intent = session?.actions?.createIntent()
        // Undispatched registration establishes the issuing waiter before its returned view Job can cancel.
        val job = views.launch(start = CoroutineStart.UNDISPATCHED) {
            if (session != null && intent != null) session.actions.create(intent)
        }
        if (session != null && intent != null) { refreshMetadata(); observeSettlement(session) }
        return job
    }

    private fun observeSettlement(session: LiveboardSession) {
        observers.launch(start = CoroutineStart.UNDISPATCHED) {
            // A snapshot of current private work, including Remove; never a per-intent success promise.
            session.awaitSettled()
            if (current() && state.value.content?.session === session) {
                // Private native truth settled before any error/metadata observer can reenter.
                session.actions.publishEnteredFailures()
                if (current() && state.value.content?.session === session) refreshMetadata()
            }
        }
    }

    fun pasteCreate(): Job {
        val session = session()
        val sensitive = session?.sensitive
        val intent = sensitive?.beginPaste()
        return views.launch {
            if (sensitive == null || intent == null) return@launch
            // Plaintext never crosses a separate queued UI delivery closure.
            withContext(dependencies.environment.ui) {
                val text = dependencies.platform.readClipboard()
                if (!current() || state.value.content?.session !== session) return@withContext
                if (text == null) sensitive.failPaste(intent) else sensitive.completePaste(intent, text)
            }
        }
    }

    private val recoverObservations = LiveboardRecoverObservations()
    fun recover(secretId: String): Job {
        val session = session()
        return observeRecover(recoverObservations, RecoverObservationInput(session, secretId, dependencies.platform) {
            action -> command(session, action) })
    }
    fun delete(secretId: String): Job {
        val session = session()
        val intent = session?.actions?.deleteIntent(secretId)
        return command(session) { if (intent != null) session?.actions?.delete(intent) }
    }
    fun removeDevice(deviceId: String): Job {
        val session = session()
        val intent = session?.removeDeviceIntent(deviceId)
        return command(session) { if (intent != null) session?.actions?.remove(intent) }
    }
    private val decisionObservations = LiveboardDecisionObservations()
    internal fun beginInboxDecisionObservation(rendered: Boolean): LiveboardDecisionObservationTicket? =
        runCatching {
            decisionObservations.begin(dependencies.platform.decisionObservationEnabled, rendered)
        }.getOrNull()
    internal fun recordRejectedInboxDecision(observation: LiveboardDecisionObservationTicket?) {
        val record = runCatching {
            observation?.takeIf { decisionObservations.claim(it) }?.let(decisionObservations::rejected)
        }.getOrNull()
        offerDecisionObservation(record)
    }

    fun decide(identity: models.apiModels.liveboard.LiveboardOperationIdentity, accepted: Boolean,
        observation: LiveboardDecisionObservationTicket? = null): Job {
        val session = session()
        val intent = session?.actions?.decisionIntent(identity, accepted)
        val ticket = runCatching { observation?.takeIf { decisionObservations.claim(it) } }.getOrNull()
        val rejection = createDecisionRejectionTrace(ticket)
        val job = command(session) {
            ticket?.let { runCatching { decisionObservations.bodyReached(it) } }
            if (intent != null) {
                val returned = session?.actions?.decideObserved(intent, rejection)
                if (returned != null) ticket?.let { runCatching {
                    decisionObservations.returnedObserved(it, decisionObservationOutcome(returned),
                        rejection.observedRejectionSource())
                } }
            }
        }
        val record = runCatching { ticket?.let { decisionObservations.postRegistration(it,
            if (session != null) DecisionObservationFlag.ONE else DecisionObservationFlag.ZERO,
            if (session == null) DecisionObservationFlag.UNKNOWN else if (intent != null) DecisionObservationFlag.ONE
                else DecisionObservationFlag.ZERO) } }.getOrNull()
        if (record != null) {
            offerDecisionObservation(record)
            job.invokeOnCompletion { failure ->
                val completion = when (failure) {
                    null -> DecisionObservationCompletion.COMPLETED
                    is CancellationException -> DecisionObservationCompletion.CANCELLED
                    else -> DecisionObservationCompletion.THREW
                }
                offerDecisionObservation(runCatching { ticket?.let { decisionObservations.completed(it, completion) }
                    }.getOrNull())
            }
        }
        return job
    }

    private fun decisionObservationOutcome(result: NativeLiveboardOutcome<*>): DecisionObservationOutcome =
        when (result) {
            is NativeLiveboardOutcome.NotEntered -> DecisionObservationOutcome.NOT_ENTERED
            is NativeLiveboardOutcome.Entered -> when (result.result) {
                is NativeResult.Success -> DecisionObservationOutcome.ENTERED_SUCCESS
                is NativeResult.Unavailable -> DecisionObservationOutcome.ENTERED_UNAVAILABLE
                NativeResult.Uncertain -> DecisionObservationOutcome.ENTERED_UNCERTAIN
            }
        }

    private fun offerDecisionObservation(record: LiveboardDecisionObservationRecord?) {
        if (record == null) return
        val platform = dependencies.platform
        runCatching { views.launch { runCatching { platform.emitDecisionObservation(record) } } }
    }
    private fun command(session: LiveboardSession?, action: suspend () -> Unit): Job {
        val job = views.launch(start = CoroutineStart.UNDISPATCHED) { if (session != null && current()) action() }
        if (session != null && current()) { refreshMetadata(); observeSettlement(session) }
        return job
    }
    internal var showObservationBinding: LiveboardShowView? = null
        private set
    internal fun beginShowObservation(rendered: Boolean): LiveboardShowObservationTicket? = runCatching {
        if (dependencies.platform.decisionObservationEnabled) {
            showObservationBinding = createShowView(showObservationBinding, dependencies.platform, views)
            showObservationBinding?.begin(rendered)
        } else null
    }.getOrNull()
    internal fun recordRejectedShowObservation(observation: LiveboardShowObservationTicket?) =
        offerShowObservation(observation?.let { showObservationBinding?.owner?.rejected(it) })
    fun show(secretId: String): Job = show(secretId, null)
    internal fun show(secretId: String, observation: LiveboardShowObservationTicket?): Job {
        val ticket = showObservationBinding?.claimObservation(observation)
        val job = views.launch(start = CoroutineStart.UNDISPATCHED) {
            val selected = session()
            ticket?.body(selected != null, selected?.generation)
            val returned = selected?.sensitive?.show(secretId, ticket)
            if (returned != null) ticket?.returned(returned)
        }
        if (ticket != null) {
            offerShowObservation(showObservationBinding?.owner?.registered(ticket))
            job.invokeOnCompletion { failure ->
                offerShowObservation(showObservationBinding?.owner?.completed(ticket, failure is CancellationException,
                    failure != null))
            }
        }
        return job
    }
    fun hide(secretId: String) { session()?.sensitive?.hide(secretId) }
    fun copy(secretId: String) {
        session()?.sensitive?.copyValue(secretId)?.let(dependencies.platform::copyText)
    }
    fun searchChanged(query: String) { session()?.sensitive?.filterChanged(query) }
    fun dismissError() { session()?.inbox?.dismissError() }
    /** Explicit completion observation uses the existing scoped lane, never another native worker. */
    internal fun refreshAfterCommand() {
        val session = session() ?: return
        val required = dependencies.reads.requireFreshRead(session.scope) ?: return
        if (current() && session.ownsNativeScope()) dependencies.services.socket.refreshAppState(required)
    }

    fun resetProfile(): Job = clearLocal(removal = false)

    private fun clearLocal(removal: Boolean): Job {
        val origin = expectedOwner
        return views.launch {
            if (!current() || !resetting.compareAndSet(false, true)) return@launch
            try {
                withContext(dependencies.environment.ui) {
                    if (!current()) return@withContext
                    dependencies.services.socket.onAppBackground()
                    if (current()) mutableState.update { it.copy(chrome = it.chrome.copy(reset = LiveboardRootReset
                        .Running, revision = Any())) }
                }
                if (!current()) return@launch
                val result = dependencies.services.reset.reset(origin)
                if (result == null) {
                    withContext(dependencies.environment.ui) {
                        if (current()) mutableState.update { it.copy(chrome = it.chrome.copy(reset =
                            LiveboardRootReset.Failed, revision = Any())) }
                    }
                    return@launch
                }
                withContext(dependencies.environment.ui) {
                    // Actual queued publication revalidates the owned receipt, never recaptures a successor.
                    if (closed || !attachment.isCurrent() || !dependencies.services.reset.isCurrent(result)
                        ) return@withContext
                    expectedOwner = result.owner
                    retryReceipt = result
                    val navigate = result.value || removal
                    val ticket = if (navigate) LiveboardRootNavigation(result) else null
                    mutableState.update { it.copy(createCompletion = null, chrome = it.chrome.copy(
                        reset = if (navigate) LiveboardRootReset.Idle else LiveboardRootReset.Failed,
                        navigation = ticket, overlay = LiveboardRootOverlay.None, revision = Any())) }
                }
            } finally { resetting.value = false }
        }
    }

    fun back(): Boolean {
        if (!current()) return false
        when {
            state.value.chrome.overlay != LiveboardRootOverlay.None -> {
                if (state.value.chrome.overlay == LiveboardRootOverlay.Create) state.value.content?.session
                    ?.sensitive?.dismissCreate()
                if (!current()) return false
                mutableState.update { it.copy(createCompletion = null, chrome = it.chrome.copy(overlay =
                    LiveboardRootOverlay.None, revision = Any())) }
            }
            state.value.chrome.route != LiveboardRootRoute.Home -> selectRoute(LiveboardRootRoute.Home)
            else -> return false
        }
        return true
    }

    /** Presentation completion is scoped to its token, not a harmless frame/chrome revision. */
    fun finishCreate(completion: LiveboardCreateCompletion, expected: LiveboardSession): Boolean {
        while (true) {
            val previous = state.value
            if (!current() || previous.content?.session !== expected || !expected.ownsNativeScope() ||
                previous.createCompletion !== completion || previous.chrome.overlay != LiveboardRootOverlay.Create
                    ) return false
            val sensitive = expected.sensitive.state.value
            if (sensitive.createOpen || sensitive.createCompletion !== completion) return false
            val finished = previous.copy(createCompletion = null,
                chrome = previous.chrome.copy(overlay = LiveboardRootOverlay.None, revision = Any()))
            // No generic Back/conceal/native side effect after this observable publication.
            if (mutableState.compareAndSet(previous, finished)) return true
        }
    }

    /** Voyager calls this inside its actual UI effect immediately before navigation. */
    fun consumeNavigation(ticket: LiveboardRootNavigation): Boolean {
        if (!current() || state.value.chrome.navigation !== ticket || !dependencies.services.reset.isCurrent(ticket
            .receipt)) return false
        mutableState.update { if (it.chrome.navigation === ticket) it.copy(chrome = it.chrome.copy(navigation =
            null, revision = Any())) else it }
        return current() && dependencies.services.reset.isCurrent(ticket.receipt)
    }
    private fun refreshMetadata() {
        if (current()) mutableState.update { it.copy(chrome = it.chrome.copy(revision = Any())) }
    }

    fun close() {
        if (closed) return
        val ownsAttachment = current()
        closed = true
        if (ownsAttachment) {
            state.value.content?.session?.sensitive?.routeChanged()
            // Inline conceal observers can attach B, so check exact attachment again before global teardown.
            if (attachment.isCurrent()) dependencies.services.socket.actionsToFollow(add = null, exclude = follows)
        }
        closeRecoverObservation(recoverObservations, dependencies.platform)
        showObservationBinding?.close()
        observers.cancel()
        views.cancel()
        attachment.close()
        mutableState.value = LiveboardRootPresentation()
        navigationObservations.close()
    }
}
