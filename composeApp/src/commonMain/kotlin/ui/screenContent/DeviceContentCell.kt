package ui.screenContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.arrow
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.Repository
import sharedData.getDeviceId

@Composable
fun DeviceContent (getBubbleData: Repository, index: Int, action:() -> Unit ) {

        val secretText = textOnValue(
            getBubbleData.secrets.size,
            stringResource(Res.string.secret),
            stringResource(Res.string.secrets_4),
            stringResource(Res.string.secrets_5)
        )

        Image(
            painter = painterResource(Res.drawable.devices),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        Column {
            Text(
                text = getBubbleData.devices[index].deviceMake + " (" + getBubbleData.devices[index].name + ")",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                    color = AppColors.White
                )
            )
            Text(
                text = getDeviceId(), style = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White30
                )
            )
            Text(
                text = getBubbleData.secrets.size.toString() + " " + secretText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White75
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Image(painter = painterResource(Res.drawable.arrow),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .clickable { action() /* OpenBubble*/ })
        }
    }
