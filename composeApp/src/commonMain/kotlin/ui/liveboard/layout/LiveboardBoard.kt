package ui.liveboard.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.AppString
import core.appString
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.LiveboardRootRoute
import ui.liveboard.inbox.InboxTile
import ui.liveboard.profile.ProfileScreen
import ui.liveboard.vault.VaultTile
import ui.liveboard.visual.LiveboardArt
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardMotionClock
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardTileHeading
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LiveboardViewport
import ui.liveboard.visual.LocalLiveboardViewport
import ui.liveboard.visual.LiveboardStyleBindings
import ui.liveboard.visual.LiveboardSurfaceInteraction
import ui.liveboard.visual.liveboardTilePadding

/** Shared native presentation only: command and disclosure ownership remain in Session. */
@Composable
fun LiveboardBoard(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(T.liveboardColorBackground).windowInsetsPadding(
        WindowInsets.safeDrawing)) {
        val viewport = LiveboardViewport(maxWidth.value, maxHeight.value)
        var anchor by remember(state.root.content?.session) { mutableStateOf<Rect?>(null) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        CompositionLocalProvider(LocalLiveboardViewport provides viewport) {
            LiveboardMotionClock(environment.reducedMotion) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
                    LiveboardOverlayHost(state, actions, environment, anchor) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20
                            .dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.root.chrome.route == LiveboardRootRoute.Profile) {
                                ProfileScreen(state, actions, environment, Modifier.widthIn(max = T
                                    .liveboardDimensionProfileMaxWidth))
                            } else {
                                Column(Modifier.widthIn(max = 1256.dp).fillMaxWidth().testTag("lb-home")) {
                                    Header(state)
                                    if (viewport.wide) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(
                                            if (viewport.expanded) 22.dp else 18.dp), verticalAlignment = Alignment
                                            .Top) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(
                                                if (viewport.expanded) 26.dp else 22.dp)) {
                                                SecretsCount(state, actions, Modifier.fillMaxWidth())
                                                CreateTile(state, actions, Modifier.fillMaxWidth())
                                            }
                                            VaultTile(state, actions, { anchor = it.translate(-origin) },
                                                environment, Modifier.weight(VAULT_TILE_WEIGHT))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(
                                                if (viewport.expanded) 26.dp else 22.dp)) {
                                                InboxTile(state, actions, environment)
                                                ProfileTile(actions)
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(if (viewport.compact) 16
                                            .dp else 20.dp)) {
                                            VaultTile(state, actions, { anchor = it.translate(-origin) },
                                                environment, Modifier.fillMaxWidth())
                                            Row(horizontalArrangement = Arrangement.spacedBy(if (viewport.compact
                                                ) 12.dp else 16.dp)) {
                                                SecretsCount(state, actions, Modifier.weight(1f))
                                                CreateTile(state, actions, Modifier.weight(1f))
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(if (viewport.compact
                                                ) 12.dp else 16.dp), verticalAlignment = Alignment.Top) {
                                                InboxTile(state, actions, environment, Modifier.weight(1f))
                                                ProfileTile(actions, Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: LiveboardRenderState) {
    val viewport = LocalLiveboardViewport.current
    val heading: @Composable () -> Unit = {
        Column {
            Text(appString(AppString.lbYourVault), style = LiveboardTypography.label(T.liveboardFontSizeEyebrow))
            Text(appString(AppString.orbitBrand), style = LiveboardTypography.wordmark((viewport.width *
                WORDMARK_WIDTH_RATIO).coerceIn(MIN_WORDMARK_SIZE, MAX_WORDMARK_SIZE).sp), modifier = Modifier
                .padding(top = 7.dp))
        }
    }
    val gap = LiveboardStyleBindings.dimension(".app-header", "margin-bottom", viewport, 27.dp)
    if (viewport.compact) Column(Modifier.fillMaxWidth().padding(bottom = gap), verticalArrangement = Arrangement
        .spacedBy(11.dp)) {
        heading()
        Text(state.root.content?.member?.identity?.vaultName.orEmpty(), style = LiveboardTypography.text(T
            .liveboardFontSizeFoot, T.liveboardColorMuted))
    } else Row(Modifier.fillMaxWidth().padding(bottom = gap), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.weight(1f)) { heading() }
        Text(state.root.content?.member?.identity?.vaultName.orEmpty(), style = LiveboardTypography.text(T
            .liveboardFontSizeVaultName, T.liveboardColorMuted), modifier = Modifier.weight(1f), textAlign =
            androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun SecretsCount(state: LiveboardRenderState, actions: LiveboardRenderActions, modifier: Modifier = Modifier) {
    val viewport = LocalLiveboardViewport.current
    val height = LiveboardStyleBindings.dimension(".tile-count", "min-height", viewport, 124.dp)
    val countHeight = LiveboardStyleBindings.dimension(".count svg", "height", viewport, 45.dp)
    val padding = liveboardTilePadding()
    LiveboardSurface(modifier.heightIn(min = height).testTag("lb-open-secrets"),
        interaction = LiveboardSurfaceInteraction(actions.navigation.secrets, selected = state.root.chrome.route ==
            LiveboardRootRoute.Secrets)) {
        Column(Modifier.fillMaxWidth().padding(padding).heightIn(min = height - padding.calculateTopPadding() -
            padding.calculateBottomPadding()), verticalArrangement = Arrangement.SpaceBetween) {
            LiveboardTileHeading("02", appString(AppString.lbSecrets))
            LiveboardDots(state.root.content?.member?.secrets?.size?.toString()?.padStart(2, '0') ?: "00", height =
                countHeight)
            Text(appString(AppString.lbSecrets), style = LiveboardTypography.text(if (viewport.compact) T
                .liveboardFontSizeFootCompact else T.liveboardFontSizeFoot, T.liveboardColorMuted))
        }
    }
}

@Composable
private fun CreateTile(state: LiveboardRenderState, actions: LiveboardRenderActions, modifier: Modifier = Modifier) {
    val viewport = LocalLiveboardViewport.current
    val height = LiveboardStyleBindings.dimension(".tile-action", "min-height", viewport, 124.dp)
    val padding = liveboardTilePadding()
    LiveboardSurface(modifier.heightIn(min = height).testTag("lb-open-create"),
        interaction = LiveboardSurfaceInteraction(actions.create.open, state.activity.create.canOpen, state.root
            .chrome.overlay == ui.liveboard.LiveboardRootOverlay.Create)) {
        Column(Modifier.fillMaxWidth().padding(padding).heightIn(min = height - padding.calculateTopPadding() -
            padding.calculateBottomPadding()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {
            LiveboardTileHeading("03", appString(AppString.lbCreate), Modifier.align(Alignment.Start))
            LiveboardArt("secret", Modifier.size(LiveboardStyleBindings.dimension(".action-art svg", "height",
                viewport, 41.dp)))
            LiveboardDots(appString(AppString.lbCreate), height = LiveboardStyleBindings.dimension(
                ".action-name svg", "height", viewport, 12.dp))
        }
    }
}

@Composable
private fun ProfileTile(actions: LiveboardRenderActions, modifier: Modifier = Modifier) {
    LiveboardSurface(modifier.testTag("lb-open-profile"), interaction = LiveboardSurfaceInteraction(actions
        .navigation.profile)) {
        Column(Modifier.fillMaxWidth().padding(liveboardTilePadding()), verticalArrangement = Arrangement.spacedBy(
            14.dp)) {
            LiveboardTileHeading("05", appString(AppString.profile))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveboardArt("settings", Modifier.size(LiveboardStyleBindings.dimension(".utility-art svg",
                    "height", LocalLiveboardViewport.current, 43.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveboardDots(appString(AppString.profile), height = 14.dp)
                    Text(appString(AppString.lbVaultDetails), style = LiveboardTypography.text(if (
                        LocalLiveboardViewport.current.compact) T.liveboardFontSizeFootCompact else T
                        .liveboardFontSizeFoot, T.liveboardColorMuted))
                }
            }
        }
    }
}

private const val VAULT_TILE_WEIGHT: Float = 1.65f
private const val WORDMARK_WIDTH_RATIO: Float = .033f
private const val MIN_WORDMARK_SIZE: Float = 25f
private const val MAX_WORDMARK_SIZE: Float = 38f
