package scenes.splashscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.text
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import scenes.onboarding.OnboardingScreen

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = SplashScreenViewModel()
        val navigator = LocalNavigator.current
        LaunchedEffect(Unit) {
            delay(3000)
            when {
                viewModel.isOnboardingComplete() -> {
                    if (viewModel.checkAuth() == true) {
                        // Route to main screen
                    } else {
                        // Route to Sign up
                    }
                }

                else -> {
                    navigator?.push(OnboardingScreen())
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.background_main),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.FillBounds

            )
            Image(
                painter = painterResource(Res.drawable.background_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(380.dp)
                    .offset(y = (-15).dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(115.dp)
            )
            Image(
                painter = painterResource(Res.drawable.text),
                contentDescription = null,
                modifier = Modifier
                    .size(height = 23.dp, width = 184.dp)
                    .offset(y = 25.dp)
            )
        }
    }
}