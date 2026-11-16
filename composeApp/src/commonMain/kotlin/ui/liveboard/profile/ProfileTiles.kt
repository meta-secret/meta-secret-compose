package ui.liveboard.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.widthIn
import core.AppString
import core.appString
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardTileHeading
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LocalLiveboardViewport
import ui.liveboard.visual.LiveboardSurfaceInteraction
import ui.liveboard.visual.liveboardTilePadding

@Composable
fun ProfileIdentity(state: LiveboardRenderState, modifier: Modifier = Modifier) {
    val member = state.root.content?.member ?: return
    val viewport = LocalLiveboardViewport.current
    val compact = viewport.width <= COMPACT_PROFILE_WIDTH
    val name: @Composable () -> Unit = {
        Column {
            Text(appString(AppString.lbName), style = LiveboardTypography.text(11.sp, T.liveboardColorMuted))
            Text(member.identity.vaultName, style = LiveboardTypography.heading(if (compact) 22.sp else (viewport
                .width * HEADING_WIDTH_RATIO).coerceIn(MIN_HEADING_SIZE, MAX_HEADING_SIZE).sp), modifier = Modifier
                .padding(top = 9.dp))
        }
    }
    val metrics: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().drawBehind {
            if (compact) drawLine(T.liveboardColorStorageRule.copy(alpha = .2f), Offset.Zero, Offset(size.width, 0f
                ), 1.dp.toPx())
            else drawLine(T.liveboardColorStorageRule.copy(alpha = .2f), Offset.Zero, Offset(0f, size.height), 1.dp
                .toPx())
        }.padding(start = if (compact) 0.dp else 28.dp, top = if (compact) 21.dp else 0.dp), horizontalArrangement =
            Arrangement.spacedBy(32.dp)) {
            listOf(member.devices.size to AppString.lbDevices, member.secrets.size to AppString.lbSecrets)
                .forEachIndexed { index, item ->
                Column(Modifier.weight(1f).widthIn(min = if (compact) 0.dp else 60.dp).then(if (index == 1 &&
                    compact) Modifier.drawBehind {
                    drawLine(T.liveboardColorStorageRule.copy(alpha = .2f), Offset.Zero, Offset(0f, size.height), 1
                        .dp.toPx())
                }.padding(start = 25.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    LiveboardDots(item.first.toString().padStart(2, '0'), height = if (compact) 33.dp else 34.dp)
                    Text(appString(item.second), style = LiveboardTypography.text(T.liveboardFontSizeProfileLabel, T
                        .liveboardColorMuted))
                }
            }
        }
    }
    LiveboardSurface(modifier.fillMaxWidth(), interaction = LiveboardSurfaceInteraction(moves = false)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) androidx.compose.foundation.layout.PaddingValues(20.dp
            ) else liveboardTilePadding())) {
            LiveboardTileHeading("01", appString(AppString.lbVault))
            if (compact) Column(Modifier.padding(top = 23.dp)) {
                name(); androidx.compose.foundation.layout.Box(Modifier.padding(top = 22.dp)) { metrics() }
            } else Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(28
                .dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { name() }
                androidx.compose.foundation.layout.Box(Modifier.widthIn(max = 220.dp)) { metrics() }
            }
        }
    }
}

@Composable
fun ProfileAbout(environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    LiveboardSurface(modifier.fillMaxWidth(), interaction = LiveboardSurfaceInteraction(moves = false)) {
        Column(Modifier.fillMaxWidth().padding(if (LocalLiveboardViewport.current.width <= COMPACT_PROFILE_WIDTH
            ) androidx.compose.foundation.layout.PaddingValues(20.dp) else liveboardTilePadding())) {
            LiveboardTileHeading("02", appString(AppString.lbApplication))
            Text(appString(AppString.orbitBrand), style = LiveboardTypography.heading(23.sp), modifier = Modifier
                .padding(top = 24.dp))
            Row(Modifier.fillMaxWidth().padding(top = 22.dp).drawBehind { drawLine(T.liveboardColorStorageRule.copy(
                alpha = .17f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(
                bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appString(AppString.lbVersion), style = LiveboardTypography.text(T
                    .liveboardFontSizeProfileLabel, T.liveboardColorMuted))
                Text(environment.appVersion, style = LiveboardTypography.text(T.liveboardFontSizeProfileLabel))
            }
            Text(appString(AppString.poweredBy), style = LiveboardTypography.text(T.liveboardFontSizeFoot, T
                .liveboardColorMuted), modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
fun ProfileReset(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiveboardSurface(modifier.fillMaxWidth().testTag("lb-profile-reset"), interaction = LiveboardSurfaceInteraction(
        onClick)) {
        Column(Modifier.fillMaxWidth().padding(if (LocalLiveboardViewport.current.width <= COMPACT_PROFILE_WIDTH
            ) androidx.compose.foundation.layout.PaddingValues(20.dp) else liveboardTilePadding())) {
            LiveboardTileHeading("03", appString(AppString.lbLocalData))
            Row(Modifier.fillMaxWidth().padding(top = 27.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                LiveboardDots(appString(AppString.lbReset), height = 17.dp, color = T.liveboardColorError)
                LiveboardIcon("reset", Modifier.size(26.dp), T.liveboardColorError, moving = false)
            }
            Text(appString(AppString.lbResetDescription), style = LiveboardTypography.text(11.sp, T
                .liveboardColorMuted).copy(lineHeight = 19.25.sp), modifier = Modifier.padding(top = 18.dp))
        }
    }
}

private const val COMPACT_PROFILE_WIDTH: Float = 620f
private const val HEADING_WIDTH_RATIO: Float = .027f
private const val MIN_HEADING_SIZE: Float = 18f
private const val MAX_HEADING_SIZE: Float = 28f
