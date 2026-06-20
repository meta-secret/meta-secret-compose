package ui.scenes.splashscreen

import core.AppString

import core.appString

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import kotlinproject.composeapp.generated.resources.enable_biometric_required
import org.koin.compose.viewmodel.koinViewModel
import ui.scenes.mainscreen.MainScreen
import ui.scenes.onboarding.OnboardingScreen
import ui.scenes.signinscreen.SignInScreen
import core.BiometricState
import core.NotificationCoordinatorInterface
import org.koin.compose.koinInject

class SplashScreen : Screen {
    @Composable

    override fun Content() {
        val viewModel: SplashScreenViewModel = koinViewModel()
        val navigator: Navigator? = LocalNavigator.current
        val imageProvider: ImageProviderInterface = koinInject()

        val biometricError = appString(AppString.enable_biometric_required)

        val navigationEvent by viewModel.navigationEvent.collectAsState()
        val biometricState by viewModel.biometricState.collectAsState()
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()

        LaunchedEffect(Unit) {
            viewModel.handle(SplashViewEvents.ON_APPEAR)
        }

        LaunchedEffect(biometricState) {
            when (biometricState) {
                is BiometricState.Success ->
                    viewModel.handle(SplashViewEvents.BIOMETRIC_SUCCEEDED)
                is BiometricState.Error -> {
                    notificationCoordinator.showError(biometricError)
                }
                BiometricState.Idle -> Unit
            }
        }

        LaunchedEffect(navigationEvent) {
            if (navigationEvent != SplashNavigationEvent.Idle) {
                navigate(navigationEvent, navigator)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = imageProvider.getPainter(AppImage.BackgroundMain),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Box(
                modifier = Modifier
                    .offset(y = (-29).dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = imageProvider.getPainter(AppImage.BackgroundLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 95.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = imageProvider.getPainter(AppImage.Logo),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 35.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )

                        Image(
                            painter = imageProvider.getPainter(AppImage.Text),
                            contentDescription = null,
                            modifier = Modifier
                                .offset(y = 40.dp)
                                .fillMaxWidth()
                                .height((viewModel.screenMetricsProvider.screenHeight() * 0.02833).dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            
        }
    }
}

fun navigate(navigationEvent: SplashNavigationEvent, navigator: Navigator?) {
    when (navigationEvent) {
        SplashNavigationEvent.NavigateToMain -> {
            navigator?.push(MainScreen())
        }

        SplashNavigationEvent.NavigateToSignUp -> {
            navigator?.push(SignInScreen())
        }

        SplashNavigationEvent.NavigateToOnboarding -> {
            navigator?.push(OnboardingScreen())
        }

        else -> Unit
    }
}
