package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.addDeviceAdvice
import kotlinproject.composeapp.generated.resources.addSecret
import kotlinproject.composeapp.generated.resources.lackOfDevices
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.metasecretpicture
import kotlinproject.composeapp.generated.resources.secretCapital
import kotlinproject.composeapp.generated.resources.secretName
import kotlinproject.composeapp.generated.resources.useMetaSecret
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors

@Composable
fun popup(
    onDismiss: () -> Unit,
    header: String,
    boxHeight: Int,
) {
    var textName by remember { mutableStateOf("") }
    var textSecret by remember { mutableStateOf("") }
    val isSecretsScreen: Boolean = header == stringResource(Res.string.addSecret)

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .height(boxHeight.dp)
                .fillMaxWidth()
                .background(AppColors.PopUp, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = header,
                    fontSize = 24.sp,
                    fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                    color = AppColors.White,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 30.dp)
                )
                if (isSecretsScreen) {
                    Column {
                        textInput(stringResource(Res.string.secretName)) { newValue ->
                            textName = newValue
                        }
                        textInput(stringResource(Res.string.secretCapital)) { newValue ->
                            textSecret = newValue
                        }
                    }
                } else if (!isSecretsScreen) {
                    Text(
                        text = stringResource(Res.string.addDeviceAdvice),
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                        color = AppColors.White75,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 24.dp)
                    )
                    Column(
                        modifier = Modifier
                            .background(AppColors.White5, RoundedCornerShape(10.dp))
                            .padding(bottom = 12.dp),
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
                            modifier = Modifier
                                .align(Alignment.Start)
                        )
                        Text(
                            text = stringResource(Res.string.useMetaSecret),
                            fontSize = 15.sp,
                            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
                            color = AppColors.White75,
                            modifier = Modifier
                                .align(Alignment.Start)
                        )
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
                    enabled = ((textName.isNotEmpty() && textSecret.isNotEmpty()) || !isSecretsScreen),
                    onClick = {
                        //isError = SignInScreenViewModel.isNameError(text)
                    }
                ) {
                    if (isSecretsScreen) {
                        Text(text = stringResource(Res.string.addSecret), fontSize = 16.sp)
                    } else {
                        Text(text = stringResource(Res.string.addDevice), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun textInput(
    placeholderText: String,
    onTextChange: (String) -> Unit)
{
    var text by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = FocusRequester()
    //val focusManager = LocalFocusManager.current

    TextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            onTextChange(newText)
        },
        shape = RoundedCornerShape(8.dp),
        placeholder = {
            Text(
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.5f),
                text = placeholderText
            )
        },
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .height(52.dp)
            .border(
                width = 2.dp,
                color =
                if (isError) {
                    AppColors.RedError
                } else {
                    if (isFocused) {
                        AppColors.ActionPremium
                    } else {
                        Color.Transparent
                    }
                },
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = AppColors.White5,
            cursorColor = AppColors.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
    )
    if (placeholderText == stringResource(Res.string.secretCapital)) {

    }
}