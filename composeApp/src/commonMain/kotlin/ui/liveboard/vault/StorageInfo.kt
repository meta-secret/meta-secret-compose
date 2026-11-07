package ui.liveboard.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardTypography

data class StoragePresentation(val mode: String, val preview: Boolean, val count: Int)

@Composable
fun StorageInfo(presentation: StoragePresentation, modifier: Modifier = Modifier, actions: @Composable () -> Unit =
    {}) {
    val body = when (presentation.mode) { "whole" -> if (presentation.preview) AppString
        .lbForecastWhole else AppString.lbStorageWhole; "copies" -> if (presentation.preview) AppString
        .lbForecastCopies else AppString.lbStorageCopies; "shares" -> if (presentation.preview) AppString
        .lbForecastShares else AppString.lbStorageShares; else -> null }
    val rule = when (presentation.mode) { "whole" -> if (presentation.preview) AppString
        .lbForecastWholeRule else AppString.lbStorageWholeRule; "copies" -> if (presentation.preview) AppString
        .lbForecastCopiesRule else AppString.lbStorageCopiesRule; "shares" -> if (presentation.preview) AppString
        .lbForecastSharesRule else AppString.lbStorageSharesRule; else -> null }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(T.liveboardColorStorageRule.copy(alpha = .2f)))
        Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (presentation.preview
                ) "${appString(AppString.lbTimeMachine)} · ${presentation.count} ${appString(AppString.lbDevices).lowercase()}" else appString(AppString.lbStorage),
                style = LiveboardTypography.text(T.liveboardFontSizeStorageLabel, if (presentation.preview) T
                    .liveboardColorPreview else T.liveboardColorStorageRule),
                modifier = Modifier.weight(1f).padding(top = 6.dp))
            actions()
        }
        body?.let { Text(appString(it), style = LiveboardTypography.text(T.liveboardFontSizeStorageBody, T
            .liveboardColorStorageBody), modifier = Modifier.padding(top = 8.dp)) }
        rule?.let { Text(appString(it), style = LiveboardTypography.text(T.liveboardFontSizeStorageRule, T
            .liveboardColorStorageRule), modifier = Modifier.padding(top = 4.dp)) }
    }
}
