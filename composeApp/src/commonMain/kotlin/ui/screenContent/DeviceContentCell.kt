package ui.screenContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.android
import kotlinproject.composeapp.generated.resources.cli
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.laptop
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.other
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import kotlinproject.composeapp.generated.resources.tablet
import kotlinproject.composeapp.generated.resources.web
import models.appInternalModels.DeviceCellModel
import models.appInternalModels.DeviceStatus
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import core.AppColors
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun DeviceContent(
    model: DeviceCellModel,
    currentDeviceId: String?,
    onClick: ()-> Unit
) {
    val effectiveStatus = if (model.id == currentDeviceId) DeviceStatus.Current else model.status
    val secretText = when {
        model.secretsCount == 0 || model.secretsCount > 4 -> stringResource(Res.string.secrets_5)
        model.secretsCount in 2..4 -> stringResource(Res.string.secrets_4)
        else -> stringResource(Res.string.secret)
    }
    // TODO: We don't have device deletion functionality. I'm gonna uncomment it later.
//    SwipeableItem(
//        itemsCount = model.devicesCount,
//        buttonText = stringResource(Res.string.removeDevice),
//        isRevealed = false,
//        screenMetricsProvider,
//        action = {},
//        onExpanded = { },
//        onCollapsed = {},
//        content = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                    .height(88.dp)
                    .clickable {
                        onClick()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(resolveDeviceIcon(model.deviceType, model.deviceName)),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = model.vaultName,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                                color = AppColors.White
                            )
                        )
                        Text(
                            text = model.deviceName.ifBlank { model.deviceType },
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = Color(0xFF7A9ABF)
                            )
                        )
                        Text(
                            text = "${model.secretsCount} $secretText",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = Color(0xFF4E6A88)
                            )
                        )
                    }
                    StatusBadge(status = effectiveStatus)
                }
            }
//        }
//    )
}

private fun resolveDeviceIcon(deviceTypeRaw: String, deviceNameRaw: String): DrawableResource {
    val type = deviceTypeRaw.trim().lowercase()
    // Name is intentionally ignored; icon mapping is based strictly on deviceType.

    return when (type) {
        "iphone", "ios", "phone" -> Res.drawable.devices
        "android" -> Res.drawable.android
        "tablet", "ipad" -> Res.drawable.tablet
        "desktop", "laptop", "macos", "windows", "linux" -> Res.drawable.laptop
        "web", "browser" -> Res.drawable.web
        "cli", "terminal" -> Res.drawable.cli
        "other", "" -> Res.drawable.other
        else -> Res.drawable.other
    }
}

@Composable
private fun StatusBadge(status: DeviceStatus) {
    val (textColor, backgroundColor, borderColor) = when (status) {
        DeviceStatus.Current -> Triple(
            Color(0xFF91BDFF),
            Color(0x2E3B82F6),
            Color(0x595B9DFF)
        )
        DeviceStatus.Member -> Triple(
            Color(0xFF6EE7A0),
            Color(0x2622C55E),
            Color(0x4D4ADE80)
        )
        DeviceStatus.Pending -> Triple(
            Color(0xFFFDE68A),
            Color(0x26EAB308),
            Color(0x4DFACC15)
        )
        DeviceStatus.Declined -> Triple(
            Color(0xFFFCA5A5),
            Color(0x26EF4444),
            Color(0x4DF87171)
        )
    }

    Text(
        text = status.value,
        style = TextStyle(
            fontSize = 10.sp,
            fontFamily = FontFamily(Font(Res.font.manrope_bold)),
            color = textColor
        ),
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
