package ui.liveboard.secrets

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.liveboard_brand
import org.jetbrains.compose.resources.painterResource
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardControlSize
import ui.liveboard.visual.LiveboardControlPalette
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardStyleBindings
import ui.liveboard.visual.liveboardSheetStyle
import androidx.compose.ui.draw.clip
import ui.liveboard.visual.LocalLiveboardViewport

@Composable
fun SecretsSheet(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    val member = state.root.content?.member ?: return
    val secrets = member.secrets.filter { it.identity.name.contains(state.search, ignoreCase = true) }
    val viewport = LocalLiveboardViewport.current
    val columns = if (viewport.width >= TWO_COLUMN_WIDTH) 2 else 1
    val noMatches = member.secrets.isNotEmpty() && secrets.isEmpty()
    LiveboardSurface(modifier.fillMaxSize().testTag("lb-secrets"), liveboardSheetStyle(viewport)) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp)).padding(horizontal = 22
        .dp)) {
        Box(Modifier.padding(top = 12.dp, bottom = if (viewport.short) 10.dp else 21.dp).size(35.dp, 3.dp).align(
            Alignment.CenterHorizontally)
            .background(T.liveboardColorStorageRule.copy(alpha = .65f), RoundedCornerShape(9.dp)))
        Row(Modifier.fillMaxWidth().padding(bottom = if (viewport.short) 15.dp else 23.dp), verticalAlignment =
            Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LiveboardControl(appString(AppString.orbitBack), "back", actions.navigation.back,
                LiveboardControlOptions(tag = "lb-secrets-back", size = LiveboardControlSize(44.dp, 21.dp,
                    LiveboardControlPalette.Secret, caption = false)))
            Column(Modifier.weight(1f)) {
                Text(appString(AppString.lbYourVault), style = LiveboardTypography.label(T.liveboardFontSizeEyebrow))
                LiveboardDots(appString(AppString.lbSecrets), Modifier.padding(top = 8.dp), height = 24.dp)
            }
            Image(painterResource(Res.drawable.liveboard_brand), appString(AppString.orbitBrand), Modifier.size(
                LiveboardStyleBindings.dimension(".profile-brand", "width", viewport, 33.dp),
                LiveboardStyleBindings.dimension(".profile-brand", "height", viewport, 38.dp)), alpha = .8f)
        }
        SearchField(state.search, actions.secrets.search)
        Text("${secrets.size} ${appString(AppString.lbSecrets).lowercase()}", style = LiveboardTypography.text(T
            .liveboardFontSizeFoot, T.liveboardColorStorageRule), modifier = Modifier.padding(top = 21.dp, bottom =
            16.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("lb-secrets-list"), verticalArrangement = Arrangement
            .spacedBy(20.dp)) {
            if (secrets.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment
                    .CenterHorizontally) {
                    LiveboardDots(appString(if (noMatches) AppString.lbNoMatches else AppString.noSecretsHeader),
                        height = 20.dp)
                    if (!noMatches) Text(appString(AppString.noSecrets), style = LiveboardTypography.text(T
                        .liveboardFontSizeFoot, T.liveboardColorMuted), modifier = Modifier.padding(top = 16.dp))
                }
            }
            itemsIndexed(secrets.chunked(columns), key = { _, row -> row.first().identity.passId }) { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top) {
                    row.forEachIndexed { index, secret -> Box(Modifier.weight(1f)) { SecretCard(secret, state,
                        actions, environment, rowIndex * columns + index) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    }
}

private const val TWO_COLUMN_WIDTH: Float = 600f
