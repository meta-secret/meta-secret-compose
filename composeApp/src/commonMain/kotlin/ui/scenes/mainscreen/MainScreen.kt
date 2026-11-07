package ui.scenes.mainscreen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ui.liveboard.LiveboardScreen
import ui.scenes.registration.RegistrationScreen

/** All three existing entry routes share the same keyed native Liveboard root. */
class MainScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LiveboardScreen {
            var root = navigator
            while (root.parent != null) root = root.parent!!
            root.replaceAll(RegistrationScreen())
        }
    }
}
