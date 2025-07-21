package ui.screenContent

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
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.removeDevice
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import models.appInternalModels.DeviceCellModel
import models.appInternalModels.DeviceStatus
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import ui.SwipeableItem

@Composable
fun DeviceContent(
    model: DeviceCellModel,
    onClick: ()-> Unit
) {
    val secretText = when {
        model.secretsCount == 0 || model.secretsCount > 4 -> stringResource(Res.string.secrets_5)
        model.secretsCount in 2..4 -> stringResource(Res.string.secrets_4)
        else -> stringResource(Res.string.secret)
    }
    SwipeableItem(
        itemsCount = model.devicesCount,
        buttonText = stringResource(Res.string.removeDevice),
        isRevealed = false,
        action = {},
        onExpanded = { },
        onCollapsed = {},
        content = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(AppColors.White5, RoundedCornerShape(12.dp)).height(96.dp)
                    .clickable {
                        onClick()
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().height(60.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(Res.drawable.devices),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = model.deviceName,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                                color = AppColors.White
                            )
                        )
                        Text(
                            text = model.status.value,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = when (model.status) {
                                    DeviceStatus.Member -> AppColors.White30
                                    DeviceStatus.Pending -> AppColors.Warning
                                    DeviceStatus.Unknown -> AppColors.RedError
                                }
                            )
                        )
                        Text(
                            text = "${model.secretsCount} $secretText",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = AppColors.White75
                            )
                        )
                    }
                }
            }
        }
    )
}