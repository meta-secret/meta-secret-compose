package ui.scenes.splashscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import core.AppString
import core.BiometricState
import core.NotificationCoordinatorInterface
import core.appString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ui.design.generated.OrbitTokens
import ui.scenes.mainscreen.MainScreen
import ui.scenes.onboarding.OnboardingScreen
import ui.scenes.registration.RegistrationScreen
import ui.theme.OrbitTextStyles

class SplashScreen : Screen {
    @Composable

    override fun Content() {
        val viewModel: SplashScreenViewModel = koinViewModel()
        val navigator: Navigator? = LocalNavigator.current

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
            if (navigationEvent !is SplashNavigationEvent.Idle) {
                navigate(navigationEvent, navigator)
            }
        }

        StartupSurface()
    }
}

/** Pure first-frame presentation; startup decisions remain in the existing VM. */
@Composable
internal fun StartupSurface() {
    Box(Modifier.fillMaxSize().background(OrbitTokens.orbitColorBackgroundCenter),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(appString(AppString.orbitBrand), style = OrbitTextStyles.heading())
            Text(appString(AppString.startupTagline), style = OrbitTextStyles.status())
        }
    }
}

fun navigate(navigationEvent: SplashNavigationEvent, navigator: Navigator?) {
    when (navigationEvent) {
        is SplashNavigationEvent.NavigateToMain -> navigator?.push(MainScreen())
        is SplashNavigationEvent.NavigateToSignUp -> navigator?.push(RegistrationScreen())
        is SplashNavigationEvent.NavigateToOnboarding -> navigator?.push(OnboardingScreen())
        is SplashNavigationEvent.NavigateToEmailConfirmationPending ->
            navigator?.push(RegistrationScreen())
        is SplashNavigationEvent.Idle -> Unit
    }
}
