package ui.liveboard.layout

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ui.design.generated.LiveboardMotionRect
import ui.design.generated.LiveboardMotionSize
import ui.design.generated.LiveboardScriptedGeometry
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardDuration
import ui.design.generated.LiveboardDelay
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.LiveboardRootOverlay
import ui.liveboard.LiveboardRootRoute
import ui.liveboard.create.CreateSecretModal
import ui.liveboard.invite.InvitePopover
import ui.liveboard.invite.InvitePresentation
import ui.liveboard.secrets.SecretsSheet
import ui.liveboard.visual.LiveboardBackdropRequest
import ui.liveboard.visual.rememberLiveboardBackdrop
import ui.liveboard.visual.liveboardEasing
import androidx.compose.ui.input.pointer.pointerInput

/** Records only the board or an explicitly redacted sheet; foreground disclosure is never captured. */
@Composable
fun LiveboardOverlayHost(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, inviteAnchor: Rect?, background: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val overlay = state.root.chrome.overlay
        var retained by remember(state.root.content?.session) { mutableStateOf(LiveboardRootOverlay.None) }
        LaunchedEffect(overlay) { if (overlay != LiveboardRootOverlay.None) retained = overlay }
        val displaying = if (overlay != LiveboardRootOverlay.None) overlay else retained
        val open = overlay != LiveboardRootOverlay.None
        val timeline = when (displaying) {
            LiveboardRootOverlay.Invite -> if (open) LiveboardTimelines.inviteOpen else LiveboardTimelines.inviteClose
            else -> if (open) LiveboardTimelines.createOpen else LiveboardTimelines.createClose
        }
        val progress by animateFloatAsState(if (open) 1f else 0f,
            tween(if (environment.reducedMotion) 0 else (timeline.timing.duration as LiveboardDuration.Fixed).ms,
                easing = liveboardEasing(timeline.timing.easing)),
            finishedListener = { if (it == 0f && overlay == LiveboardRootOverlay.None) retained =
                LiveboardRootOverlay.None })
        val contentTiming = if (open && displaying == LiveboardRootOverlay.Invite) LiveboardTimelines
            .inviteContent else LiveboardTimelines.inviteContentClose
        val inviteAlpha by animateFloatAsState(if (open && displaying == LiveboardRootOverlay.Invite) 1f else 0f,
            tween(if (environment.reducedMotion) 0 else (contentTiming.timing.duration as LiveboardDuration.Fixed).ms,
                delayMillis = if (environment.reducedMotion) 0 else (contentTiming.timing.delay as LiveboardDelay
                    .Fixed).ms,
                easing = liveboardEasing(contentTiming.timing.easing)))
        val showSheet = state.root.chrome.route == LiveboardRootRoute.Secrets
        val sheetProgress by animateFloatAsState(if (showSheet) 1f else 0f,
            tween(if (environment.reducedMotion) 0 else ((if (showSheet) LiveboardTimelines
                .sheetOpen else LiveboardTimelines.sheetClose).timing.duration as LiveboardDuration.Fixed).ms,
                easing = if (showSheet) CubicBezierEasing(OPEN_EASING_X1, OPEN_EASING_Y1, OPEN_EASING_X2, 1f
                    ) else CubicBezierEasing(CLOSE_EASING_X1, 0f, 1f, 1f)))
        val layer = key(environment.captureEpoch) { rememberGraphicsLayer() }
        var bounds by remember { mutableStateOf(IntSize.Zero) }
        val nested = displaying != LiveboardRootOverlay.None
        val radius = if (displaying == LiveboardRootOverlay.Create) T.liveboardDimensionCreateBackdropBlur else T
            .liveboardDimensionInviteBackdropBlur
        val image = rememberLiveboardBackdrop(LiveboardBackdropRequest(environment.captureEpoch, radius, nested ||
            sheetProgress > 0f, bounds), environment.blur, layer)
        val surfaceImage = rememberLiveboardBackdrop(LiveboardBackdropRequest(environment.captureEpoch, T
            .liveboardDimensionInviteSurfaceBlur,
            displaying == LiveboardRootOverlay.Invite, bounds), environment.blur, layer)
        val sheetWidth = if (maxWidth >= 600.dp) minOf(680.dp, maxWidth - 48.dp) else maxWidth
        val sheetHeight = minOf(maxHeight - 24.dp, 884.dp)
        Box(Modifier.fillMaxSize().onSizeChanged { bounds = it }.then(if (nested || sheetProgress > 0f || showSheet
            ) Modifier.clearAndSetSemantics { } else Modifier)
            .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
            background()
            if (showSheet && nested) SecretsSheet(LiveboardRenderState(state.root, state.inbox, null, state
                .activity, state.search), actions, environment,
                Modifier.width(sheetWidth).height(sheetHeight).align(Alignment.BottomCenter).clearAndSetSemantics { })
        }
        if (nested || sheetProgress > 0f) {
            image?.let { Image(it, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale
                .FillBounds) }
            Box(Modifier.fillMaxSize().testTag("lb-overlay-shade").background(
                (if (displaying == LiveboardRootOverlay.Create) T.liveboardColorCreateBackdrop else if (nested) T
                    .liveboardColorInviteBackdrop else T.liveboardColorSheetBackdrop)
                    .copy(alpha = if (nested) (if (displaying == LiveboardRootOverlay.Create) T
                        .liveboardColorCreateBackdrop.alpha else T.liveboardColorInviteBackdrop.alpha) *
                        progress else T.liveboardColorSheetBackdrop.alpha * sheetProgress))
                .clickable(enabled = if (nested) open else showSheet, interactionSource = remember {
                    MutableInteractionSource() }, indication = null, onClick = actions.navigation.back))
        }
        if (sheetProgress > 0f && !nested) SecretsSheet(state, actions, environment, Modifier.width(sheetWidth)
            .height(sheetHeight)
            .then(if (!showSheet) Modifier.clearAndSetSemantics { } else Modifier)
            .align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - sheetProgress) * size.height;
                alpha = if (showSheet) SHEET_ALPHA_MIDPOINT + SHEET_ALPHA_MIDPOINT * sheetProgress else sheetProgress })
        if (nested) when (displaying) {
            LiveboardRootOverlay.Create -> CreateSecretModal(state, actions, environment, Modifier.align(Alignment
                .Center).padding(horizontal = 16.dp)
                .then(if (!open) Modifier.clearAndSetSemantics { } else Modifier)
                .graphicsLayer { alpha = progress; scaleX = INITIAL_MODAL_SCALE + MODAL_SCALE_DELTA * progress;
                    scaleY = scaleX; translationY = (1f - progress) * 22.dp.toPx() })
            LiveboardRootOverlay.Invite -> {
                val anchor = inviteAnchor ?: Rect(0f, 0f, with(density) { 42.dp.toPx() }, with(density) { 42.dp
                    .toPx() })
                val origin = with(density) { LiveboardMotionRect(anchor.left.toDp().value, anchor.top.toDp().value,
                    anchor.width.toDp().value, anchor.height.toDp().value) }
                val fit = LiveboardScriptedGeometry.invite.fit(origin, LiveboardMotionSize(maxWidth.value, maxHeight
                    .value), 0f)
                val width = origin.width + (fit.bounds.width - origin.width) * progress
                val height = origin.height + (fit.bounds.height - origin.height) * progress
                InvitePopover(state.root.content?.member?.identity?.vaultName.orEmpty(), InvitePresentation(fit,
                    inviteAlpha, surfaceImage,
                    with(density) { Offset((origin.right - width).dp.toPx(), origin.top.dp.toPx()) }, open), actions
                        .navigation.back,
                    Modifier.offset((origin.right - width).dp, origin.top.dp).size(width.dp, height.dp)
                        .then(if (!open) Modifier.clearAndSetSemantics { } else Modifier))
            }
            LiveboardRootOverlay.None -> Unit
        }
        if (nested && !open || sheetProgress > 0f && !showSheet) Box(Modifier.fillMaxSize().clearAndSetSemantics { }
            .pointerInput(displaying) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
}

private const val OPEN_EASING_X1: Float = .22f
private const val OPEN_EASING_Y1: Float = .8f
private const val OPEN_EASING_X2: Float = .24f
private const val CLOSE_EASING_X1: Float = .42f
private const val SHEET_ALPHA_MIDPOINT: Float = .5f
private const val INITIAL_MODAL_SCALE: Float = .97f
private const val MODAL_SCALE_DELTA: Float = .03f
