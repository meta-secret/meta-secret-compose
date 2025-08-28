package ui.screenContent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinproject.composeapp.generated.resources.removeSecret
import kotlinproject.composeapp.generated.resources.shield_l1
import kotlinproject.composeapp.generated.resources.shield_l2
import kotlinproject.composeapp.generated.resources.shield_l3
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.scenes.secretsscreen.SecretsScreenViewModel
import core.AppColors
import models.appInternalModels.DevicesQuantity
import core.Secret
import ui.SwipeableItem
import ui.dialogs.removesecret.removeSecret
import ui.dialogs.showsecret.showSecret

@Composable
fun SecretsContent(index: Int, secret: Secret) {
    val viewModel: SecretsScreenViewModel = koinViewModel()
    val devicesCount by viewModel.devicesCount.collectAsState()
    var isRemoveDialogVisible by remember { mutableStateOf(false) }
    var isShowDialogVisible by remember { mutableStateOf(false) }
    var isSwiped by remember { mutableStateOf(false) }

    val deviceText = when {
        devicesCount == 0 || devicesCount > 4 -> stringResource(Res.string.devices_5)
        devicesCount in 2..4 -> stringResource(Res.string.devices_4)
        else -> stringResource(Res.string.device)
    }

    var protectionLevelShield = painterResource(Res.drawable.shield_l3)
    var protectionLevelText = stringResource(Res.string.level_3)

    when (devicesCount) {
        DevicesQuantity.OneDevice.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l1)
            protectionLevelText = stringResource(Res.string.level_1)
        }

        DevicesQuantity.TwoDevices.amount -> {
            protectionLevelShield = painterResource(Res.drawable.shield_l2)
            protectionLevelText = stringResource(Res.string.level_2)
        }
    }
    SwipeableItem(
        buttonText = stringResource(Res.string.removeSecret),
        isRevealed = isSwiped,
        action = { isRemoveDialogVisible = it },
        onExpanded = { isSwiped = true },
        onCollapsed = { isSwiped = false },
        content = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(AppColors.White5, RoundedCornerShape(12.dp)).height(96.dp)
                    .clickable {
                        if (!isSwiped) {
                            isShowDialogVisible = true
                        } else {
                            isSwiped = false
                        }
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
                        Text(
                            modifier = Modifier.height(22.dp),
                            text = viewModel.getSecret(index).secretName,
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
                                text = "$devicesCount $deviceText",
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
    )
    if (isRemoveDialogVisible) {
        dialogAnimation({
            removeSecret(
                viewModel.deleteSecretText(secret.secretName),
                secret,
                buttonVisibility = { isSwiped = it },
                dialogVisibility = { isRemoveDialogVisible = it })
        })
    }
    if (isShowDialogVisible) {
        dialogAnimation({
            showSecret(
                secret,
                dialogVisibility = { isShowDialogVisible = it })
        })
    }
}

@Composable
fun dialogAnimation(action: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 1500)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 1000)
        )
    ) {
        action()
    }
}