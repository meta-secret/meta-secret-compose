package scenes.devicesscreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.devicesList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sharedData.enums.ScreenId
import ui.Addbutton
import ui.CommonBackground
import ui.ContentCell
import ui.warningContent


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        val popUpHeader = stringResource(Res.string.addDevice)

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
                    ContentCell(
                        {},
                        screenType = ScreenId.Devices,
                        getBubbleData = viewModel.data(),
                        index = index
                    )
                }
            }
        }
        Addbutton(popUpHeader, 510)
    }
}