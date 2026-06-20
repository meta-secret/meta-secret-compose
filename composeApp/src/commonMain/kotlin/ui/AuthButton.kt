package ui

import core.AppString

import core.appString
import core.AppImage
import core.ImageProviderInterface
import models.appInternalModels.EmailProvider

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import core.AppColors
import org.koin.compose.koinInject
import ui.theme.AppTextStyles

@Composable
fun AuthProviderButton(
    provider: EmailProvider,
    onClick: () -> Unit,
) {
    val imageProvider: ImageProviderInterface = koinInject()
    val appleLogo = imageProvider.getPainter(AppImage.Apple)
    val googleLogo = imageProvider.getPainter(AppImage.Google)
    val shape = RoundedCornerShape(14.dp)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, AppColors.BorderColor, shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (provider != EmailProvider.MANUAL) {AppColors.DarkBlue} else {Color.Transparent},
            contentColor = AppColors.White,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (provider) {
                EmailProvider.APPLE -> Image(
                    painter = appleLogo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                EmailProvider.GOOGLE -> Image(
                    painter = googleLogo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                EmailProvider.MANUAL -> Spacer(modifier = Modifier.size(0.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when (provider) {
                    EmailProvider.APPLE -> appString(AppString.emailSelectionApple)
                    EmailProvider.GOOGLE -> appString(AppString.emailSelectionGoogle)
                    EmailProvider.MANUAL -> appString(AppString.emailSelectionManual)
                },
                style = AppTextStyles.BodyStrong(),
                color = AppColors.White
            )
        }
    }
}
