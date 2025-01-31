package scenes.secretsscreen

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
import org.jetbrains.compose.resources.stringResource
import sharedData.AppColors
import sharedData.DeviceRepository
import sharedData.SecretRepository
import storage.KeyValueStorage

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorage

) : ViewModel() {
    val sizeDevices = DeviceRepository(keyValueStorage).devices.size
    val sizeSecrets = SecretRepository(keyValueStorage).secrets.size

    var isWarningVisible: Boolean = sizeDevices < 3

    fun getNickName(): String? {
        return keyValueStorage.signInInfo?.username
    }

    fun addDevice(): Boolean {
        //TODO("Not yet implemented")
        return true
    }

    fun getSecret(index: Int): SecretRepository.Secret {
        val secret = SecretRepository(keyValueStorage).secrets[index]
        return secret
    }

    @Composable
    fun getWarningText(): AnnotatedString {
        return buildAnnotatedString {
            append(stringResource(Res.string.lackOfDevices_start))
            append((3 - sizeDevices).toString())
            append(stringResource(Res.string.lackOfDevices_end))
            pushStringAnnotation(tag = "addDevice", annotation = "")
            withStyle(style = SpanStyle(color = AppColors.ActionLink)) {
                append(stringResource(Res.string.addDevice))
            }
            pop()
        }
    }
}
