package scenes.devicesscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import org.koin.compose.viewmodel.koinViewModel
import sharedData.AppColors
import ui.AddButton
import ui.dialogs.adddevice.addingDevice
import ui.dialogs.adddevice.popUpDevice
import ui.screenContent.CommonBackground
import ui.screenContent.DeviceContent

class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        val devices by viewModel.devicesList.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        
        var isDialogVisible by remember { mutableStateOf(false) }
        var isMainDialogVisible by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            viewModel.handle(DeviceViewEvents.ON_APPEAR)
        }

        CommonBackground(Res.string.devicesList) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(50.dp).align(Alignment.Center),
                        color = AppColors.ActionMain
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(devices) { device ->
                            DeviceContent(device)
                        }
                    }
                }
            }
        }
        
        AddButton { isDialogVisible = it }

        AnimatedVisibility(
            visible = isDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 1500)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 1000)
            )
        ) {
            popUpDevice(
                dialogVisibility = { isDialogVisible = it },
                mainDialogVisibility = { isMainDialogVisible = it }
            )
        }
        AnimatedVisibility(
            visible = isMainDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 1500)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 1000)
            )
        ) {
            addingDevice ({ isMainDialogVisible = it}, viewModel.vaultName.value ?: "")
        }
    }
}