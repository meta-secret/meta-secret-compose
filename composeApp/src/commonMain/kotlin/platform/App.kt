package platform

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.jetbrains.compose.ui.tooling.preview.Preview
import ui.scenes.splashscreen.SplashScreen

expect fun getPlatformModule(): org.koin.core.module.Module

@Composable
@Preview
fun App() {
    MaterialTheme {
        Navigator(SplashScreen()){ navigator ->
            SlideTransition(navigator)
        }
    }
}
