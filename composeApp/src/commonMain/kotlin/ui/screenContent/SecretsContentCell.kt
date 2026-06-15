package ui.screenContent

import core.AppString

import core.appString

import ui.theme.AppTextStyles

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import core.AppColors
import models.appInternalModels.DevicesQuantity
import core.Secret

@Composable
fun SecretsContent(
    secret: Secret,
    devicesCount: Int,
    onClick: () -> Unit
) {
    val deviceText = when {
        devicesCount == 0 || devicesCount > 4 -> appString(AppString.devices_5)
        devicesCount in 2..4 -> appString(AppString.devices_4)
        else -> appString(AppString.device)
    }

    var protectionLevelShield = painterResource(Res.drawable.shield_l1)
    var protectionLevelText = appString(AppString.level_1)

    when (devicesCount) {
        DevicesQuantity.OneDevice.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l1)
            protectionLevelText = appString(AppString.level_1)
        }

        DevicesQuantity.TwoDevices.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l2)
            protectionLevelText = appString(AppString.level_2)
        }

        DevicesQuantity.ThreeDevices.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l3)
            protectionLevelText = appString(AppString.level_3)
        }
    }
    // TODO: We don't have secret deletion functionality. I'm gonna uncomment it later.
//    SwipeableItem(
//        itemsCount = -1,
//        buttonText = appString(AppString.removeSecret),
//        isRevealed = false,
//        screenMetricsProvider,
//        action = {},
//        onExpanded = {},
//        onCollapsed = {},
//        content = {
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.height(22.dp),
                                text = secret.secretName,
                                style = AppTextStyles.Strong18().copy(color = AppColors.White)
                            )
                        }
                        Row(
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.devices_logo),
                                contentDescription = null,
                                tint = AppColors.White75
                            )
                            Text(
                                text = "$devicesCount $deviceText",
                                style = AppTextStyles.Paragraph().copy(color = AppColors.White75)
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
                            style = AppTextStyles.Micro().copy(color = AppColors.White75)
                        )
                    }
                }
            }
//        }
//    )
}
