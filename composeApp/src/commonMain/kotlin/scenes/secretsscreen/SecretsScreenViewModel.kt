package scenes.secretsscreen

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
import ui.WarningStateHolder

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorage,
) : ViewModel() {
    val secretsCount = data().secrets.size
    val devicesCount = data().devices.size

    fun addSecret() {
        val newSecret = Repository.Secret(secretName = "Names", password = "Password")
        Repository(keyValueStorage).addSecret(newSecret)
    }

    fun data(): Repository {
        val device = Repository(keyValueStorage)
        return device
    }

    fun changeWarningVisibilityTo(state: Boolean) {
        WarningStateHolder.setVisibility(state)
    }

    @Composable
    fun getWarningText(): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - devicesCount).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addDevice", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addText))
            }
            pop()
        }
    }
}
