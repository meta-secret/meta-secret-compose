package platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.jetbrains.compose.ui.tooling.preview.Preview
import ui.scenes.splashscreen.SplashScreen

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Navigator(SplashScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
