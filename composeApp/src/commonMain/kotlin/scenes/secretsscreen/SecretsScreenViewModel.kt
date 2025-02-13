package scenes.secretsscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import ui.WarningStateHolder

class SecretsScreenViewModel(
    private val repository: Repository
) : ViewModel() {

    private val _secretsCount = mutableStateOf(repository.secrets.size)
    val secretsCount: State<Int> get() = _secretsCount

    private val _devicesCount = mutableStateOf(repository.devices.size)
    val devicesCount: State<Int> get() = _devicesCount

    fun getSecret (index: Int): Repository.Secret {
        val secret = repository.secrets[index]
        return secret
    }

    fun changeWarningVisibilityTo(state: Boolean) {
        WarningStateHolder.setVisibility(state)
    }

    @Composable
    fun getWarningText(): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - repository.devices.size).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addDevice", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addText))
            }
            pop()
        }
    }
}