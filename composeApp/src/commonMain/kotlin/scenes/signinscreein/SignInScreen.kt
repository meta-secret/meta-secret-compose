package scenes.signinscreein

import AppColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.advice
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.forward
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.nicknameError
import kotlinproject.composeapp.generated.resources.placeholder
import kotlinproject.composeapp.generated.resources.scan
import kotlinproject.composeapp.generated.resources.start
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import scenes.splashscreen.SplashScreenViewModel

class SignInScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SignInScreenViewModel = koinViewModel()
        val navigator = LocalNavigator.current
        var text by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        var isFocused by remember { mutableStateOf(false) }
        val focusRequester = FocusRequester()
        val focusManager = LocalFocusManager.current

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    focusManager.clearFocus()
                }
        ) {
            Image(
                painter = backgroundMain,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 75.dp)
                        .aspectRatio(1f)
                ) {
                    Image(
                        painter = backgroundLogo,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = logo,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 55.dp)
                            .aspectRatio(1f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 32.dp),
                    text = stringResource(Res.string.start),
                    color = AppColors.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )

                Text(
                    text = stringResource(Res.string.advice),
                    color = AppColors.White75,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(vertical = 14.dp)
                )

                Column(     //scan + input
                    modifier = Modifier
                        .padding(top = 26.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, AppColors.White50),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null,
                        onClick = {
                            //TODO: Navigate to screen
                        }
                    ) {
                        Text(text = stringResource(Res.string.scan), fontSize = 16.sp)
                    }

                    TextField(
                        value = text,
                        onValueChange = { newText -> text = newText },
                        shape = RoundedCornerShape(8.dp),
                        placeholder = {
                            Text(
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                text = stringResource(Res.string.placeholder)
                            )
                        },
                        modifier = Modifier
                            .padding(top = 14.dp)
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
                }

                if (isError) {
                    Text(
                        text = stringResource(Res.string.nicknameError),
                        color = AppColors.RedError,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 4.dp)
                    )
                }

                Button(     //forward
                    modifier = Modifier
                        .padding(top = 36.dp)
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppColors.ActionMain,
                        contentColor = AppColors.White,
                        disabledBackgroundColor = AppColors.ActionMain.copy(alpha = 0.5f),
                        disabledContentColor = AppColors.White.copy(alpha = 0.5f)
                    ),
                    enabled = text.isNotEmpty(),
                    onClick = {
                        isError = viewModel.isNameError(text)
                    }
                ) {
                    Text(text = stringResource(Res.string.forward), fontSize = 16.sp)
                }

            }
        }
    }
}

@Composable
@Preview()
fun SignInScreenPreview() {
    SignInScreen().Content()
}