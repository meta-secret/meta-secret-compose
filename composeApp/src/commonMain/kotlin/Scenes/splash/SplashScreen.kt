package scenes.splashscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import scenes.splash.SplashScreenViewModel

@Composable
fun SplashScreen() {
    val viewModel = SplashScreenViewModel()

    LaunchedEffect(Unit) {
        delay(2000)
        when {
            viewModel.readOnboardingKey() -> {
                if (viewModel.checkAuth() == true) {
                    // Route to main screen
                } else {
                    // Route to Sign up
                }
            }
            else -> {
                // Route to onboarding
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "MetaSecret",
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}
