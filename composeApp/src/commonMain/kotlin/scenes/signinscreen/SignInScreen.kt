package scenes.signinscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.accept_request_on_other_device
import kotlinproject.composeapp.generated.resources.advice
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.forward
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.nicknameError
import kotlinproject.composeapp.generated.resources.placeholder
import kotlinproject.composeapp.generated.resources.reject_join
import kotlinproject.composeapp.generated.resources.scan
import kotlinproject.composeapp.generated.resources.start
import kotlinproject.composeapp.generated.resources.unexpected_login
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.mainscreen.MainScreen
import sharedData.AppColors
import ui.ClassicButton
import ui.dialogs.qrscanning.scanQRCode
import ui.notifications.InAppNotification

class SignInScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SignInScreenViewModel = koinViewModel()
        val navigator = LocalNavigator.current
        val focusRequester = FocusRequester()
        val focusManager = LocalFocusManager.current

        var isNameError by remember { mutableStateOf(false) }
        var isSnackError by remember { mutableStateOf(false) }
        var isFocused by remember { mutableStateOf(false) }
        var isScanning by remember { mutableStateOf(false) }
        var scannedText by remember { mutableStateOf("") }
        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)
        val unexpectedLoginStringResource = stringResource(Res.string.unexpected_login)
        val waitForJoinMessage = stringResource(Res.string.accept_request_on_other_device)
        val nameErrorMessage = stringResource(Res.string.nicknameError)
        val rejectedJoinMessage = stringResource(Res.string.reject_join)

        val snackBarMessageType by viewModel.snackBarMessage.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val navigationEvent by viewModel.navigationEvent.collectAsState()

        var snackBarMessage: String? by remember { mutableStateOf(null) }

        LaunchedEffect(navigationEvent) {
            if (navigationEvent) {
                navigator?.push(MainScreen())
            }
        }

        LaunchedEffect(snackBarMessageType) {
             when (snackBarMessageType) {
                SignInSnackMessages.UNEXPECTED_LOGIN_STATE -> {
                    isSnackError = true
                    snackBarMessage = unexpectedLoginStringResource
                }
                SignInSnackMessages.WAIT_JOIN -> {
                    isSnackError = false
                    snackBarMessage = waitForJoinMessage
                }
                SignInSnackMessages.INCORRECT_NAME -> {
                    isNameError = true
                }
                SignInSnackMessages.SIGN_IN_ERROR -> {
                    isSnackError = true
                    snackBarMessage = unexpectedLoginStringResource
                }
                SignInSnackMessages.REJECT -> {
                    isSnackError = true
                    snackBarMessage = rejectedJoinMessage
                }
                null -> {
                    snackBarMessage = null
                }
             }
        }

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
                        .padding(top = 14.dp)
                )

                Column(
                    modifier = Modifier
                        .padding(top = 26.dp, bottom = 36.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ClassicButton(
                        { isScanning = true },
                        stringResource(Res.string.scan),
                        color = Color.Transparent,
                        borderColor = AppColors.White50,
                        isEnabled = !isLoading
                    )
                    if (isScanning) {
                        scanQRCode(
                            isVisible = { isScanning = it },
                            scannedText = { scannedText = it })
                    }
                    TextField(
                        value = scannedText,
                        onValueChange = { newText -> scannedText = newText },
                        shape = RoundedCornerShape(8.dp),
                        placeholder = {
                            Text(
                                fontSize = 16.sp,
                                color = AppColors.White50,
                                text = stringResource(Res.string.placeholder)
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(
                                width = 2.dp,
                                color =
                                    if (isNameError) {
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
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                if (isNameError) {
                    Text(
                        text = nameErrorMessage,
                        color = AppColors.RedError,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 4.dp)
                    )
                }

                ClassicButton(
                    {
                        focusManager.clearFocus()
                        viewModel.handle(SignInViewEvents.StartSignInProcess(scannedText))
                    },
                    stringResource(Res.string.forward),
                    isEnabled = !isLoading
                )
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AppColors.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (snackBarMessage != null) {
                Column {
                    Spacer(modifier = Modifier.height(40.dp))
                    InAppNotification(
                        isSuccessful = !isSnackError,
                        message = snackBarMessage ?: "",
                        onDismiss = {}
                    )
                    Spacer(modifier = Modifier.fillMaxHeight())
                }
            }
        }
    }
}
