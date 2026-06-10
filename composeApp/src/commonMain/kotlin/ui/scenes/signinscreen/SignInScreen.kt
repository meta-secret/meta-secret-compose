package ui.scenes.signinscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import core.AppColors
import core.NotificationCoordinatorInterface
import core.email.EmailProvider
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.emailSelectionApple
import kotlinproject.composeapp.generated.resources.emailSelectionBackToOptions
import kotlinproject.composeapp.generated.resources.emailSelectionChange
import kotlinproject.composeapp.generated.resources.emailSelectionConfirmDescription
import kotlinproject.composeapp.generated.resources.emailSelectionConfirmTitle
import kotlinproject.composeapp.generated.resources.emailSelectionContinue
import kotlinproject.composeapp.generated.resources.emailSelectionDescription
import kotlinproject.composeapp.generated.resources.emailSelectionGoogle
import kotlinproject.composeapp.generated.resources.emailSelectionManual
import kotlinproject.composeapp.generated.resources.emailSelectionManualDescription
import kotlinproject.composeapp.generated.resources.emailSelectionManualHint
import kotlinproject.composeapp.generated.resources.emailSelectionManualTitle
import kotlinproject.composeapp.generated.resources.emailSelectionCouldNotGetEmail
import kotlinproject.composeapp.generated.resources.emailSelectionPrivateRelayMessage
import kotlinproject.composeapp.generated.resources.emailSelectionPrivateRelayTitle
import kotlinproject.composeapp.generated.resources.emailSelectionErrorTitle
import kotlinproject.composeapp.generated.resources.emailSelectionTitle
import kotlinproject.composeapp.generated.resources.emailSelectionTryAgain
import kotlinproject.composeapp.generated.resources.join
import kotlinproject.composeapp.generated.resources.joining
import kotlinproject.composeapp.generated.resources.cancel
import kotlinproject.composeapp.generated.resources.start
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ui.ClassicButton
import ui.notifications.NotificationProvider
import ui.scenes.mainscreen.MainScreen

class SignInScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SignInScreenViewModel = koinViewModel()
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()

        val navigator = LocalNavigator.current
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        var isFocused by remember { mutableStateOf(false) }

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)

        val isLoading by viewModel.isLoading.collectAsState()
        val navigationEvent by viewModel.navigationEvent.collectAsState()
        val emailSelectionStep by viewModel.emailSelectionStep.collectAsState()
        val selectedEmail by viewModel.selectedEmail.collectAsState()
        val manualEmail by viewModel.manualEmail.collectAsState()
        val manualEmailError by viewModel.manualEmailError.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()
        val showJoinDecision by viewModel.showJoinDecision.collectAsState()
        val showJoinPending by viewModel.showJoinPending.collectAsState()
        val providerOrder = viewModel.providerOrder

        LaunchedEffect(navigationEvent) {
            if (navigationEvent) {
                navigator?.push(MainScreen())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { focusManager.clearFocus() }
        ) {
            Image(
                painter = backgroundMain,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
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
                        .padding(top = 28.dp),
                    text = stringResource(Res.string.start),
                    color = AppColors.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )

                if (!showJoinDecision && !showJoinPending) {
                    EmailSelectionBody(
                        emailSelectionStep = emailSelectionStep,
                        selectedEmail = selectedEmail,
                        manualEmail = manualEmail,
                        manualEmailError = manualEmailError,
                        statusMessage = statusMessage,
                        providerOrder = providerOrder,
                        isFocused = isFocused,
                        manualFocusRequester = focusRequester,
                        isLoading = isLoading,
                        onFocusChanged = { isFocused = it },
                        onProviderSelected = { provider ->
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.SelectProvider(provider))
                        },
                        onManualEntry = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.StartManualEntry)
                        },
                        onManualChanged = { value ->
                            viewModel.handle(SignInViewEvents.UpdateManualEmail(value))
                        },
                        onManualContinue = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.SubmitManualEmail)
                        },
                        onConfirmEmail = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.ConfirmEmail)
                        },
                        onChangeEmail = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.StartManualEntry)
                        },
                        onRetry = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.RetryCurrent)
                        },
                        onBackToOptions = {
                            focusManager.clearFocus()
                            viewModel.handle(SignInViewEvents.BackToProviderOptions)
                        },
                    )
                }

                if (showJoinDecision || showJoinPending) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClassicButton(
                            action = {
                                focusManager.clearFocus()
                                viewModel.handle(SignInViewEvents.CancelJoin)
                            },
                            text = stringResource(Res.string.cancel),
                            isEnabled = !isLoading,
                            color = AppColors.Warning,
                            modifier = Modifier.weight(1f)
                        )
                        ClassicButton(
                            action = {
                                if (!showJoinPending) {
                                    focusManager.clearFocus()
                                    viewModel.handle(SignInViewEvents.JoinExistingVault)
                                }
                            },
                            text = if (showJoinPending) {
                                stringResource(Res.string.joining)
                            } else {
                                stringResource(Res.string.join)
                            },
                            isEnabled = !isLoading && !showJoinPending,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black60)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.ActionMain)
                }
            }
        }

        NotificationProvider(
            notificationCoordinator = notificationCoordinator,
            screenMetricsProvider = viewModel.screenMetricsProvider
        )
    }
}

