package ui.dialogs.addsecret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addSecret
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.secretCapital
import kotlinproject.composeapp.generated.resources.secretName
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import sharedData.actualHeightFactor

@Composable
fun addSecret(
    dialogVisibility: (Boolean) -> Unit,
    notificationVisibility: (Boolean) -> Unit
) {
    var textName by remember { mutableStateOf("") }
    var textSecret by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)

    val viewModel: AddSecretViewModel = koinViewModel()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { dialogVisibility(false) }
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(bottom = with(density) { imeHeight.toDp() }),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .heightIn(
                        min = (actualHeightFactor() * 294).dp,
                        max = (actualHeightFactor() * 494).dp
                    )
                    .fillMaxWidth()
                    .background(AppColors.PopUp, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = {}, enabled = false),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.addSecret),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 30.dp)
                    )
                    Column {
                        viewModel.textInput(stringResource(Res.string.secretName)) { newValue ->
                            textName = newValue
                        }
                        viewModel.textInput(stringResource(Res.string.secretCapital)) { newValue ->
                            textSecret = newValue
                        }
                    }
                    Button(
                        modifier = Modifier
                            .padding(vertical = 20.dp)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppColors.ActionMain,
                            contentColor = AppColors.White,
                            disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
                            disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                        ),
                        enabled = (textName.isNotEmpty() && textSecret.isNotEmpty()),
                        onClick = {
                            viewModel.addSecret(textName, textSecret)
                            notificationVisibility(true)
                            dialogVisibility(false)
                        }
                    ) {
                        Text(text = stringResource(Res.string.addSecret), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}