package scenes.splashscreen

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
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.background_logo
import kotlinproject.composeapp.generated.resources.background_main
import kotlinproject.composeapp.generated.resources.logo
import kotlinproject.composeapp.generated.resources.text
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import scenes.onboarding.OnboardingScreen

class SplashScreen : Screen {
    @Composable

    override fun Content() {
        val viewModel: SplashScreenViewModel = koinViewModel()
        val navigator: Navigator? = LocalNavigator.current
        val navigationEvent by viewModel.navigationEvent.collectAsState()

        val backgroundMain = painterResource(Res.drawable.background_main)
        val backgroundLogo = painterResource(Res.drawable.background_logo)
        val logo = painterResource(Res.drawable.logo)
        val text = painterResource(Res.drawable.text)

        LaunchedEffect(Unit) {
            viewModel.onAppear()
        }

        when (navigationEvent) {
            SplashNavigationEvent.NavigateToMain -> {
                // TODO: Move to main screen
            }

            SplashNavigationEvent.NavigateToSignUp -> {
                // TODO: Move to sign in screen
            }

            SplashNavigationEvent.NavigateToOnboarding -> {
                navigator?.push(OnboardingScreen())
            }

            else -> Unit
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
                                .height(23.dp), // TODO: Need to find a way to remove hardcode
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
