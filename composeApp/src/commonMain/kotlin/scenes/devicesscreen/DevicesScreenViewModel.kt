package scenes.devicesscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addText
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.Repository
import storage.KeyValueStorage

class DevicesScreenViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {
    val devicesSize = data().devices.size

    fun addDevice() {
        //TODO("Not yet implemented")
    }

    fun data(): Repository {
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
                append(stringResource(Res.string.addText))
            }
            pop()
        }
    }
}
