package ui.liveboard.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import core.AppString
import core.appString
import core.metaSecretCore.liveboard.LiveboardActionTarget
import core.metaSecretCore.liveboard.LiveboardKind
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderState
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardSurfaceStyle
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport
import ui.liveboard.visual.LiveboardStyleBindings
import ui.liveboard.visual.LiveboardInsets
import ui.liveboard.visual.LiveboardSurfaceInteraction
import ui.liveboard.visual.LiveboardInteractionKind
import ui.liveboard.visual.liveboardInsets
import androidx.compose.ui.draw.drawBehind

@Composable
fun VaultTile(state: LiveboardRenderState, actions: LiveboardRenderActions, onInviteAnchor: (Rect) -> Unit,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    val content = state.root.content ?: return
    val member = content.member
    val viewport = LocalLiveboardViewport.current
    val devices = member.devices.sortedByDescending { it.current }
    var preview by remember(content.session) { mutableStateOf<Int?>(null) }
    var removal by remember(content.session) { mutableStateOf<String?>(null) }
    var consumed by remember(content.session) { mutableStateOf(emptySet<String>()) }
    val anchors = remember { mutableStateMapOf<String, Offset>() }
    var slotsOrigin by remember { mutableStateOf(Offset.Zero) }
    val operation = visibleVaultOperation(member, consumed, state.activity.actions)
    val selected = devices.firstOrNull { it.deviceId == removal && it.canRemove && !it.current }
    val pending = selected?.let { target -> state.activity.actions.any { it.target == LiveboardActionTarget.Remove(
        target.deviceId) } } == true
    val forecast = if (selected != null) (devices.size - 1).coerceAtLeast(1) else preview
    val mode = if (forecast != null) when (forecast) { 1 -> "whole"; 2 -> "copies"; else -> "shares" } else member
        .permissions.storageMode
    LaunchedEffect(devices.map { it.deviceId }, removal) {
        if (removal != null && devices.none { it.deviceId == removal }) removal = null
    }
    LaunchedEffect(member.operations, state.sensitive?.revealed?.keys) {
        consumed = consumed + member.operations.filter { item -> item.identity.kind == LiveboardKind.Recover &&
            member.secrets.any { it.identity.passId in state.sensitive?.revealed.orEmpty() && it.identity.passId ==
                item.identity.secretId && it.primary.claimId == item.identity.requestId }
        }.map { it.identity.requestId }
    }
    val minimum = LiveboardStyleBindings.dimension(".tile-vault", "min-height", viewport, T
        .liveboardDimensionVaultHeight)
    val padding = liveboardInsets(".tile-vault", viewport, liveboardInsets(".tile", viewport,
        LiveboardInsets(T.liveboardDimensionTilePadY, T.liveboardDimensionTilePadX, T.liveboardDimensionTilePadY, T
            .liveboardDimensionTilePadX)))
    LiveboardSurface(modifier.heightIn(min = minimum).testTag("lb-vault"), LiveboardSurfaceStyle(border = T
        .liveboardColorVaultBorder), LiveboardSurfaceInteraction(moves = false)) {
        Column(Modifier.fillMaxWidth().padding(start = padding.left, top = padding.top, end = padding.right,
            bottom = padding.bottom), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment =
                Alignment.CenterVertically) {
                LiveboardDots(appString(AppString.lbVault), height = if (viewport.compact) 22.dp else 24.dp)
                LiveboardSurface(Modifier.size(T.liveboardDimensionControl).onGloballyPositioned { onInviteAnchor(it
                    .boundsInRoot()) }
                    .testTag("lb-invite"),
                    LiveboardSurfaceStyle(LiveboardMaterials.addDevice, 21.dp, T.liveboardColorVaultBorder, false),
                    LiveboardSurfaceInteraction(actions.navigation.invite, kind = LiveboardInteractionKind.Plus)) {
                    LiveboardIcon("plus", Modifier.size(19.dp).align(Alignment.Center), T.liveboardColorDevice)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = if (viewport.wide) 38.dp else 20.dp).onGloballyPositioned {
                slotsOrigin = it.boundsInRoot().topLeft }) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(PREVIEW_SLOT_COUNT) { index ->
                        val device = devices.getOrNull(index)
                        val isRequester = operation != null && device?.deviceId == operation.identity.requesterId
                        val isResponder = operation != null && device?.deviceId == operation.status.responderId
                        val slotStatus = if (operation != null && (isRequester || isResponder)) DeviceSlotStatus(
                            appString(when (operation.status.outcome) { "accepted" -> AppString.orbitApproved;
                                "declined" -> AppString.orbitDeclined; else -> AppString.lbPending }), isRequester,
                                operation.status.outcome) else null
                        DeviceSlot(DeviceSlotContent(index, device, preview != null && index < (preview ?: 0) &&
                            device == null,
                            selected?.deviceId == device?.deviceId && selected != null, slotStatus), {
                            if (device?.current == true) { preview = null; removal = null }
                            else if (device?.canRemove == true) { removal = device.deviceId; preview = null }
                            else if (device == null) { preview = index + 1; removal = null }
                        }, { point -> if (device != null) anchors[device.deviceId] = point - slotsOrigin },
                            environment, Modifier.weight(1f).drawBehind {
                            if (index > 0) drawLine(T.liveboardColorStorageRule.copy(alpha = .2f), Offset.Zero,
                                Offset(0f, size.height), 1.dp.toPx())
                        })
                    }
                }
                DeviceLinks(member, operation, anchors, Modifier.matchParentSize())
            }
            if (operation != null) {
                val subject = member.secrets.firstOrNull { it.identity.passId == operation.identity.secretId }
                    ?.identity?.name
                    ?: member.devices.firstOrNull { it.deviceId == operation.identity.targetDeviceId }?.name.orEmpty()
                Column(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 16.dp)) {
                    Text(appString(when (operation.identity.kind) { LiveboardKind.RemoveDevice -> AppString
                        .lbRemoveRequest; LiveboardKind.DeleteSecret -> AppString.lbDeleteRequest; else -> AppString
                        .lbRecoverRequest }).uppercase(),
                        style = LiveboardTypography.label(T.liveboardFontSizeSlotHeading))
                    Text(subject, style = LiveboardTypography.text(T.liveboardFontSizeFormValue), modifier =
                        Modifier.padding(top = 8.dp))
                    Text(appString(when (operation.status.outcome) { "accepted" -> AppString.orbitApproved;
                        "declined" -> AppString.orbitDeclined; else -> AppString.lbPending }),
                        style = LiveboardTypography.text(T.liveboardFontSizeStorageRule, when (operation.status
                            .outcome) { "accepted" -> T.liveboardColorApproved; "declined" -> T.liveboardColorError;
                            else -> T.liveboardColorMuted }))
                }
            }
            StorageInfo(StoragePresentation(mode, forecast != null, forecast ?: devices.size), Modifier.padding(
                top = if (viewport.wide) 30.dp else 18.dp)) {
                if (forecast != null) Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    LiveboardControl(appString(AppString.lbCurrent), "current", { preview = null; removal = null },
                        LiveboardControlOptions(!pending, tag = "lb-current"))
                    if (selected != null) LiveboardControl(appString(AppString.lbRemove), "remove", { actions.vault
                        .removeDevice(selected.deviceId) },
                        LiveboardControlOptions(state.inbox?.ready == true && !pending, destructive = true,
                            pending = pending, tag = "lb-remove-${selected.deviceId}"))
                }
            }
        }
    }
}

private const val PREVIEW_SLOT_COUNT: Int = 3
