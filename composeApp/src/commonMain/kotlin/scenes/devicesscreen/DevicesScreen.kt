package scenes.devicesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import org.koin.compose.viewmodel.koinViewModel
import ui.AddButton
import ui.dialogs.addingDevice
import ui.dialogs.popUpDevice
import ui.notifications.warningContent
import ui.screenContent.CommonBackground
import ui.screenContent.DeviceContent


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        var isDialogVisible by remember { mutableStateOf(false) }
        var isMainDialogVisible by remember { mutableStateOf(false) }

        CommonBackground(Res.string.devicesList) {
            warningContent(
                text = viewModel.getWarningText(),
                action = { viewModel.addDevice() },
                closeAction = { viewModel.changeWarningVisibilityTo(false) },
                viewModel.devicesCount
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(viewModel.devicesCount) { index ->
                    DeviceContent(index)
                }
            }
        }
        AddButton { isDialogVisible = it }

        if (isDialogVisible) {
            popUpDevice(
                dialogVisibility = { isDialogVisible = it },
                mainDialogVisibility = { isMainDialogVisible = it }
            )
        } else if (isMainDialogVisible) {
            addingDevice { isMainDialogVisible = it }
        }
    }
}

