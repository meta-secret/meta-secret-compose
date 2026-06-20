package ui

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.AppColors
import core.ScreenMetricsProviderInterface
import org.koin.compose.koinInject
import ui.theme.AppTextStyles

@Composable
fun RemoveButton(
    screenMetricsProvider: ScreenMetricsProviderInterface,
    action: (Boolean) -> Unit,
    description: String
) {
    val imageProvider: ImageProviderInterface = koinInject()
    Box(
        modifier = Modifier
            .width((screenMetricsProvider.widthFactor() * 80).dp)
            .height(96.dp)
            .background(AppColors.RedError, RoundedCornerShape(12.dp))
            .clickable { action(true) },
        Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = imageProvider.getPainter(AppImage.Trashbox),
                contentDescription = null,
            )
            Text(
                text = description,
                textAlign = TextAlign.Center,
                color = AppColors.White,
                style = AppTextStyles.Micro(),
                modifier = Modifier
                    .padding(horizontal = 18.5.dp)
            )
        }
    }
}
