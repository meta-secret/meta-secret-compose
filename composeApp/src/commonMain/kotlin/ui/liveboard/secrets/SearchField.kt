package ui.liveboard.secrets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardSurfaceStyle
import ui.liveboard.visual.LiveboardStyleBindings
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    LiveboardSurface(modifier.fillMaxWidth(), LiveboardSurfaceStyle(LiveboardMaterials.search, T
        .liveboardDimensionSearchRadius, T.liveboardColorTileBorder.copy(alpha = .15f), false)) {
        Row(Modifier.fillMaxWidth().heightIn(min = T.liveboardDimensionSearchCompactHeight).padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveboardIcon("search", Modifier.size(22.dp), T.liveboardColorMuted, moving = false)
            BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = 13.dp).testTag("lb-search"),
                textStyle = LiveboardTypography.text(T.liveboardFontSizeSearchCompact), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), cursorBrush = SolidColor(T
                    .liveboardColorDevice), decorationBox = { editor ->
                    Box {
                        if (value.isEmpty()) Text(appString(AppString.LbSearchSecrets), style = LiveboardTypography
                            .text(T.liveboardFontSizeSearchCompact, LiveboardStyleBindings.color(
                                "#secrets-search-input::placeholder", "color", LocalLiveboardViewport.current, T
                                    .liveboardColorPlaceholder)))
                        editor()
                    }
                })
            if (value.isNotEmpty()) androidx.compose.foundation.layout.Box(Modifier.size(30.dp).testTag(
                "lb-search-clear").clickable { onChange("") }, contentAlignment = Alignment.Center) {
                LiveboardIcon("close", Modifier.size(20.dp), moving = false)
            }
        }
    }
}
