package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import models.apiModels.liveboard.LiveboardSnapshot
import models.apiModels.liveboard.LiveboardSecret

/** Plaintext exists only in authored input, current cards, or private clearable capsules. */
class LiveboardSensitiveController internal constructor(
    private val session: LiveboardSession,
    private val dependencies: LiveboardSessionDependencies,
) {
    private class Delivery(val secretId: String, val value: String)
    private class SuspendedCard(val card: Any, val selection: LiveboardCardContinuity,
        val disclosure: LiveboardDisclosure)
    private class ReadSuspension(
        val afterAttempt: Long,
        val cards: Map<String, SuspendedCard>,
        val proof: NativeReadFrame? = null,
        val observations: List<LiveboardShowObservationTicket> = emptyList(),
    )
    private class Transient(
        val generation: Any = Any(),
        val cards: Map<String, Any> = emptyMap(),
        val deliveries: Map<Any, Delivery> = emptyMap(),
        val suspension: ReadSuspension? = null,
    )
    private val transient = MutableStateFlow(Transient())
    private val mutableEpoch = MutableStateFlow(transient.value.generation)
    internal val privacyEpoch: StateFlow<Any> = mutableEpoch.asStateFlow()
    private val mutableState = MutableStateFlow(LiveboardSensitiveSnapshot())
    val state: StateFlow<LiveboardSensitiveSnapshot> = mutableState.asStateFlow()
    private val auth = LiveboardAuthGate(dependencies.client.auth, dependencies.environment) { conceal(false,
        closeCreate = false) }
    private val hidden = MutableStateFlow(false)
    private val hardDecisionStamp = MutableStateFlow(Any())
    internal fun decisionStamp(): Any = hardDecisionStamp.value
    internal fun isDecisionStamp(stamp: Any?): Boolean = stamp === hardDecisionStamp.value && !hidden.value &&
        session.ownsNativeScope()
    private var revision = Any()
    private var authoredVersion = Any()
    private var formIdentity = Any()
    private val selected = MutableStateFlow<Map<LiveboardCreateIntent, LiveboardEntryAdmission>>(emptyMap())
    private val shows = MutableStateFlow<Map<String, LiveboardEntryAdmission>>(emptyMap())
    private val cardSelections = MutableStateFlow<Map<String, LiveboardCardContinuity>>(emptyMap())
    internal fun generation(): Any = transient.value.generation
    internal fun isGeneration(generation: Any): Boolean = isGenerationObserved(generation, null)
    private fun isGenerationObserved(generation: Any, trace: LiveboardDecisionRejectionTrace?): Boolean =
        (!session.isDisposed).observedCurrentness(trace,
            LiveboardDecisionRejectionSource.POST_CAS_SENSITIVE_DISPOSED) &&
            (transient.value.generation === generation).observedCurrentness(trace,
                LiveboardDecisionRejectionSource.POST_CAS_SENSITIVE_GENERATION_CHANGED)

    internal fun isCurrent(generation: Any): Boolean = isCurrentObserved(generation, null)
    internal fun isCurrentObserved(generation: Any, trace: LiveboardDecisionRejectionTrace?): Boolean =
        (!hidden.value).observedCurrentness(trace, LiveboardDecisionRejectionSource.POST_CAS_SENSITIVE_HIDDEN) &&
            (transient.value.suspension == null).observedCurrentness(trace,
                LiveboardDecisionRejectionSource.POST_CAS_SENSITIVE_SUSPENDED) &&
            isGenerationObserved(generation, trace)
    internal fun isRevision(expected: Any): Boolean = revision === expected
    internal fun isCard(id: String, card: Any): Boolean = transient.value.cards[id] === card

    fun editDraft(name: String, value: String) {
        editForm(LiveboardDraft(name, value))
    }

    fun openCreate(): Boolean = openCreatePresentation(newForm = !state.value.createOpen)

    /** Root modal ownership can change even when a soft conceal retained createOpen. */
    internal fun openCreatePresentation(newForm: Boolean): Boolean {
        if (session.currentMember()?.permissions?.canCreateSecret != true) return false
        if (newForm) formIdentity = Any()
        mutableState.update { it.copy(draft = it.draft ?: LiveboardDraft("", ""), createOpen = true,
            pasteHint = "", createCompletion = null) }
        return true
    }

    fun editForm(draft: LiveboardDraft) {
        if (session.currentMember() == null) return
        revokeDraft()
        authoredVersion = Any()
        mutableState.update { it.copy(draft = draft.copy(words = draft.words.toList()), pasteHint = "",
            createCompletion = null) }
        session.actions.settleRevoked()
    }

    fun beginPaste(): LiveboardPasteIntent? = if (state.value.createOpen && session.currentMember() != null)
        LiveboardPasteIntent(generation(), revision) else null

    fun completePaste(intent: LiveboardPasteIntent, text: String): Boolean {
        if (!isCurrent(intent.generation) || !isRevision(intent.revision) || !state.value.createOpen) return false
        val draft = state.value.draft ?: return false
        if (draft.mode == LiveboardDraftMode.Plain) editForm(draft.copy(value = text))
        else {
            val words = text.trim().split(Regex("\\s+"))
            if (words.size !in listOf(SHORT_SEED_WORDS, MAX_SEED_WORDS)) { failPaste(intent); return false }
            editForm(draft.copy(wordCount = words.size, words = words + List(MAX_SEED_WORDS - words.size) { "" }))
        }
        return true
    }

    fun failPaste(intent: LiveboardPasteIntent) {
        if (isCurrent(intent.generation) && isRevision(intent.revision)) {
            mutableState.update { it.copy(pasteHint = "ClipboardUnavailable") }
        }
    }
    internal fun selectDraft(): LiveboardCreateIntent? {
        val draft = state.value.draft ?: return null
        val intent = LiveboardCreateIntent(session.generation, revision, draft.name, authoredVersion, formIdentity)
        val context = LiveboardEntrySelection(session, generation(), LiveboardEntrySubject.Create(revision))
        selected.update { it + (intent to LiveboardEntryAdmission(session.scope, context, draft)) }
        return intent
    }
    internal fun selectedAdmission(intent: LiveboardCreateIntent): LiveboardEntryAdmission? = selected.value[intent]
    internal fun releaseDraft(intent: LiveboardCreateIntent) {
        selected.value[intent]?.cancelQueued()
        selected.update { it - intent }
    }
    private fun revokeDraft() {
        selected.value.values.forEach { it.cancelQueued() }
        selected.value = emptyMap()
        revision = Any()
    }
    fun dismissCreate() { conceal(clearDraft = true) }
    internal fun completedCreate(intent: LiveboardCreateIntent) {
        if (authoredVersion !== intent.authoredVersion) return
        val completion = if (state.value.createOpen && formIdentity === intent.formIdentity
            ) LiveboardCreateCompletion() else null
        conceal(clearDraft = true, completion = completion)
    }
    internal fun captureEpoch(): Any = transient.value.generation

    suspend fun show(secretId: String): Boolean = show(secretId, null)
    internal suspend fun show(secretId: String, observation: LiveboardShowObservationTicket?): Boolean {
        if (!session.isReadyForEntry().observedShowRejection(observation, ShowObservationPhase.NOT_READY))
            return false
        val secret = session.currentMember()?.secrets?.firstOrNull { it.identity.passId == secretId } ?: run {
            observation?.phase(ShowObservationPhase.NO_SELECTION)
            return false
        }
        if (secret.primary.readiness != "ready" || secret.primary.action !in listOf("showLocal", "showRecovered")
            ) { observation?.phase(ShowObservationPhase.NOT_SHOWABLE); return false }
        if (secret.primary.action == "showRecovered" && secret.primary.claimId == null) {
            observation?.phase(ShowObservationPhase.MISSING_CLAIM); return false
        }
        hide(secretId)
        val card = Any()
        val stamp = generation()
        transient.update { Transient(it.generation, it.cards + (secretId to card), it.deliveries) }
        var selectedFact: ShowGuardFacts? = null
        cardSelections.update { it + (secretId to selectCard(card, secret, observation).also {
            selectedFact = it.selectedFact }) }
        observation.guard(selectedFact)
        val context = LiveboardEntrySelection(session, stamp, LiveboardEntrySubject.Reveal(secret, card))
        val admission = LiveboardEntryAdmission(session.scope, context)
        shows.update { it + (secretId to admission) }
        return try {
            if (!auth.confirm().observedShowRejection(observation, ShowObservationPhase.AUTH_DECLINED) ||
                !context.isCurrent().observedShowRejection(observation, ShowObservationPhase.POST_AUTH_RETIRED)
                ) return false
            val delivery = readDelivery(secret, admission, observation) ?: return false
            // Only an opaque token crosses a potentially held UI continuation.
            val returned = withContext(dependencies.environment.ui) {
                deliver(secretId, delivery, context, observation)
            }
            observation?.phase(if (returned) ShowObservationPhase.UI_DELIVERY_TRUE
                else ShowObservationPhase.UI_DELIVERY_FALSE)
            returned
        } finally {
            admission.cancelQueued()
            shows.update { old -> if (old[secretId] === admission) old - secretId else old }
        }
    }

    private suspend fun readDelivery(secret: LiveboardSecret, admission: LiveboardEntryAdmission,
        observation: LiveboardShowObservationTicket?): Any? = withContext(dependencies.environment.io) {
            val recovered = secret.primary.action == "showRecovered"
            val id = if (recovered) secret.primary.claimId ?: return@withContext null else secret.identity.passId
            observation?.phase(ShowObservationPhase.RAW_PENDING)
            val result = dependencies.checked.showSelected(session.scope, id, recovered, admission, observation)
            val value = if (result is NativeInvocation.Value) LiveboardRevealDecoder.disclosure(result.value,
                recovered)
                else null
            if (value == null) {
                observation?.phase(if (result is NativeInvocation.Value) ShowObservationPhase.DECODE_REJECTED
                    else ShowObservationPhase.RAW_NOT_VALUE)
                null
            } else {
                val token = storeDelivery(secret.identity.passId, value, admission)
                observation?.phase(if (token == null) ShowObservationPhase.DELIVERY_REJECTED
                    else ShowObservationPhase.DELIVERY_STORED)
                token
            }
        }

    private fun selectCard(card: Any, secret: LiveboardSecret,
        observation: LiveboardShowObservationTicket?): LiveboardCardContinuity {
        val frame = dependencies.reads.frames.value
        val member = session.currentMember()
        return LiveboardCardContinuity.select(card, secret, member.takeIf {
            dependencies.reads.frames.value === frame }, frame?.attempt ?: Long.MIN_VALUE, observation)
    }

    private fun storeDelivery(id: String, value: LiveboardRevealValue, admission: LiveboardEntryAdmission): Any? {
        val token = Any()
        while (true) {
            val old = transient.value
            val selection = cardSelections.value[id]
            if (selection == null || admission.selection?.isCurrent() != true || old.cards[id] !== selection.card)
                return null
            if (transient.compareAndSet(old, Transient(old.generation, old.cards,
                old.deliveries + (token to Delivery(id, value.value))))) {
                selection.bind(value.receipt)
                return token
            }
        }
    }

    private fun deliver(id: String, token: Any, context: LiveboardEntrySelection,
        observation: LiveboardShowObservationTicket?): Boolean {
        while (true) {
            val old = transient.value
            val value = old.deliveries[token]?.value ?: return false
            val valid = context.isCurrent()
            if (!transient.compareAndSet(old, Transient(old.generation, old.cards, old.deliveries - token))) continue
            if (!valid) return false
            mutableState.update { it.copy(revealed = it.revealed + (id to
                LiveboardDisclosure.observed(id, value, observation))) }
            // A publication observer may synchronously conceal or change owner.
            return context.isCurrent() && id in state.value.revealed
        }
    }

    fun hide(secretId: String) {
        hardDecisionStamp.value = Any()
        session.actions.revokeDecisions()
        try { hideCard(secretId) } finally { session.actions.settleDecisions() }
    }
    private fun hideCard(secretId: String) {
        shows.value[secretId]?.cancelQueued()
        transient.update { old -> Transient(old.generation, old.cards - secretId,
            old.deliveries.filterValues { delivery -> delivery.secretId != secretId },
            old.suspension?.let { ReadSuspension(it.afterAttempt, it.cards - secretId, it.proof) }) }
        cardSelections.update { it - secretId }
        mutableState.update { LiveboardSensitiveSnapshot.copyRevealed(it, it.revealed - secretId,
            ShowSnapshotWriter.HIDE_CARD) }
    }
    fun filterChanged(query: String) { conceal(false, closeCreate = false) }
    fun routeChanged() { conceal(false, closeCreate = false) }
    fun copyValue(secretId: String): String? {
        if (!session.isReadyForEntry()) return null
        val expected = cardSelections.value[secretId] ?: return null
        if (!isCard(secretId, expected.card) || !expected.current(session.currentMember(), dependencies.reads.frames
                .value?.attempt ?: Long.MIN_VALUE)) return null
        return state.value.revealed[secretId]?.value
    }
    internal fun background() { hidden.value = true; conceal(true) }
    internal fun foreground() { hidden.value = false }

    internal fun observeMember(member: LiveboardSnapshot, attempt: Long) {
        cardSelections.value.forEach { (id, expected) -> if (!expected.current(member, attempt)) hideCard(id) }
        transient.value.suspension?.cards?.forEach { (id, card) ->
            if (!card.selection.current(member, attempt)) hideCard(id)
        }
    }

    /** A technical read masks existing disclosure without renewing its authorization. */
    internal fun suspendForRead() {
        if (transient.value.suspension != null || !session.ownsNativeScope()) return
        session.revokeQueuedForPrivacy(technical = true)
        shows.value.values.forEach { it.cancelQueued() }
        revokeDraft()
        shows.value = emptyMap()
        val suspended = suspendCards()
        cardSelections.value = emptyMap()
        mutableState.update { LiveboardSensitiveSnapshot.copyRevealed(it, emptyMap(),
            ShowSnapshotWriter.READ_MASK, clearPasteHint = true) }
        if (transient.value === suspended) mutableEpoch.value = suspended.generation
        session.settleQueuedForPrivacy(technical = true)
    }

    private fun suspendCards(): Transient {
        while (true) {
            val old = transient.value
            if (old.suspension != null) return old
            val frame = dependencies.reads.frames.value
            val member = if (frame == null) null else session.currentMember()
            val captured = mutableListOf<LiveboardShowObservationTicket>()
            val cards = state.value.revealed.mapNotNull { (id, value) ->
                val card = old.cards[id]
                val selected = cardSelections.value[id]
                if (hidden.value || card == null || selected == null) null
                else if (selected.card === card && selected.capture(member, frame?.attempt ?: Long.MIN_VALUE)) {
                    selected.observation.cacheGuardTicket(captured)
                    id to SuspendedCard(card, selected, value)
                } else null
            }.toMap()
            if (dependencies.reads.frames.value !== frame) continue
            // Pending native/UI delivery payloads and the old admission generation end here.
            // A revoked frame carries no cards; only a new physical SUCCESS can consume its empty mask.
            val next = Transient(suspension = ReadSuspension(frame?.attempt ?: Long.MIN_VALUE, cards,
                observations = captured))
            if (transient.compareAndSet(old, next)) {
                captured.guardAll(ShowGuardPoint.CAPTURE)
                return next
            }
        }
    }

    /** Called only by the existing beforeCanonical relay, before this physical frame is public. */
    internal fun prepareRead(frame: NativeReadFrame) {
        if (dependencies.reads.synchronization.value.let { it.loading || it.activeRefreshes > 0 }) suspendForRead()
        if (frame.scope != session.scope || frame.outcome != NativeReadOutcome.SUCCESS || frame.state?.success !=
            true) return
        var staged: List<LiveboardShowObservationTicket>? = null
        transient.update { old ->
            staged = null
            val suspended = old.suspension
            if (suspended == null || frame.attempt <= (suspended.proof?.attempt ?: suspended.afterAttempt) ||
                !session.ownsNativeScope()) old
            else {
                val observations = suspended.observations
                Transient(old.generation, suspension = ReadSuspension(suspended.afterAttempt, suspended.cards,
                    frame, observations)).also { staged = observations }
            }
        }
        staged.guardAll(ShowGuardPoint.STAGE)
    }

    internal fun resumeRead(frame: NativeReadFrame) {
        while (true) {
            val old = transient.value
            val suspended = old.suspension ?: return
            if (suspended.proof !== frame || hidden.value || !session.isFrameCurrent(frame) || !session.inbox
                .isReady()) return
            val member = session.currentMember() ?: return
            val restoring = mutableListOf<LiveboardShowObservationTicket>()
            val cards = suspended.cards.filterValues { card ->
                val selection = card.selection
                selection.cacheGuardWhenCurrent(selection.current(member, frame.attempt), restoring)
            }
            val restored = Transient(old.generation, cards.mapValues { it.value.card })
            if (!transient.compareAndSet(old, restored)) continue
            cardSelections.value = cards.mapValues { it.value.selection }
            // Reentrant Hide/privacy/owner callbacks after publication own the successor state.
            var published = false
            mutableState.update { current ->
                published = false
                if (transient.value === restored && session.isFrameCurrent(frame) && session.inbox.isReady())
                    LiveboardSensitiveSnapshot.copyRevealed(current, cards.mapValues { it.value.disclosure },
                        ShowSnapshotWriter.READ_RESTORE).also { published = true } else current
            }
            restoring.guardWhenPublished(published, ShowGuardPoint.RESTORE)
            return
        }
    }

    internal fun discardRead() {
        if (transient.value.suspension != null) conceal(false)
    }
    internal fun conceal(clearDraft: Boolean, closeCreate: Boolean = true, completion: LiveboardCreateCompletion? =
        null) {
        hardDecisionStamp.value = Any()
        session.revokeQueuedForPrivacy()
        shows.value.values.forEach { it.cancelQueued() }
        revokeDraft()
        shows.value = emptyMap()
        val concealed = Transient()
        transient.value = concealed
        cardSelections.value = emptyMap()
        if (clearDraft) authoredVersion = Any()
        mutableState.update { LiveboardSensitiveSnapshot.concealed(
            draft = if (clearDraft) null else it.draft,
            createOpen = !clearDraft && !closeCreate && it.createOpen,
            completion = completion,
        ) }
        // State collectors may create a nested generation/input. Never relabel that successor.
        if (transient.value.generation === concealed.generation) mutableEpoch.value = concealed.generation
        // Awaiters/worker cancellation run only after actual transient references and UI values are gone.
        session.settleQueuedForPrivacy()
    }
}

private const val SHORT_SEED_WORDS: Int = 12
private const val MAX_SEED_WORDS: Int = 24
