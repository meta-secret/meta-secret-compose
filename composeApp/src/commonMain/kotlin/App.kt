import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import di.appModule
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.core.context.startKoin
import scenes.splashscreen.SplashScreen

@Composable
@Preview
fun App() {
    startKoin {
        modules(appModule)
    }

    MaterialTheme {
        Navigator(SplashScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }
}
