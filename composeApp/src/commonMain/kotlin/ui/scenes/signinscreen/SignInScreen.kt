package ui.scenes.signinscreen

import core.AppString
import core.appString
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import core.AppColors
import core.NotificationCoordinatorInterface
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.logo
import models.appInternalModels.EmailProvider
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ui.AuthProviderButton
import ui.ClassicButton
import ui.NakedButton
import ui.dialogs.CommonYesNoSheet
import ui.notifications.NotificationProvider
import ui.scenes.mainscreen.MainScreen
import ui.theme.AppTextStyles

class SignInScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SignInScreenViewModel = koinViewModel()
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()

        val navigator = LocalNavigator.current
        val focusManager = LocalFocusManager.current

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)

        val isLoading by viewModel.isLoading.collectAsState()
        val navigationEvent by viewModel.navigationEvent.collectAsState()
        val showJoinDecision by viewModel.showJoinDecision.collectAsState()
        val showJoinPending by viewModel.showJoinPending.collectAsState()
        val emailError by viewModel.emailError.collectAsState()

        var showResetAllDataSheet by remember { androidx.compose.runtime.mutableStateOf(false) }
        val providerOrder = viewModel.providerOrder

        LaunchedEffect(navigationEvent) {
            when (navigationEvent) {
                SignInNavigationEvent.MainScreen -> {
                    navigator?.push(MainScreen())
                    viewModel.consumeNavigationEvent()
                }

                SignInNavigationEvent.ManualSignInScreen -> {
                    navigator?.push(ManualSignInScreen(emailError))
                    viewModel.consumeNavigationEvent()
                }

                else -> {}
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

                if (!showJoinDecision && !showJoinPending) {
                    EmailSelectionBody(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        providerOrder = providerOrder,
                        onProviderSelected = { provider ->
                            if (provider == EmailProvider.MANUAL) {
                                navigator?.push(ManualSignInScreen())
                            } else {
                                viewModel.handle(SignInViewEvents.SelectEmailProvider(provider))
                            }
                        },
                        onResetAllDataClick = {
                            focusManager.clearFocus()
                            showResetAllDataSheet = true
                        }
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
                            text = appString(AppString.cancel),
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
                                appString(AppString.joining)
                            } else {
                                appString(AppString.join)
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

            if (showResetAllDataSheet) {
            CommonYesNoSheet(
                title = appString(AppString.resetAllData),
                subtitle = appString(AppString.resetAllDataWarning),
                isVisible = showResetAllDataSheet,
                isNoMain = true,
                onNo = { showResetAllDataSheet = false },
                onYes = {
                    showResetAllDataSheet = false
                    viewModel.handle(SignInViewEvents.ClearAllData)
                }
            )
        }

        NotificationProvider(
            notificationCoordinator = notificationCoordinator,
            screenMetricsProvider = viewModel.screenMetricsProvider
        )
    }
}

@Composable
private fun EmailSelectionBody(
    modifier: Modifier = Modifier,
    providerOrder: List<EmailProvider>,
    onProviderSelected: (EmailProvider) -> Unit,
    onResetAllDataClick: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(top = 0.dp, bottom = 32.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = appString(AppString.emailSelectionTitle),
            color = AppColors.White,
            style = AppTextStyles.ScreenTitle(),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .widthIn(max = 340.dp),
            text = appString(AppString.emailSelectionDescription),
            color = AppColors.White75,
            style = AppTextStyles.Caption(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            providerOrder.filter { it != EmailProvider.MANUAL }.forEach { provider ->
                AuthProviderButton(
                    provider = provider,
                    onClick = { onProviderSelected(provider) }
                )
            }

            OrDivider()

            AuthProviderButton(
                provider = EmailProvider.MANUAL,
                onClick = { onProviderSelected(EmailProvider.MANUAL) },
            )

            NakedButton(
                title = appString(AppString.resetAllData),
                onClick = onResetAllDataClick
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColors.White50)
        )
        Text(
            text = appString(AppString.orText),
            color = AppColors.White50,
            style = AppTextStyles.CaptionStrong(),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColors.White50)
        )
    }
}
