package scenes.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.compose_multiplatform
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

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
    ) {
        Text(
            text = "MetaSecret",
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        // Background image
        Image(
            painterResource(Res.drawable.compose_multiplatform),
            contentDescription = "Background Image",
        )

    }
}

