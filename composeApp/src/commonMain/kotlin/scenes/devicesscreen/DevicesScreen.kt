package scenes.devicesscreen

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import ui.CommonBackground


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        CommonBackground(Res.string.devicesList)
    }
}

