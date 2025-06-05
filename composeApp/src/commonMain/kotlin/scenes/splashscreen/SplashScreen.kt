package scenes.splashscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.biometric_error
import kotlinproject.composeapp.generated.resources.enable_biometric_required
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.text
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sharedData.getScreenHeight
import org.koin.compose.viewmodel.koinViewModel
import scenes.mainscreen.MainScreen
import scenes.onboarding.OnboardingScreen
import scenes.signinscreen.SignInScreen
import sharedData.BiometricState
import ui.notifications.InAppNotification


class SplashScreen : Screen {
    @Composable

    override fun Content() {
        val viewModel: SplashScreenViewModel = koinViewModel()
        val navigator: Navigator? = LocalNavigator.current

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)
        val text = painterResource(Res.drawable.text)

        val biometricError = stringResource(Res.string.enable_biometric_required)

        val navigationEvent by viewModel.navigationEvent.collectAsState()
        val biometricState by viewModel.biometricState.collectAsState()
        
        var showErrorNotification by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            viewModel.onAppear()
        }

        LaunchedEffect(biometricState) {
            when (val state = biometricState) {
                is BiometricState.Error -> {
                    errorMessage = state.message
                    showErrorNotification = true
                    delay(3000)
                    showErrorNotification = false
                }
                is BiometricState.Success -> {
                    navigate(navigationEvent, navigator)
                }
                else -> {
                    errorMessage = biometricError
                    delay(1000)
                    showErrorNotification = true
                    delay(3000)
                    showErrorNotification = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = backgroundMain,
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
                    painter = backgroundLogo,
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
                            painter = logo,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 35.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )

                        Image(
                            painter = text,
                            contentDescription = null,
                            modifier = Modifier
                                .offset(y = 40.dp)
                                .fillMaxWidth()
                                .height((getScreenHeight() * 0.02833).dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            
            if (showErrorNotification) {
                Column {
                    Spacer(modifier = Modifier.height(40.dp))
                    InAppNotification(
                        isSuccessful = false,
                        message = errorMessage,
                        onDismiss = { showErrorNotification = false }
                    )
                    Spacer(modifier = Modifier.fillMaxHeight())
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
