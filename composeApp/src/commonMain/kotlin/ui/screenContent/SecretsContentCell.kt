package ui.screenContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import sharedData.Repository
import sharedData.enums.DevicesQuantity

@Composable
fun SecretsContent(getBubbleData: Repository, index: Int) {
    val deviceText = textOnValue(
        getBubbleData.devices.size,
        stringResource(Res.string.device),
        stringResource(Res.string.devices_4),
        stringResource(Res.string.devices_5)
    )
    var protectionLevelShield = painterResource(Res.drawable.shield_l3)
    var protectionLevelText = stringResource(Res.string.level_3)

    when (getBubbleData.devices.size) {
        DevicesQuantity.OneDevice.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l1);
            protectionLevelText = stringResource(Res.string.level_1)
        }

        DevicesQuantity.TwoDevices.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l2);
            protectionLevelText = stringResource(Res.string.level_2)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            modifier = Modifier.height(22.dp),
            text = getBubbleData.secrets[index].secretName,
            style = TextStyle(
                fontSize = 18.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                color = AppColors.White
            )
        )
        Row(
            modifier = Modifier.height(24.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.devices_logo),
                contentDescription = null,
                tint = AppColors.White75
            )
            Text(
                text = "${getBubbleData.devices.size} $deviceText",
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
            modifier = Modifier.height(27.dp),
        )
        Text(
            modifier = Modifier.height(22.dp),
            text = protectionLevelText,
            style = TextStyle(
                fontSize = 11.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White75
            )
        )
    }
}