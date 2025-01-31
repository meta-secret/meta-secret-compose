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
import ui.Addbutton
import ui.CommonBackground
import ui.deviceBubbleContent
import ui.warningContent


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

