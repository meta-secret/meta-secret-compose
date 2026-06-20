package ui.scenes.signinscreen

import core.AppColors
import core.AppString
import core.appString
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import core.NotificationCoordinatorInterface
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.icon_lock
import kotlinproject.composeapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import models.appInternalModels.EmailProvider
import ui.ClassicButton
import ui.NakedButton
import ui.notifications.NotificationProvider
import ui.scenes.profilescreen.ProfileScreenViewModel
import ui.scenes.splashscreen.SplashViewEvents
import ui.theme.AppTextStyles

class ManualSignInScreen(
    private val messageError: String? = null,
) : Screen {
    @Composable
    override fun Content() {
        val viewModel: ManualSignInScreenViewModel = koinViewModel()
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()
        val navigator = LocalNavigator.current
        val focusManager = LocalFocusManager.current
        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)
        val lockIcon = painterResource(Res.drawable.icon_lock)
        var email by remember { mutableStateOf("") }
        var isFocused by remember { mutableStateOf(false) }
        val normalizedEmail = email.trim()
        val isContinueEnabled = EMAIL_REGEX.matches(normalizedEmail)

        LaunchedEffect(Unit) {
            viewModel.handle(ManualSignInViewEvents.ShowError(messageError))
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
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = appString(AppString.emailSelectionManualTitle),
                        style = AppTextStyles.ScreenTitle(),
                        color = AppColors.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    Text(
                        text = appString(AppString.emailSelectionManualDescription),
                        style = AppTextStyles.Caption(),
                        color = AppColors.White75,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .widthIn(max = 340.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        TextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) AppColors.ActionMain else AppColors.White50,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text(
                                    text = "alex.morgan@mail.com",
                                    style = AppTextStyles.Body(),
                                    color = AppColors.White50
                                )
                            },
                            textStyle = AppTextStyles.BodyStrong().copy(color = AppColors.White),
                            maxLines = 1,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = AppColors.DarkBlue,
                                unfocusedContainerColor = AppColors.DarkBlue,
                                cursorColor = AppColors.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = AppColors.White,
                                unfocusedTextColor = AppColors.White,
                                focusedPlaceholderColor = AppColors.White50,
                                unfocusedPlaceholderColor = AppColors.White50,
                                focusedLeadingIconColor = AppColors.White50,
                                unfocusedLeadingIconColor = AppColors.White50,
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp)
                    ) {
                        Image(
                            painter = lockIcon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(AppColors.White75),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            text = appString(AppString.emailSelectionManualHintLine1) + " " +
                                appString(AppString.emailSelectionManualHintLine2),
                            style = AppTextStyles.Caption(),
                            color = AppColors.White75,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClassicButton(
                            action = {
                                focusManager.clearFocus()
                                navigator?.push(EmailConfirmationScreen(normalizedEmail, EmailProvider.MANUAL))
                            },
                            text = appString(AppString.emailSelectionContinue),
                            isEnabled = isContinueEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )

                        NakedButton(
                            title = appString(AppString.emailSelectionBackToOptions),
                            onClick = {
                                focusManager.clearFocus()
                                navigator?.pop()
                            }
                        )
                    }
                }
            }
        }

        NotificationProvider(
            notificationCoordinator = notificationCoordinator,
            screenMetricsProvider = viewModel.screenMetricsProvider
        )
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
