package ui

import sharedData.AppColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.lifecycle.ViewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.warning
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


object WarningStateHolder {
    val isWarningVisible = MutableStateFlow(true)

    fun setVisibility(state: Boolean) {
        isWarningVisible.value = state
    }
}

@Composable
internal inline fun <reified T : ViewModel> warningContent(
    viewModel: T,
    crossinline addDevice: (T) -> Boolean,
    getDevicesCount: Int,
    isVisible: Boolean
) {

    val annotatedText = buildAnnotatedString {
        append(stringResource(Res.string.lackOfDevices_start))
        append((3 - getDevicesCount).toString())
        append(stringResource(Res.string.lackOfDevices_end))
        pushStringAnnotation(tag = "addDevice", annotation = "")
        withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
            append(stringResource(Res.string.addDevice))
        }
        pop()
    }
    if (isVisible && getDevicesCount < 3) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .background(AppColors.White5, RoundedCornerShape(10.dp))
                .height(92.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .height(60.dp)
                    .padding(16.dp),
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
                        annotatedText.getStringAnnotations("addDevice", offset, offset)
                            .firstOrNull()
                            ?.let {
                                addDevice(viewModel)
                            }
                    },
                    modifier = Modifier
                        .padding(end = 30.dp)
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
                        .clickable {
                            WarningStateHolder.setVisibility(false)
                        }
                )
            }
        }
    }
}