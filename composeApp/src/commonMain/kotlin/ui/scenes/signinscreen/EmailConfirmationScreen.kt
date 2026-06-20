package ui.scenes.signinscreen

import core.AppImage
import core.ImageProviderInterface
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import core.AppColors
import core.AppString
import core.NotificationCoordinatorInterface
import core.appString
import models.appInternalModels.EmailProvider
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.ClassicButton
import ui.NakedButton
import ui.notifications.NotificationProvider
import ui.scenes.mainscreen.MainScreen
import ui.theme.AppTextStyles

class EmailConfirmationScreen(
    private val email: String,
    private val provider: EmailProvider
) : Screen {
    @Composable
    override fun Content() {
        val viewModel: EmailConfirmationScreenViewModel = koinViewModel(
            parameters = { parametersOf(email, provider) }
        )
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()
        val imageProvider: ImageProviderInterface = koinInject()
        val navigator = LocalNavigator.current
        val focusManager = LocalFocusManager.current

        val isLoading by viewModel.isLoading.collectAsState()
        val navigationEvent by viewModel.navigationEvent.collectAsState()
        val screenState by viewModel.screenState.collectAsState()

        val providerIcon = when (provider) {
            EmailProvider.APPLE -> imageProvider.getPainter(AppImage.Apple)
            EmailProvider.GOOGLE -> imageProvider.getPainter(AppImage.Google)
            EmailProvider.MANUAL -> imageProvider.getPainter(AppImage.IconEmail)
        }
        val providerLabel = when (provider) {
            EmailProvider.APPLE -> "APPLE ID"
            EmailProvider.GOOGLE -> "GOOGLE"
            EmailProvider.MANUAL -> "EMAIL"
        }

        LaunchedEffect(navigationEvent) {
            when (val event = navigationEvent) {
                EmailConfirmationNavigationEvent.MainScreen -> {
                    navigator?.push(MainScreen())
                    viewModel.consumeNavigationEvent()
                }

                EmailConfirmationNavigationEvent.BackToSignIn -> {
                    navigator?.popUntilRoot()
                    viewModel.consumeNavigationEvent()
                }

                else -> Unit
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { focusManager.clearFocus() }
        ) {
            Image(
                painter = imageProvider.getPainter(AppImage.BackgroundMain),
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
                        painter = imageProvider.getPainter(AppImage.BackgroundLogo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = imageProvider.getPainter(AppImage.Logo),
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
                        text = appString(AppString.emailSelectionConfirmTitle),
                        style = AppTextStyles.ScreenTitle(),
                        color = AppColors.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    Text(
                        text = appString(AppString.emailSelectionConfirmDescription),
                        style = AppTextStyles.Caption(),
                        color = AppColors.White75,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    Spacer(modifier = Modifier.size(28.dp))

                    val cardShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AppColors.White50, cardShape)
                            .background(AppColors.White5, cardShape)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(AppColors.DarkBlue, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = providerIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = providerLabel,
                                    style = AppTextStyles.Tiny(),
                                    color = AppColors.White75
                                )
                                Text(
                                    text = email,
                                    style = AppTextStyles.BodyStrong(),
                                    color = AppColors.White,
                                    maxLines = 1
                                )
                            }

                            Image(
                                painter = imageProvider.getPainter(AppImage.EmailReceivedCheck),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp)
                    ) {
                        Image(
                            painter = imageProvider.getPainter(AppImage.IconLock),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(AppColors.White50),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            text = appString(AppString.emailSelectionManualHintLine2),
                            style = AppTextStyles.Caption(),
                            color = AppColors.White30,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (screenState is EmailConfirmationScreenState.VaultExists) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = appString(AppString.name_occupied_join_prompt),
                            style = AppTextStyles.CaptionStrong(),
                            color = AppColors.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (screenState) {
                            is EmailConfirmationScreenState.JoiningPending -> {
                                ClassicButton(
                                    action = {
                                        focusManager.clearFocus()
                                        viewModel.handle(EmailConfirmationViewEvents.CancelJoin)
                                    },
                                    text = appString(AppString.cancel),
                                    color = AppColors.Warning,
                                    isEnabled = !isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ClassicButton(
                                    action = {},
                                    text = appString(AppString.joining),
                                    isEnabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is EmailConfirmationScreenState.VaultExists -> {
                                ClassicButton(
                                    action = {
                                        focusManager.clearFocus()
                                        viewModel.handle(EmailConfirmationViewEvents.JoinExistingVault)
                                    },
                                    text = appString(AppString.join),
                                    isEnabled = !isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                NakedButton(
                                    title = appString(AppString.emailSelectionChange),
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.handle(EmailConfirmationViewEvents.StartOver)
                                    }
                                )
                            }
                            is EmailConfirmationScreenState.Default -> {
                                ClassicButton(
                                    action = {
                                        focusManager.clearFocus()
                                        viewModel.handle(EmailConfirmationViewEvents.ContinueClicked)
                                    },
                                    text = appString(AppString.emailSelectionContinue),
                                    isEnabled = !isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                NakedButton(
                                    title = appString(AppString.emailSelectionChange),
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.handle(EmailConfirmationViewEvents.StartOver)
                                    }
                                )
                            }
                        }
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
