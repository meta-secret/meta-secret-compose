package scenes.devicesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import org.koin.compose.viewmodel.koinViewModel
import ui.AddButton
import ui.DevicesDialogStateHolder
import ui.WarningStateHolder
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
                closeAction = { WarningStateHolder.setVisibility(false) },
                isVisible = WarningStateHolder.isWarningVisible,
                viewModel.devicesSize
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(viewModel.devicesSize) { index ->
                    ContentCell { DeviceContent(viewModel.data(), index) {} }
                }
            }
        }
        AddButton {
            DevicesDialogStateHolder.setVisibility(true)
        }
        popUpDevice()
    }
}

