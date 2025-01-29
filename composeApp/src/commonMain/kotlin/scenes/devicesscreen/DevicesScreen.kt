package scenes.devicesscreen

import AppColors
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.arrow
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.devices
import kotlinproject.composeapp.generated.resources.devicesList
import kotlinproject.composeapp.generated.resources.lackOfDevice
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.secret
import kotlinproject.composeapp.generated.resources.warning
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.getDeviceId
import sharedData.getDeviceMake
import ui.CommonBackground


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        CommonBackground(Res.string.devicesList) {
            deviceField {
                warningContent()
            }
            deviceField {
                deviceContent()
            }
        }
    }
}

@Composable
fun deviceContent(){
    val viewModel: DevicesScreenViewModel = koinViewModel()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .height(60.dp)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.devices),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        Column {
            Text(
                text = getDeviceMake() + " (" + viewModel.getNickName() + ")",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                    color = AppColors.White
                )
            )
            Text(
                text = getDeviceId(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White30
                )
            )
            Text(
                text = viewModel.getSecretsCount().toString() + " " + stringResource(Res.string.secret),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White75
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically)
        ) {
            Image(
                painter = painterResource(Res.drawable.arrow),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { println("Pressed") }
            )
        }
    }
}

@Composable
fun warningContent() {

    val viewModel: DevicesScreenViewModel = koinViewModel()
    val annotatedText = buildAnnotatedString {
        append(stringResource(Res.string.lackOfDevice))
        pushStringAnnotation(tag = "addDevice", annotation = "")
        withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
            append(stringResource(Res.string.addDevice))
        }
        pop()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .height(60.dp)
            .padding(start = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.warning),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        ClickableText(
            text = annotatedText,
            style = TextStyle(
                fontSize = 15.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                color = AppColors.White75
            ),
            onClick = { offset ->
                annotatedText.getStringAnnotations("addDevice", offset, offset).firstOrNull()
                    ?.let {
                        viewModel.addDevice()
                    }
            },
            modifier = Modifier
                .width(255.dp)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(end = 5.dp)
    ) {
        Image(
            painter = painterResource(Res.drawable.close),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { println("Pressed") }
        )
    }
}

@Composable
fun deviceField(Content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(AppColors.White5, RoundedCornerShape(10.dp))
            .height(92.dp)
    ) {
        Content()
    }
}