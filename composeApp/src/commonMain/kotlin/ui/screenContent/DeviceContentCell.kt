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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.arrow
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.removeDevice
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.secrets_4
import kotlinproject.composeapp.generated.resources.secrets_5
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.devicesscreen.DevicesScreenViewModel
import sharedData.AppColors
import sharedData.getDeviceId
import ui.SwipeableItem

@Composable
fun DeviceContent(index: Int) {
    val viewModel: DevicesScreenViewModel = koinViewModel()
    val secretsCount by viewModel.secretsCount.collectAsState()
    val devicesCount by viewModel.devicesCount.collectAsState()
    var isSwiped by remember { mutableStateOf(false) }

    val secretText = when {
        secretsCount == 0 || secretsCount > 4 -> stringResource(Res.string.secrets_5)
        secretsCount in 2..4 -> stringResource(Res.string.secrets_4)
        else -> stringResource(Res.string.secret)
    }
    SwipeableItem(
        itemsCount = devicesCount,
        buttonText = stringResource(Res.string.removeDevice),
        isRevealed = isSwiped,
        action = { viewModel.removeDevice(); isSwiped = false },
        onExpanded = { isSwiped = true },
        onCollapsed = { isSwiped = false },
        content = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(AppColors.White5, RoundedCornerShape(12.dp)).height(96.dp)
                    .clickable { isSwiped = false }
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
                    Column {
                        Text(
                            text = viewModel.getDevice(index).deviceMake + " (" + viewModel.getNickName() + ")",
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
                            text = "$secretsCount $secretText",
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
                                .clickable { /* OpenBubble*/ })
                    }
                }
            }
        }
    )
}