@Composable
private fun EmailSelectionBody(
    emailSelectionStep: EmailSelectionStep,
    selectedEmail: String,
    manualEmail: String,
    manualEmailError: String?,
    statusMessage: String?,
    providerOrder: List<EmailProvider>,
    isFocused: Boolean,
    manualFocusRequester: FocusRequester,
    isLoading: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onProviderSelected: (EmailProvider) -> Unit,
    onManualEntry: () -> Unit,
    onManualChanged: (String) -> Unit,
    onManualContinue: () -> Unit,
    onConfirmEmail: () -> Unit,
    onChangeEmail: () -> Unit,
    onRetry: () -> Unit,
    onBackToOptions: () -> Unit,
) {
    LaunchedEffect(emailSelectionStep) {
        if (emailSelectionStep == EmailSelectionStep.MANUAL_ENTRY) {
            manualFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 24.dp, bottom = 28.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (emailSelectionStep) {
            EmailSelectionStep.PROVIDER_OPTIONS -> {
                Text(
                    text = stringResource(Res.string.emailSelectionTitle),
                    color = AppColors.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = stringResource(Res.string.emailSelectionDescription),
                    color = AppColors.White75,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.Start)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    providerOrder.filter { it != EmailProvider.MANUAL }.forEach { provider ->
                        AuthProviderButton(
                            provider = provider,
                            text = when (provider) {
                                EmailProvider.APPLE -> stringResource(Res.string.emailSelectionApple)
                                EmailProvider.GOOGLE -> stringResource(Res.string.emailSelectionGoogle)
                                EmailProvider.MANUAL -> stringResource(Res.string.emailSelectionManual)
                            },
                            onClick = { onProviderSelected(provider) },
                            isEnabled = !isLoading
                        )
                    }

                    ClassicButton(
                        action = onManualEntry,
                        text = stringResource(Res.string.emailSelectionManual),
                        color = Color.Transparent,
                        borderColor = AppColors.White50,
                        isEnabled = !isLoading
                    )
                }
            }

            EmailSelectionStep.MANUAL_ENTRY -> {
                Text(
                    text = stringResource(Res.string.emailSelectionManualTitle),
                    color = AppColors.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = stringResource(Res.string.emailSelectionManualDescription),
                    color = AppColors.White75,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                TextField(
                    value = manualEmail,
                    onValueChange = onManualChanged,
                    shape = RoundedCornerShape(8.dp),
                    placeholder = {
                        Text(
                            fontSize = 16.sp,
                            color = AppColors.White50,
                            text = "name@example.com"
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(
                            width = 2.dp,
                            color = if (!manualEmailError.isNullOrBlank()) {
                                AppColors.RedError
                            } else {
                                if (isFocused) AppColors.ActionPremium else Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(manualFocusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChanged(focusState.isFocused)
                        },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.White5,
                        unfocusedContainerColor = AppColors.White5,
                        disabledContainerColor = AppColors.White5,
                        cursorColor = AppColors.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                if (!manualEmailError.isNullOrBlank()) {
                    Text(
                        text = manualEmailError,
                        color = AppColors.RedError,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.emailSelectionManualHint),
                        color = AppColors.White50,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                ClassicButton(
                    action = onManualContinue,
                    text = stringResource(Res.string.emailSelectionContinue),
                    isEnabled = !isLoading
                )
                Text(
                    text = stringResource(Res.string.emailSelectionBackToOptions),
                    color = AppColors.ActionLink,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onBackToOptions() }
                )
            }

            EmailSelectionStep.CONFIRMATION -> {
                Text(
                    text = stringResource(Res.string.emailSelectionConfirmTitle),
                    color = AppColors.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = stringResource(Res.string.emailSelectionConfirmDescription),
                    color = AppColors.White75,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                EmailSummaryCard(email = selectedEmail)

                ClassicButton(
                    action = onConfirmEmail,
                    text = stringResource(Res.string.emailSelectionContinue),
                    isEnabled = !isLoading
                )
                Text(
                    text = stringResource(Res.string.emailSelectionChange),
                    color = AppColors.ActionLink,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onChangeEmail() }
                )
            }

            EmailSelectionStep.PRIVATE_RELAY -> {
                WarningCard(
                    title = stringResource(Res.string.emailSelectionPrivateRelayTitle),
                    message = statusMessage ?: stringResource(Res.string.emailSelectionPrivateRelayMessage)
                )

                ClassicButton(
                    action = onManualEntry,
                    text = stringResource(Res.string.emailSelectionManual),
                    isEnabled = !isLoading
                )
                ClassicButton(
                    action = onBackToOptions,
                    text = stringResource(Res.string.emailSelectionBackToOptions),
                    color = Color.Transparent,
                    borderColor = AppColors.White50,
                    isEnabled = !isLoading
                )
            }

            EmailSelectionStep.ERROR -> {
                WarningCard(
                    title = stringResource(Res.string.emailSelectionErrorTitle),
                    message = statusMessage ?: stringResource(Res.string.emailSelectionCouldNotGetEmail)
                )

                ClassicButton(
                    action = onRetry,
                    text = stringResource(Res.string.emailSelectionTryAgain),
                    isEnabled = !isLoading
                )
                ClassicButton(
                    action = onManualEntry,
                    text = stringResource(Res.string.emailSelectionManual),
                    color = Color.Transparent,
                    borderColor = AppColors.White50,
                    isEnabled = !isLoading
                )
            }
        }
    }
}

@Composable
private fun AuthProviderButton(
    provider: EmailProvider,
    text: String,
    onClick: () -> Unit,
    isEnabled: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = when (provider) {
        EmailProvider.GOOGLE -> Color(0xFF4285F4)
        EmailProvider.APPLE -> Color.White
        EmailProvider.MANUAL -> AppColors.White50
    }

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, AppColors.White10, shape),
        shape = shape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color(0xFF101C36),
            contentColor = AppColors.White,
            disabledContainerColor = Color(0xFF101C36).copy(alpha = 0.5f),
            disabledContentColor = AppColors.White.copy(alpha = 0.5f)
        ),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.White10),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (provider) {
                        EmailProvider.APPLE -> ""
                        EmailProvider.GOOGLE -> "G"
                        EmailProvider.MANUAL -> "@" 
                    },
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmailSummaryCard(
    email: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.White10, RoundedCornerShape(16.dp))
            .background(Color(0xFF101C36), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = email,
                color = AppColors.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.emailSelectionManualHint),
                color = AppColors.White50,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0x33FF0952)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = AppColors.RedError,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = title,
            color = AppColors.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = message,
            color = AppColors.White75,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Start)
        )
    }
}
