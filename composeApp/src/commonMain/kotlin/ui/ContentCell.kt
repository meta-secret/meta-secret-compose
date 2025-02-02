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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.arrow
import kotlinproject.composeapp.generated.resources.device
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.devices_4
import kotlinproject.composeapp.generated.resources.devices_5
import kotlinproject.composeapp.generated.resources.devices_logo
import kotlinproject.composeapp.generated.resources.level_1
import kotlinproject.composeapp.generated.resources.level_2
import kotlinproject.composeapp.generated.resources.level_3
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import kotlinproject.composeapp.generated.resources.shield_l1
import kotlinproject.composeapp.generated.resources.shield_l2
import kotlinproject.composeapp.generated.resources.shield_l3
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.Repository
import sharedData.getDeviceId

@Composable
fun ContentSell(
    action: () -> Unit,
    screenId: String,
    getBubbleData: Repository,
    index: Int
) {

    var protectionLevelShield = painterResource(Res.drawable.shield_l3)
    var protectionLevelText = stringResource(Res.string.level_3)
    val secretsSize = getBubbleData.secrets.size
    val devicesSize = getBubbleData.devices.size

    Box(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(AppColors.White5, RoundedCornerShape(10.dp)).height(92.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().height(60.dp).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (screenId == "Devices") {
                val secretText = textOnValue(
                    secretsSize,
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
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically)
                ) {
                    Image(painter = painterResource(Res.drawable.arrow),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .clickable { action() /* OpenBubble*/ })
                }
            } else {
                val deviceText = textOnValue(
                    devicesSize,
                    stringResource(Res.string.device),
                    stringResource(Res.string.devices_4),
                    stringResource(Res.string.devices_5)
                )

                when (devicesSize) {
                    1 -> {
                        protectionLevelShield = painterResource(Res.drawable.shield_l1);
                        protectionLevelText = stringResource(Res.string.level_1)
                    }

                    2 -> {
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
        }
    }
}

@Composable
fun textOnValue(dataSize: Int, base: String, under4: String, from5: String): String {
    var actualString = base
    if (dataSize == 0) actualString = from5
    else if (dataSize in 2..4) actualString = under4
    else if (dataSize > 4) actualString = from5
    return actualString
}