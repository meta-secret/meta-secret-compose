package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.orbit_manrope_regular
import org.jetbrains.compose.resources.Font
import ui.design.generated.OrbitTokens

/** Scoped registration typography; legacy screens keep AppTextStyles. */
object OrbitTextStyles {
    @Composable
    private fun family() = FontFamily(Font(Res.font.orbit_manrope_regular, FontWeight.Normal))
    @Composable
    fun heading(size: Float = 29f) = TextStyle(
        fontFamily = family(), fontSize = size.sp, lineHeight = (size * if (size >= 29) 1.2f else 1.4f).sp,
        letterSpacing = (if (size >= 29) -1.15 else -.65).sp,
        color = OrbitTokens.orbitColorText,
    )
    @Composable
    fun status() = TextStyle(
        fontFamily = family(), fontSize = OrbitTokens.orbitFontSizeTagline, lineHeight = 16.5.sp,
        letterSpacing = .2.sp, color = OrbitTokens.orbitColorSecondary,
    )
    @Composable
    fun field() = TextStyle(fontFamily = family(), fontSize = OrbitTokens.orbitFontSizeField, color = OrbitTokens
        .orbitColorText)
    @Composable
    fun label() = TextStyle(
        fontFamily = family(), fontSize = OrbitTokens.orbitFontSizeActionLabel,
        letterSpacing = .45.sp, color = OrbitTokens.orbitColorControl,
    )
    @Composable
    fun step() = TextStyle(
        fontFamily = family(), fontSize = OrbitTokens.orbitFontSizeStep,
        color = OrbitTokens.orbitColorRuler,
    )
}
