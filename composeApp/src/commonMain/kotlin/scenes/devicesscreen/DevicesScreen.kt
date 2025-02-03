package scenes.devicesscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import sharedData.enums.ScreenId
import ui.AddButton
import ui.CommonBackground
import ui.ContentCell
import ui.DevicesStateHolder
import ui.popUpDevice
import ui.warningContent


class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        val visibility by DevicesStateHolder.isDialogVisible.collectAsState()


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
        AddButton {
            DevicesStateHolder.setVisibility(true)
        }
        AnimatedVisibility(
            visible = visibility,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 1500)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 1000)
            )
        ) {
            popUpDevice()
        }
    }
}

