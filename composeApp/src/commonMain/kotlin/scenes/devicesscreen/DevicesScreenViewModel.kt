package scenes.devicesscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addDevice
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.Repository
import storage.KeyValueStorage
import ui.WarningStateHolder

class DevicesScreenViewModel(
    private val keyValueStorage: KeyValueStorage
): ViewModel() {
    val devicesSize = data().devices.size
    val secretsSize = data().secrets.size

    val isWarningVisible: StateFlow<Boolean> = WarningStateHolder.isWarningVisible

    fun closeWarning() {
        WarningStateHolder.setVisibility(false)
    }
    fun addDevice() {
        //TODO("Not yet implemented")
    }

    fun data (): Repository {
        val device = Repository(keyValueStorage)
        return device
    }

    @Composable
    fun getWarningText(): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - devicesSize).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addDevice", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addDevice))
            }
            pop()
        }
    }



}
