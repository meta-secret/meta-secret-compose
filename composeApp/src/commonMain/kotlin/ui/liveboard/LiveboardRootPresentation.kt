package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardSession
import core.metaSecretCore.liveboard.LiveboardDisclosure
import core.metaSecretCore.liveboard.LiveboardSensitiveSnapshot
import core.metaSecretCore.liveboard.LiveboardShowObservationRecord
import core.metaSecretCore.liveboard.LiveboardShowObservationTicket
import core.metaSecretCore.liveboard.LiveboardShowObservations
import core.metaSecretCore.liveboard.guardsOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import models.apiModels.liveboard.LiveboardSnapshot

enum class LiveboardRootRoute { Home, Secrets, Profile }
enum class LiveboardRootOverlay { None, Create, Invite }
enum class LiveboardRootReset { Idle, Running, Failed }

class LiveboardRootNavigation internal constructor(internal val receipt: core.metaSecretCore.liveboard
    .NativeKeyResult<Boolean>)
class LiveboardRootContent internal constructor(
    val member: LiveboardSnapshot,
    val session: LiveboardSession,
    val deviceLabels: Map<String, String> = emptyMap(),
)

data class LiveboardRootChrome(
    val route: LiveboardRootRoute = LiveboardRootRoute.Home,
    val overlay: LiveboardRootOverlay = LiveboardRootOverlay.None,
    val reset: LiveboardRootReset = LiveboardRootReset.Idle,
    val navigation: LiveboardRootNavigation? = null,
    val revision: Any = Any(),
)

data class LiveboardRootPresentation(
    val content: LiveboardRootContent? = null,
    val chrome: LiveboardRootChrome = LiveboardRootChrome(),
    val createCompletion: core.metaSecretCore.liveboard.LiveboardCreateCompletion? = null,
)

/** Direct current owner projections; these DTOs never duplicate a sensitive or command holder. */
class LiveboardRenderState(
    val root: LiveboardRootPresentation,
    val inbox: core.metaSecretCore.liveboard.LiveboardInboxState?,
    val sensitive: core.metaSecretCore.liveboard.LiveboardSensitiveSnapshot?,
    val activity: LiveboardRenderActivity,
    val search: String = "",
) { override fun toString(): String = "LiveboardRenderState(redacted)" }
class LiveboardRenderActivity(
    val create: core.metaSecretCore.liveboard.LiveboardCreatePresentation,
    val actions: List<core.metaSecretCore.liveboard.LiveboardActionPresentation>,
)
class LiveboardRenderActions(
    val navigation: LiveboardNavigationActions,
    val vault: LiveboardVaultActions,
    val inbox: LiveboardInboxActions,
    val secrets: LiveboardSecretsActions,
    val create: LiveboardCreateActions,
)
class LiveboardNavigationActions(val home: () -> Unit, val secrets: () -> Unit, val profile: () -> Unit,
    val invite: () -> Unit, val back: () -> Unit)
class LiveboardVaultActions(val removeDevice: (String) -> Unit, val resetProfile: () -> Unit)
class LiveboardInboxActions(val dismissError: () -> Unit, val decide: (models.apiModels.liveboard
    .LiveboardOperationIdentity, Boolean) -> Unit)
class LiveboardSecretsActions(val search: (String) -> Unit, val values: LiveboardValueActions,
    val commands: LiveboardSecretActions)
class LiveboardValueActions(val show: (String) -> Unit, val hide: (String) -> Unit, val copy: (String) -> Unit)
class LiveboardSecretActions(val recover: (String) -> Unit, val delete: (String) -> Unit)
class LiveboardCreateActions(val open: () -> Unit, val edit: (core.metaSecretCore.liveboard.LiveboardDraft) -> Unit,
    val paste: () -> Unit, val submit: () -> Unit, val dismiss: () -> Unit)
class LiveboardRenderEnvironment(val appVersion: String, val blur: core.OrbitBackdropBlur,
    val reducedMotion: Boolean, val captureEpoch: Any) {
    internal var showObservationEmitter: ((LiveboardShowObservationTicket) -> Unit)? = null
        private set
    internal companion object {
        fun observed(appVersion: String, blur: core.OrbitBackdropBlur, reducedMotion: Boolean, captureEpoch: Any,
            emitter: ((LiveboardShowObservationTicket) -> Unit)?): LiveboardRenderEnvironment =
            LiveboardRenderEnvironment(appVersion, blur, reducedMotion, captureEpoch).also {
                it.showObservationEmitter = emitter }
    }
}

/** UI transport binding only; its metadata owner never stores this platform or coroutine scope. */
internal class LiveboardShowView(private val platform: LiveboardPlatformPort, private val scope: CoroutineScope) {
    val owner = LiveboardShowObservations()
    val emitter = showCommitEmitter(owner, scope, platform)
    private val guardDrain = runCatching { scope.launch(start = CoroutineStart.UNDISPATCHED) {
        owner.guardChanges.collect {
            owner.guardRecords().forEach { record ->
                if (owner.guardsOpen()) runCatching { platform.emitShowObservation(record) }
            }
        }
    } }.getOrNull()
    fun begin(rendered: Boolean) = owner.begin(true, rendered)
    fun claimObservation(ticket: LiveboardShowObservationTicket?): LiveboardShowObservationTicket? = runCatching {
        ticket?.takeIf(owner::claim)
    }.getOrNull()
    fun collected(generation: Any, snapshot: LiveboardSensitiveSnapshot) {
        runCatching {
            val revealed: Map<String, LiveboardDisclosure> = snapshot.revealed
            val present = revealed.values.mapNotNull { it.showObservation }
            owner.collected(generation, revealed.isEmpty(), present, snapshot.showWriter ?:
                core.metaSecretCore.liveboard.ShowSnapshotWriter.UNOBSERVED).forEach { offer(it) }
        }
    }
    fun offer(record: LiveboardShowObservationRecord?) { offerShowRecord(scope, platform, record) }
    fun close() { owner.close(); guardDrain?.cancel() }
}
internal fun createShowView(existing: LiveboardShowView?, platform: LiveboardPlatformPort,
    scope: CoroutineScope): LiveboardShowView = existing ?: LiveboardShowView(platform, scope)

private fun offerShowRecord(scope: CoroutineScope, platform: LiveboardPlatformPort,
    record: LiveboardShowObservationRecord?) {
    if (record == null) return
    runCatching { scope.launch { runCatching { platform.emitShowObservation(record) } } }
}
private fun showCommitEmitter(owner: LiveboardShowObservations, scope: CoroutineScope,
    platform: LiveboardPlatformPort): (LiveboardShowObservationTicket) -> Unit = { ticket ->
    offerShowRecord(scope, platform, owner.committed(ticket))
}
internal val LiveboardRootCoordinator.showObservationEmitter get() = showObservationBinding?.emitter
internal fun LiveboardRootCoordinator.offerShowObservation(record: LiveboardShowObservationRecord?) {
    showObservationBinding?.offer(record)
}
