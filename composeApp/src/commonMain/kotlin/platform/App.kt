package platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import ui.scenes.splashscreen.SplashScreen

@Composable
@Preview
fun App() {
    val socketHandler: MetaSecretSocketHandlerInterface = koinInject()
    LaunchedEffect(Unit) {
        socketHandler.onAppLaunch()
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Navigator(SplashScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
