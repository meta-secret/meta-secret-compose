package ui.liveboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import core.OrbitBackdropBlur
import core.metaSecretCore.liveboard.LiveboardActionPhase
import core.metaSecretCore.liveboard.LiveboardCreatePhase
import core.metaSecretCore.liveboard.LiveboardCreatePresentation
import core.metaSecretCore.liveboard.LiveboardInboxState
import core.metaSecretCore.liveboard.LiveboardSensitiveSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject
import ui.liveboard.layout.LiveboardBoard

/** Shared Main entry for all registration/splash/onboarding routes, without legacy VM owners. */
@Composable
fun LiveboardScreen(onRegistration: () -> Unit) {
    val dependencies: LiveboardRootDependencies = koinInject()
    val blur: OrbitBackdropBlur = koinInject()
    val root = remember { LiveboardRootCoordinator(dependencies) }
    DisposableEffect(root) { onDispose { root.close() } }
    val presentation by root.state.collectAsState()
    val session = presentation.content?.session
    val emptyInbox = remember { MutableStateFlow<LiveboardInboxState?>(null) }
    val emptySensitive = remember { MutableStateFlow<LiveboardSensitiveSnapshot?>(null) }
    val emptyEpoch = remember { MutableStateFlow<Any>(Any()) }
    val inbox by (session?.inbox?.state ?: emptyInbox).collectAsState()
    val sensitive by (session?.sensitive?.state ?: emptySensitive).collectAsState()
    val epoch by (session?.sensitive?.privacyEpoch ?: emptyEpoch).collectAsState()
    var search by remember(session) { mutableStateOf("") }
    val activity = LiveboardRenderActivity(session?.actions?.createPresentation() ?:
        LiveboardCreatePresentation(LiveboardCreatePhase.Idle, false, false), session?.actions?.presentation()
            .orEmpty())
    val navigation = presentation.chrome.navigation
    LaunchedEffect(root, navigation) {
        if (navigation != null && root.consumeNavigation(navigation)) onRegistration()
    }
    // Returned/unknown attempts request one ordinary observation. No producer replay or polling.
    LaunchedEffect(root, session, activity.actions) {
        if (activity.actions.any { it.phase in listOf(LiveboardActionPhase.Submitted, LiveboardActionPhase.Unknown)
            }) root.refreshAfterCommand()
    }
    val displayedContent = presentation.content
    val displayedChrome = presentation.chrome.revision
    val displayedCompletion = presentation.createCompletion?.takeIf { completion ->
        sensitive?.let { current -> !current.createOpen && completion === current.createCompletion } == true
    }
    fun ownsRendered(): Boolean = root.state.value.content === displayedContent && root.state.value.chrome
        .revision === displayedChrome &&
        (session == null || session.sensitive.privacyEpoch.value === epoch)
    val actions = LiveboardRenderActions(
        LiveboardNavigationActions({ if (ownsRendered()) root.selectRoute(LiveboardRootRoute.Home) },
            { root.navigateSecretsFromRendered(::ownsRendered) },
            { if (ownsRendered()) root.selectRoute(LiveboardRootRoute.Profile) },
            { if (ownsRendered()) root.openInvite() }, { if (ownsRendered()) root.back() }),
        LiveboardVaultActions({ if (ownsRendered()) root.removeDevice(it) }, { if (ownsRendered()) root
            .resetProfile() }),
        LiveboardInboxActions({ if (ownsRendered()) root.dismissError() }, { identity, accepted ->
            val rendered = ownsRendered()
            val observation = root.beginInboxDecisionObservation(rendered)
            if (rendered) root.decide(identity, accepted, observation)
            else root.recordRejectedInboxDecision(observation)
        }),
        LiveboardSecretsActions({ if (ownsRendered()) { search = it; root.searchChanged(it) } },
            LiveboardValueActions({ root.showFromRendered(ownsRendered(), it) },
                { if (ownsRendered()) root.hide(it) }, {
                if (ownsRendered()) root.copy(it) }),
            LiveboardSecretActions({ if (ownsRendered()) root.recover(it) }, { if (ownsRendered()) root.delete(it) })),
        LiveboardCreateActions({ if (ownsRendered()) root.openCreate() }, { if (ownsRendered()) root.editForm(it) },
            { if (ownsRendered()) root.pasteCreate() }, { if (ownsRendered()) root.submitCreate() }, {
                if (displayedCompletion != null && session != null) root.finishCreate(displayedCompletion, session)
                else if (ownsRendered()) root.back()
            }),
    )
    LiveboardBoard(LiveboardRenderState(presentation, inbox, sensitive, activity, search), actions,
        LiveboardRenderEnvironment.observed(dependencies.services.info.getAppVersion(), blur, dependencies.platform
            .reducedMotion, epoch, root.showObservationEmitter),
        Modifier.testTag("lb-root"))
}

private fun LiveboardRootCoordinator.showFromRendered(rendered: Boolean, secretId: String) {
    val observation = beginShowObservation(rendered)
    if (rendered) show(secretId, observation) else recordRejectedShowObservation(observation)
}
