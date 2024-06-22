package Scenes.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_main
import org.jetbrains.compose.resources.painterResource


@Composable
    fun OnboardingScreen() {
        val viewModel = OnboardingViewModel()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.background_main),
                contentDescription = "background_image",
                modifier = Modifier
                    .fillMaxSize()
            )

        }
    }
