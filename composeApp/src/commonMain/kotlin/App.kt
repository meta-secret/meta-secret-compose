

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.jetbrains.compose.ui.tooling.preview.Preview
import scenes.splashscreen.SplashScreen


@Composable

@Preview
fun App() {
    MaterialTheme {
//        var showContent by remember { mutableStateOf(false) }
        Navigator(SplashScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }
}