package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import org.jetbrains.compose.resources.Font

object AppTextStyles {
    @Composable
    private fun Regular() = FontFamily(Font(Res.font.manrope_regular))

    @Composable
    private fun SemiBold() = FontFamily(Font(Res.font.manrope_semi_bold))

    @Composable
    private fun Bold() = FontFamily(Font(Res.font.manrope_bold))

    @Composable
    fun MainHeader(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun ScreenTitle(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun SectionTitle(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun SubsectionTitle(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun DialogTitle(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun CardTitle(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun Strong18(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun ButtonLabel(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun BodyStrong(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun Body(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 16.sp
    )

    @Composable
    fun ParagraphStrong(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )

    @Composable
    fun Paragraph(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 15.sp
    )

    @Composable
    fun CaptionStrong(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun Caption(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 14.sp
    )

    @Composable
    fun SmallStrong(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun Small(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 13.sp
    )

    @Composable
    fun TinyStrong(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun Tiny(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 12.sp
    )

    @Composable
    fun MicroStrong(): TextStyle = TextStyle(
        fontFamily = SemiBold(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )

    @Composable
    fun Micro(): TextStyle = TextStyle(
        fontFamily = Regular(),
        fontSize = 11.sp
    )

    @Composable
    fun Nano(): TextStyle = TextStyle(
        fontFamily = Bold(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}
