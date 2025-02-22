package scenes.devicesscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addText
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import storage.Device
import storage.KeyValueStorage
import ui.WarningStateHolder

class DevicesScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val secretsCount: StateFlow<Int> = keyValueStorage.secretData.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun getDevice(index: Int): Device {
        return devicesList.value[index]
    }

    fun addDevice(device: Device) {
        keyValueStorage.addDevice(device)
    }

    fun removeDevice() {
        keyValueStorage.removeDevice(0) //TODO
    }

    fun getNickName(): String? {
        return keyValueStorage.signInInfo?.username
    }

    fun changeWarningVisibilityTo(state: Boolean) {
        WarningStateHolder.setVisibility(state)
    }

    @Composable
    fun getWarningText(devicesCount: Int): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - devicesCount).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addText", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addText))
            }
            pop()
        }
    }
}