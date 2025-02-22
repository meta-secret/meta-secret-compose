package ui.dialogs.adddevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.addDeviceAdvice
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.lackOfDevices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.metasecretpicture
import kotlinproject.composeapp.generated.resources.useMetaSecret
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.actualHeightFactor
import ui.ClassicButton


@Composable
fun popUpDevice(dialogVisibility: (Boolean) -> Unit, mainDialogVisibility: (Boolean) -> Unit) {

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Black30)
                .clickable { dialogVisibility(false) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .height((actualHeightFactor() * 510).dp)
                    .fillMaxWidth()
                    .background(AppColors.PopUp, RoundedCornerShape(12.dp))
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
                            .clickable { dialogVisibility(false) }
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(top = 30.dp, bottom = 40.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.addDevice),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        color = AppColors.White,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = stringResource(Res.string.addDeviceAdvice),
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White75,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Column(
                        modifier = Modifier
                            .background(AppColors.White5, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.metasecretpicture),
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(Res.string.lackOfDevices),
                            fontSize = 18.sp,
                            fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                            color = AppColors.White,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = stringResource(Res.string.useMetaSecret),
                            fontSize = 15.sp,
                            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                            color = AppColors.White75,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }
                    ClassicButton({
                            dialogVisibility(false)
                            mainDialogVisibility(true) },
                        stringResource(Res.string.addDevice)
                    )
                }
            }
        }
    }
}