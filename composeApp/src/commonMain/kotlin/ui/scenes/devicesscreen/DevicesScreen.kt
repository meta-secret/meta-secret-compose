package ui.scenes.devicesscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.devicesList
import kotlinproject.composeapp.generated.resources.removeDeviceConfirmMessage
import kotlinproject.composeapp.generated.resources.removeDeviceConfirmTitle
import models.appInternalModels.DeviceStatus
import org.koin.compose.viewmodel.koinViewModel
import core.AppColors
import org.jetbrains.compose.resources.stringResource
import ui.AddButton
import ui.dialogs.adddevice.AddingDevice
import ui.dialogs.adddevice.PopUpDevice
import ui.screenContent.CommonBackground
import ui.screenContent.DeviceContent

class DevicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: DevicesScreenViewModel = koinViewModel()
        val devices by viewModel.devicesList.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val isRemoving by viewModel.isRemoving.collectAsState()

        var isDialogVisible by remember { mutableStateOf(false) }
        var isMainDialogVisible by remember { mutableStateOf(false) }
        var removeCandidateId by remember { mutableStateOf<String?>(null) }
        var removeCandidateName by remember { mutableStateOf("") }
        
        LaunchedEffect(Unit) {
            viewModel.handle(DeviceViewEvents.OnAppear)
        }

        CommonBackground(Res.string.devicesList) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(devices) { device ->
                        val memberCount = devices.count { it.status == DeviceStatus.Member || it.status == DeviceStatus.Current }
                        val canRemove = device.status == DeviceStatus.Member && memberCount > 1
                        DeviceContent(
                            model = device,
                            currentDeviceId = viewModel.currentDeviceId,
                            isRemoving = isRemoving,
                            canRemove = canRemove,
                            onRemoveClick = {
                                removeCandidateId = device.id
                                removeCandidateName = device.deviceName
                            },
                            onClick = {
                                if (device.status == DeviceStatus.Pending || device.status == DeviceStatus.Declined) {
                                    viewModel.handle(DeviceViewEvents.SelectDevice(device.id))
                                }
                            }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AppColors.ActionMain,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        AddButton { isMainDialogVisible = it }

        AnimatedVisibility(
            visible = isMainDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250)
            )
        ) {
            AddingDevice(
                viewModel.screenMetricsProvider,
                mainDialogVisibility = { isMainDialogVisible = it },
                userName = ""
            )
        }

        AnimatedVisibility(
            visible = isDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250)
            )
        ) {
            PopUpDevice(
                viewModel.screenMetricsProvider,
                mainDialogVisibility = { isDialogVisible = it },
                dialogVisibility = { isDialogVisible = it }
            )
        }

        if (removeCandidateId != null) {
            Dialog(
                onDismissRequest = { if (!isRemoving) removeCandidateId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ui.dialogs.YesNoDialog(
                    title = "${stringResource(Res.string.removeDeviceConfirmTitle)}\n${stringResource(Res.string.removeDeviceConfirmMessage, removeCandidateName)}",
                    isVisible = true,
                    onDismiss = { accepted ->
                        if (accepted == true && !isRemoving) {
                            viewModel.handle(DeviceViewEvents.RemoveDevice(removeCandidateId!!))
                        }
                        if (!isRemoving) {
                            removeCandidateId = null
                        }
                    }
                )
            }
        }

    }
}
