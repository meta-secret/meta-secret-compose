package scenes.devicesscreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import org.koin.compose.viewmodel.koinViewModel
import sharedData.Addbutton
import sharedData.deviceBubbleContent
import sharedData.warningContent
import ui.CommonBackground


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        val isVisible by viewModel.isWarningVisible.collectAsState()

        CommonBackground(Res.string.devicesList) {
            warningContent(
                viewModel = viewModel,
                getDevicesCount = viewModel.sizeDevices,
                addDevice = { viewModel.addDevice() },
                isVisible = isVisible,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items(viewModel.sizeDevices) { index ->
                    deviceBubbleContent(
                        viewModel = viewModel,
                        getDevicesOrSecretsCount = viewModel.sizeSecrets,
                        getDevice = { viewModel.getDevice(index) },
                    )
                }
            }
        }
        Addbutton()
    }
}

