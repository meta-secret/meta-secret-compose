package ui

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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.device
import kotlinproject.composeapp.generated.resources.devices_4
import kotlinproject.composeapp.generated.resources.devices_5
import kotlinproject.composeapp.generated.resources.devices_logo
import kotlinproject.composeapp.generated.resources.level_1
import kotlinproject.composeapp.generated.resources.level_2
import kotlinproject.composeapp.generated.resources.level_3
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.shield_l1
import kotlinproject.composeapp.generated.resources.shield_l2
import kotlinproject.composeapp.generated.resources.shield_l3
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.SecretRepository

@Composable
internal inline fun <reified T : ViewModel> secretBubbleContent(
    viewModel: T,
    getDevicesCount: Int,
    getSecret: (T) -> SecretRepository.Secret
) {

    var deviceText = stringResource(Res.string.device)
    var deviceCounter = getDevicesCount
    var protectionLevelShield = painterResource(Res.drawable.shield_l3)
    var protectionLevelText = stringResource(Res.string.level_3)
    if (getDevicesCount < 1) {
        deviceText = stringResource(Res.string.devices_5)
    } else if (1 < getDevicesCount && getDevicesCount <= 4)
        deviceText = stringResource(Res.string.devices_4)
    else if (getDevicesCount > 4)
        deviceText = stringResource(Res.string.devices_5)

    when (getDevicesCount) {
        1 -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l1); protectionLevelText =
                stringResource(Res.string.level_1)
        }

        2 -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l2); protectionLevelText =
                stringResource(Res.string.level_2)
        }
    }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
            )
            {
                Text(
                    modifier = Modifier
                        .height(22.dp),
                    text = getSecret(viewModel).secretName,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                        color = AppColors.White
                    )
                )
                Row(
                    modifier = Modifier
                        .height(24.dp)
                ){
                    Icon(
                        painter = painterResource(Res.drawable.devices_logo),
                        contentDescription = null,
                        tint = AppColors.White75
                    )
                    Text(
                        text = "$deviceCounter $deviceText",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                            color = AppColors.White75
                        )
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = protectionLevelShield,
                    contentDescription = null,
                    modifier = Modifier
                        .clickable { println("Pressed") }
                        .height(27.dp),
                )
                Text(
                    modifier = Modifier
                        .height(22.dp),
                    text = protectionLevelText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White75
                    )
                )
            }
        }
    }
}
