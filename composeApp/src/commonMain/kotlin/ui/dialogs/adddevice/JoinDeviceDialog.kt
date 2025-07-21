package ui.dialogs.adddevice

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.accept
import kotlinproject.composeapp.generated.resources.decline
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.wanna_join
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.actualHeightFactor
import ui.ClassicButton

@Composable
fun joinDevice(
    onDismiss: (Boolean?) -> Unit
) {
    Dialog(
        onDismissRequest = {
            onDismiss(null)
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Black30)
                .clickable { onDismiss(null) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .height((actualHeightFactor() * 200).dp)
                    .fillMaxWidth()
                    .background(AppColors.PopUp, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = {}, enabled = false)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .padding(top = 30.dp, bottom = 40.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.wanna_join),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                        color = AppColors.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClassicButton(
                            { onDismiss(true) },
                            stringResource(Res.string.accept),
                            modifier = Modifier.weight(1f)
                        )

                        ClassicButton(
                            { onDismiss(false) },
                            stringResource(Res.string.decline),
                            color = Color.Transparent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                }
            }
        }
    }
}
