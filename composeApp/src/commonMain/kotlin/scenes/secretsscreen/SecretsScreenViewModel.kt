package scenes.secretsscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addText
import kotlinproject.composeapp.generated.resources.fromAllDevices
import kotlinproject.composeapp.generated.resources.lackOfDevices_end
import kotlinproject.composeapp.generated.resources.lackOfDevices_start
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.removeSecretConfirmation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import storage.Device
import storage.KeyValueStorage
import storage.Secret
import ui.TabStateHolder
import ui.WarningStateHolder

class SecretsScreenViewModel(
    keyValueStorage: KeyValueStorage
) : ViewModel() {

    private val secretsList: StateFlow<List<Secret>> = keyValueStorage.secretData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val secrets: StateFlow<List<Secret>> = secretsList

    val secretsCount: StateFlow<Int> = secretsList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun getSecret(index: Int): Secret {
        return secretsList.value[index]
    }

    fun changeWarningVisibilityTo(state: Boolean) {
        WarningStateHolder.setVisibility(state)
    }

    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
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

    @Composable
    fun deleteSecretText(secretName: String): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.removeSecretConfirmation))
            withStyle(
                style = SpanStyle(
                    fontFamily = FontFamily(Font(Res.font.manrope_bold)),
                    fontSize = 18.sp,
                    color = AppColors.White
                )
            ) {
                append(secretName)
            }
            append(stringResource(Res.string.fromAllDevices))
        }
    }
}