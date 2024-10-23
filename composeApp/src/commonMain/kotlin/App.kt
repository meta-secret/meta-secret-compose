import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import di.KoinF
import org.jetbrains.compose.ui.tooling.preview.Preview
import scenes.splashscreen.SplashScreen

@Composable
@Preview
fun App() {
    KoinF.setupKoin()

    MaterialTheme {
        Navigator(SplashScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }
}
