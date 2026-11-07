package ui.liveboard.visual

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.orbit_manrope_regular
import org.jetbrains.compose.resources.Font
import ui.design.generated.LiveboardTokens as T

/** Scoped Manrope keeps legacy typography untouched. Sizes come from the shared package. */
object LiveboardTypography {
    @Composable
    fun text(size: TextUnit = T.liveboardFontSizeFoot, color: Color = T.liveboardColorInk): TextStyle =
        TextStyle(fontFamily = FontFamily(Font(Res.font.orbit_manrope_regular, FontWeight.Normal)), fontSize = size,
            color = color, lineHeight = (size.value * 1.6f).sp)

    @Composable
    fun heading(size: TextUnit = T.liveboardFontSizeSecretName): TextStyle =
        text(size, T.liveboardColorSecretName).copy(lineHeight = (size.value * 1.35f).sp, letterSpacing = (-.45f).sp)

    @Composable
    fun wordmark(size: TextUnit): TextStyle = text(size, T.liveboardColorForeground).copy(lineHeight = (size.value *
        1.12f).sp, letterSpacing = (-1.5f).sp)

    @Composable
    fun label(size: TextUnit = T.liveboardFontSizeTileHeading): TextStyle =
        text(size, T.liveboardColorSlotHeading).copy(letterSpacing = 1.15.sp, lineHeight = (size.value * 1.5f).sp)
}

@Composable
fun LiveboardTileHeading(number: String, title: String, modifier: Modifier = Modifier) {
    val viewport = LocalLiveboardViewport.current
    val font = LiveboardStyleBindings.dimension(".tile-heading", "font-size", viewport, androidx.compose.ui.unit.Dp(
        T.liveboardFontSizeTileHeading.value)).value.sp
    val spacing = LiveboardStyleBindings.properties(".tile-heading", viewport)["letter-spacing"]?.removeSuffix("px")
        ?.toFloatOrNull() ?: FALLBACK_HEADING_SPACING
    Text("$number  ${title.uppercase()}", modifier, style = LiveboardTypography.label(font).copy(letterSpacing =
        spacing.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val FALLBACK_HEADING_SPACING: Float = 1.15f
