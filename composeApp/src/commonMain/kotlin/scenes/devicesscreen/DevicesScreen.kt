package scenes.devicesscreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import org.koin.compose.viewmodel.koinViewModel
import ui.AddButton
import ui.dialogs.popUpDevice
import ui.notifications.warningContent
import ui.screenContent.CommonBackground
import ui.screenContent.ContentCell
import ui.screenContent.DeviceContent


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()

        CommonBackground(Res.string.devicesList) {
            warningContent(
                text = viewModel.getWarningText(),
                action = { viewModel.addDevice() },
                closeAction = { viewModel.closeWarning() },
                isVisible = viewModel.isWarningVisible
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items(viewModel.devicesSize) { index ->
                    ContentCell{DeviceContent(viewModel.data(), index) {} }
                }
            }
        }
        AddButton {
            viewModel.openDialog()
        }
        popUpDevice() // Is appropriate place for a function call?
    }
}

