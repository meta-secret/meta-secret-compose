package ui.liveboard.vault

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import core.metaSecretCore.liveboard.LiveboardKind
import models.apiModels.liveboard.LiveboardOperation
import models.apiModels.liveboard.LiveboardSnapshot
import core.metaSecretCore.liveboard.LiveboardActionPresentation
import core.metaSecretCore.liveboard.LiveboardActionTarget
import androidx.compose.ui.graphics.PathMeasure
import ui.design.generated.LiveboardMotionPoint
import ui.design.generated.LiveboardScriptedGeometry
import ui.design.generated.LiveboardTokens as T
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardDuration
import ui.liveboard.visual.liveboardCycle
import androidx.compose.ui.unit.dp

/** A drawing selection never grants permission or treats a receipt/animation as canonical completion. */
fun visibleVaultOperation(member: LiveboardSnapshot, consumed: Set<String>,
    activity: List<LiveboardActionPresentation>): LiveboardOperation? {
    val candidates = member.operations.filter { operation ->
    val secret = member.secrets.firstOrNull { it.identity.passId == operation.identity.secretId }
    val activeTarget = when (operation.identity.kind) {
        LiveboardKind.Recover -> operation.identity.secretId?.let { LiveboardActionTarget.Recover(it) }
        LiveboardKind.DeleteSecret -> operation.identity.secretId?.let { LiveboardActionTarget.Delete(it) }
        LiveboardKind.RemoveDevice -> operation.identity.targetDeviceId?.let { LiveboardActionTarget.Remove(it) }
        else -> null
    }
    val issued = activity.filter { it.target == activeTarget }
    val exactRequest = when (operation.identity.kind) {
        LiveboardKind.Recover -> secret?.primary?.claimId ?: issued.singleOrNull()?.requestId
        LiveboardKind.DeleteSecret -> secret?.deletionRequestId ?: issued.singleOrNull()?.requestId
        else -> issued.singleOrNull()?.requestId
    }
    val matching = exactRequest == null || exactRequest == operation.identity.requestId
    val supersededTerminal = operation.status.outcome != null && issued.isNotEmpty() && exactRequest == null
    operation.identity.requesterId == member.identity.currentDeviceId &&
        operation.identity.kind in setOf(LiveboardKind.Recover, LiveboardKind.DeleteSecret, LiveboardKind
            .RemoveDevice) &&
        operation.identity.requestId !in consumed && matching && !supersededTerminal &&
        (operation.identity.kind == LiveboardKind.RemoveDevice || operation.status.clientStatus != "done") &&
        !(operation.identity.kind == LiveboardKind.RemoveDevice && operation.status.outcome == "accepted" &&
            operation.status.clientStatus == "done" && operation.status.readiness in setOf("done", "settled")) &&
        (operation.status.outcome == null || operation.status.responderId != null && operation.status
            .resolutionSource != "unknown")
    }
    val pending = candidates.filter { it.status.outcome == null }
    return pending.firstOrNull() ?: candidates.singleOrNull()
}

@Composable
fun DeviceLinks(member: LiveboardSnapshot, operation: LiveboardOperation?, anchors: Map<String, Offset>,
    modifier: Modifier = Modifier) {
    if (operation == null) return
    val requester = anchors[operation.identity.requesterId] ?: return
    val reply = operation.status.outcome != null
    val peerIds = if (reply) (operation.permissions.eligiblePeerIds + listOfNotNull(operation.status.responderId))
        .distinct().filter { it != operation.identity.requesterId }
        else operation.permissions.eligiblePeerIds
    val devices = member.devices.sortedByDescending { it.current }
    val start = Triple(operation.identity, operation.status.outcome, operation.status.responderId)
    val phases = peerIds.associateWith { id ->
        val index = devices.indexOfFirst { it.deviceId == id }.coerceAtLeast(1)
        val duration = if (reply) (LiveboardTimelines.reply.timing.duration as LiveboardDuration.Fixed).ms
            else (LiveboardTimelines.request.timing.duration as LiveboardDuration.ByPeer).at(index.coerceAtMost(2))
        liveboardCycle(duration, startKey = start)
    }
    Canvas(modifier) {
        peerIds.forEach { id ->
            val peer = anchors[id] ?: return@forEach
            val active = !reply || id == operation.status.responderId
            val from = if (reply && active) peer else requester
            val to = if (reply && active) requester else peer
            val index = devices.indexOfFirst { it.deviceId == id }.coerceAtLeast(1)
            val curve = LiveboardScriptedGeometry.requestPath.curve(LiveboardMotionPoint(from.x / density, from.y /
                density), LiveboardMotionPoint(to.x / density, to.y / density), index)
            val control = Offset(curve.control.x * density, curve.control.y * density)
            val color = when (operation.status.outcome) { "accepted" -> T.liveboardColorApproved; "declined" -> T
                .liveboardColorError; else -> T.liveboardColorRequest }
            val path = Path().apply { moveTo(from.x, from.y); quadraticBezierTo(control.x, control.y, to.x, to.y) }
            drawPath(path, (if (active) color else T.liveboardColorRequest).copy(alpha = if (!active) .08f else if (
                reply) T.liveboardNumberReplyPathOpacity else T.liveboardNumberRequestPathOpacity),
                style = Stroke(T.liveboardNumberRequestPathWidth.dp.toPx(), pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(1.dp.toPx(), 5.dp.toPx()))))
            val t = phases.getValue(id)
            val measure = PathMeasure().also { it.setPath(path, false) }
            val point = measure.getPosition(t * measure.length)
            if (active) drawCircle(color, (if (reply) T.liveboardNumberReplyRadius else T
                .liveboardNumberRequestRadius).dp.toPx(), point)
        }
    }
}
