package ui.liveboard.inbox

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import core.metaSecretCore.liveboard.LiveboardActionTarget
import core.metaSecretCore.liveboard.LiveboardKind
import models.apiModels.liveboard.LiveboardOperation
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardControlSize
import ui.liveboard.visual.LiveboardControlPalette
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardSurfaceStyle
import ui.liveboard.visual.LiveboardSurfaceInteraction
import ui.liveboard.visual.LiveboardTileHeading
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport
import ui.liveboard.visual.liveboardCycle
import ui.liveboard.visual.liveboardGradient
import ui.liveboard.visual.liveboardPose
import kotlinx.coroutines.delay
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardDuration

private fun incomingKey(operation: LiveboardOperation) = "${operation.identity.kind}:${operation.identity.requestId}"

@Composable
fun InboxTile(state: LiveboardRenderState, actions: LiveboardRenderActions, environment: LiveboardRenderEnvironment,
    modifier: Modifier = Modifier) {
    val content = state.root.content ?: return
    val current = state.inbox?.queue.orEmpty().filter { it.permissions.canApprove || it.permissions.canDecline }
        .distinctBy(::incomingKey)
    var order by remember(content.session) { mutableStateOf(emptyList<String>()) }
    var expanded by remember(content.session) { mutableStateOf<String?>(null) }
    var decision by remember(content.session) { mutableStateOf<String?>(null) }
    var seen by remember(content.session) { mutableStateOf(emptySet<String>()) }
    val byKey = current.associateBy(::incomingKey)
    val keys = order.filter { it in byKey } + byKey.keys.filter { it !in order }
    val first = keys.firstOrNull()?.let(byKey::get)
    val firstKey = first?.let(::incomingKey)
    LaunchedEffect(keys) { order = keys; if (expanded != null && expanded !in byKey) expanded = null }
    val error = state.inbox?.error
    val receipt = content.member.operations.firstOrNull { incomingKey(it) == decision && incomingKey(it) !in seen &&
        it.status.outcome != null && it.status.resolutionSource != "unknown" && it.status.readiness in setOf("done",
            "settled") }
    LaunchedEffect(receipt?.identity, error) {
        if (receipt != null && error == null) {
            delay((LiveboardTimelines.notificationReceipt.timing.duration as LiveboardDuration.Fixed).ms.toLong())
            seen = seen + incomingKey(receipt)
            decision = null
        }
    }
    val busy = first?.let { item -> state.activity.actions.any { it.target == LiveboardActionTarget.Decision(item
        .identity) } } == true
    val open = firstKey != null && expanded == firstKey
    val appear = remember { Animatable(0f) }
    LaunchedEffect(firstKey, error, open, receipt?.identity) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(if (environment.reducedMotion) 0 else T.liveboardDurationNoticeAppear, easing =
            CubicBezierEasing(APPEAR_EASING_X, APPEAR_EASING_Y, APPEAR_EASING_X, 1f)))
    }
    val wash = if (error != null) LiveboardMaterials.error else LiveboardMaterials.notification
    val cycle = liveboardCycle(if (error != null) T.liveboardDurationErrorWash else T.liveboardDurationNoticeWash)
    val viewport = LocalLiveboardViewport.current
    val padX = if (viewport.compact) T.liveboardDimensionCompactTilePadX else T.liveboardDimensionTilePadX
    val padY = if (viewport.compact) T.liveboardDimensionCompactTilePadY else T.liveboardDimensionTilePadY
    LiveboardSurface(modifier.fillMaxWidth().testTag("lb-inbox").animateContentSize(tween(if (environment
        .reducedMotion) 0 else T.liveboardDurationNotificationHeight, easing = CubicBezierEasing(HEIGHT_EASING_X1,
        HEIGHT_EASING_Y1, HEIGHT_EASING_X2, 1f)))
        , LiveboardSurfaceStyle(border = if (error != null) T.liveboardColorError.copy(alpha = .42f) else T
            .liveboardColorTileBorder), LiveboardSurfaceInteraction(onClick = {
            if (error != null) actions.inbox.dismissError() else expanded = firstKey
        }, enabled = error != null || first != null, moves = false)) {
        Box(Modifier.matchParentSize().drawBehind {
            if (error != null || first != null) {
                val shift = size.width * (SWEEP_START - cycle * SWEEP_DISTANCE)
                drawRect(liveboardGradient(wash, size, Offset(shift, 0f)))
            }
        })
        Column(Modifier.fillMaxWidth().padding(horizontal = padX, vertical = padY).semantics { liveRegion =
            LiveRegionMode.Polite }) {
            LiveboardTileHeading("04", appString(AppString.lbNotifications))
            Box(Modifier.padding(top = T.liveboardDimensionNoticeContentTop).size(T.liveboardDimensionNoticeIconSize)) {
                LiveboardIcon(if (error != null) "error" else "bell", Modifier.size(T.liveboardDimensionNoticeIconSize),
                    if (error != null) T.liveboardColorError else T.liveboardColorNoticeIcon, moving = false)
                if (error == null && keys.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (
                    -4).dp).size(16.dp)
                    .background(T.liveboardColorNoticeBadge, CircleShape), contentAlignment = Alignment.Center) {
                    Text(keys.size.toString(), style = LiveboardTypography.text(T
                        .liveboardFontSizeSlotHeadingCompact, T.liveboardColorNoticeBadgeInk), modifier = Modifier
                        .testTag("lb-inbox-count"))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = T.liveboardDimensionNoticeIconCopyGap)
                .testTag(if (error == null && receipt == null && first != null
                    ) "lb-inbox-head-${first.identity.kind.name}-${first.identity.requestId}" else "lb-inbox-copy")
                .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 3.dp.toPx() }) {
                when {
                    error != null -> {
                        LiveboardDots(appString(AppString.lbError), height = 13.dp, color = T.liveboardColorError)
                        Text(error, style = LiveboardTypography.text(T.liveboardFontSizeNoticeDetail, T
                            .liveboardColorError), modifier = Modifier.padding(top = 10.dp))
                    }
                    receipt != null -> {
                        LiveboardDots(appString(if (receipt.status.outcome == "accepted") AppString
                            .orbitApproved else AppString.orbitDeclined), height = 13.dp)
                        Text(content.deviceLabels[receipt.identity.requesterId].orEmpty(), style =
                            LiveboardTypography.text(T.liveboardFontSizeNoticeDetail, T.liveboardColorMuted),
                            modifier = Modifier.padding(top = 10.dp))
                    }
                    first == null -> {
                        LiveboardDots(appString(AppString.lbNoActions), height = 13.dp)
                        Text(appString(AppString.lbNoActionsWaiting), style = LiveboardTypography.text(T
                            .liveboardFontSizeFoot, T.liveboardColorMuted), modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> {
                        val title = appString(when (first.identity.kind) {
                            LiveboardKind.Join -> AppString.lbJoinRequest
                            LiveboardKind.DeleteSecret -> AppString.lbDeleteRequest
                            LiveboardKind.RemoveDevice -> AppString.lbRemoveRequest
                            else -> AppString.lbRecoverRequest
                        })
                        val sender = content.deviceLabels[first.identity.requesterId] ?: content.member.devices
                            .firstOrNull { it.deviceId == first.identity.requesterId }?.name.orEmpty()
                        val subject = content.member.secrets.firstOrNull { it.identity.passId == first.identity
                            .secretId }?.identity?.name
                            ?: first.identity.targetDeviceId?.let(content.deviceLabels::get)
                        if (!open) LiveboardDots(title, height = T.liveboardFontSizeNoticeTitleDots.value.dp)
                        Text(sender, style = LiveboardTypography.text(T.liveboardFontSizeNoticeRequestDevice),
                            modifier = Modifier.padding(top = if (open) 0.dp else 10.dp).testTag("lb-request-sender"))
                        if (!subject.isNullOrEmpty()) Text(subject, style = LiveboardTypography.text(T
                            .liveboardFontSizeNoticeDetail, T.liveboardColorMuted), modifier = Modifier.testTag(
                            "lb-request-subject"))
                        if (open && busy) {
                            NoticeLoader(Modifier.padding(top = 10.dp))
                            Text(appString(AppString.lbPending), style = LiveboardTypography.text(T
                                .liveboardFontSizeNoticeDetail, T.liveboardColorMuted), modifier = Modifier.padding(
                                top = 10.dp))
                        } else if (open) Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement =
                            Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                            val size = LiveboardControlSize(T.liveboardDimensionNoticeControl, T
                                .liveboardDimensionNoticeIcon, LiveboardControlPalette.Notice)
                            LiveboardControl(appString(AppString.lbApprove), "approve", { decision = firstKey;
                                actions.inbox.decide(first.identity, true) },
                                LiveboardControlOptions(state.inbox?.ready == true && first.permissions
                                    .canApprove && !busy, pending = busy,
                                    tag = "lb-approve-${first.identity.requestId}", size = size))
                            LiveboardControl(appString(AppString.decline), "decline", { decision = firstKey; actions
                                .inbox.decide(first.identity, false) },
                                LiveboardControlOptions(state.inbox?.ready == true && first.permissions
                                    .canDecline && !busy,
                                    tag = "lb-decline-${first.identity.requestId}", size = size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeLoader(modifier: Modifier = Modifier) {
    val poses = (0 until LOADING_DOT_COUNT).map { liveboardPose("notification-loading", delayMs = it * 120) }
    Canvas(modifier.size(72.dp, 24.dp)) {
        poses.forEachIndexed { index, pose ->
            val x = size.width / 2 + (index - 2) * 8.dp.toPx()
            val height = 16.dp.toPx() * pose.scale.second
            drawLine(T.liveboardColorNoticeAction.copy(alpha = pose.opacity), Offset(x, (size.height - height) / 2),
                Offset(x, (size.height + height) / 2), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

private const val APPEAR_EASING_X: Float = .25f
private const val APPEAR_EASING_Y: Float = .1f
private const val HEIGHT_EASING_X1: Float = .22f
private const val HEIGHT_EASING_Y1: Float = .8f
private const val HEIGHT_EASING_X2: Float = .24f
private const val SWEEP_START: Float = 1.5f
private const val SWEEP_DISTANCE: Float = 3f
private const val LOADING_DOT_COUNT: Int = 5
