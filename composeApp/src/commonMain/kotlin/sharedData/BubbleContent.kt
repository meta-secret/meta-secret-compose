package sharedData

import AppColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.arrow
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.no
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal inline fun <reified T : ViewModel> bubbleContent(
    viewModel: T,
    getSecretsCount: Int,
    getDevice: (T) -> DeviceRepository.Device
    ) {

    var secretText = stringResource(Res.string.secret)
    var secretCounter = getSecretsCount.toString()
    if (getSecretsCount < 1) {
        secretCounter = stringResource(Res.string.no)
        secretText = stringResource(Res.string.secrets_5)
    } else if (1 < getSecretsCount && getSecretsCount <= 4)
        secretText = stringResource(Res.string.secrets_4)
        else if (getSecretsCount > 4)
            secretText = stringResource(Res.string.secrets_5)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(AppColors.White5, RoundedCornerShape(10.dp))
            .height(92.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .height(60.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.devices),
                contentDescription = null,
                contentScale = ContentScale.FillBounds
            )
            Column {
                Text(
                    text = getDevice(viewModel).deviceMake + " (" + getDevice(viewModel).name + ")",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                        color = AppColors.White
                    )
                )
                Text(
                    text = getDeviceId(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White30
                    )
                )
                Text(
                    text = secretCounter + " " + secretText,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White75
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arrow),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { println("Pressed") }
                )
            }
        }
    }
}
