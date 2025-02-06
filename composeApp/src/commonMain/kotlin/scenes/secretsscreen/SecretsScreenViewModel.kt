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
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.Repository
import storage.KeyValueStorage
import ui.NotificationStateHolder
import ui.SecretsDialogStateHolder
import ui.WarningStateHolder

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorage,
) : ViewModel() {
    val secretsSize = data().secrets.size
    val devicesSize = data().devices.size
    val isWarningVisible: StateFlow<Boolean> = WarningStateHolder.isWarningVisible
    val isSecretDialogVisible: StateFlow<Boolean> = SecretsDialogStateHolder.isDialogVisible
    val isNotificationVisible: StateFlow<Boolean> = NotificationStateHolder.isNotificationVisible

    fun closeWarning() {
        WarningStateHolder.setVisibility(false)
    }

    fun showNotification() {
        NotificationStateHolder.setVisibility(true)
    }

    fun hideNotification() {
        NotificationStateHolder.setVisibility(false)
    }

    fun showSecretDialog() {
        SecretsDialogStateHolder.setVisibility(true)
    }

    fun closeSecretDialog() {
        SecretsDialogStateHolder.setVisibility(false)
    }

    fun addSecret() {
//        val repository = Repository(keyValueStorage)
//        val newSecret = Repository.Secret(secretName = "Name", password = "Password")
//        repository.addSecret(newSecret)
    }

    fun data(): Repository {
        val device = Repository(keyValueStorage)
        return device
    }

    @Composable
    fun getWarningText(): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - data().devices.size).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addDevice", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addText))
            }
            pop()
        }
    }
}
