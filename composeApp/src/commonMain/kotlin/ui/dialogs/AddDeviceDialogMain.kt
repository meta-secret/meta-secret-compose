package ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.downloadMetasecret
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.orUseQR
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.actualHeightFactor
import ui.DevicesMainDialogStateHolder

@Composable
fun addingDevice() {
    val visibility by DevicesMainDialogStateHolder.isDialogVisible.collectAsState()

    AnimatedVisibility(
        visible = visibility,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 1500)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 1000)
        )
    ) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { DevicesMainDialogStateHolder.setVisibility(false) },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .height((actualHeightFactor() * 560).dp)
                        .fillMaxWidth()
                        .background(AppColors.PopUp, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = {}, enabled = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { DevicesMainDialogStateHolder.setVisibility(false) }
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.addDevice),
                            fontSize = 24.sp,
                            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                            color = AppColors.White,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 30.dp, bottom = 24.dp)
                        )
                        Column {
                            Row {
                                //HorizontalPager
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((actualHeightFactor() * 180).dp)
                                    .background(AppColors.White5, RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = stringResource(Res.string.downloadMetasecret),
                                fontSize = 15.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = AppColors.White75,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(vertical = 24.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.White5)
                                .height(1.dp)
                        )
                        Column(
                            modifier = Modifier
                                .padding(vertical = 24.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.orUseQR),
                                fontSize = 15.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                                color = AppColors.White75,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                            )
                            Box {
                                //QR-code
                            }
                        }
                    }
                }
            }
        }
    }
}


