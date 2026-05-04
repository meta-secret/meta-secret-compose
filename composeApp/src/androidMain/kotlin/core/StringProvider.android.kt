package core

import android.content.Context
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.runBlocking

class StringProviderAndroid(private val context: Context) : StringProviderInterface {
    @OptIn(ExperimentalResourceApi::class)
    private fun s(id: org.jetbrains.compose.resources.StringResource): String = runBlocking { getString(id) }

    override fun biometricTitle() = s(Res.string.biometric_title)
    override fun biometricSubtitle() = s(Res.string.biometric_subtitle)
    override fun biometricDescription() = s(Res.string.biometric_description)
    override fun biometricFallback() = s(Res.string.biometric_fallback)
    override fun biometricNotAvailable() = s(Res.string.biometric_not_available)
    override fun biometricErrorNoHardware() = s(Res.string.biometric_error_no_hardware)
    override fun biometricErrorNoEnrolled() = s(Res.string.biometric_error_no_enrolled)
    override fun biometricPermissionRequired() = s(Res.string.biometric_permission_required)
    override fun biometricPromptReason() = s(Res.string.biometric_prompt)
    override fun biometricPermissionSettings() = s(Res.string.biometric_permission_settings)

    override fun backupChoosePathMessage() = s(Res.string.backup_choose_path_message)
    override fun backupChoosePathWarning() = s(Res.string.backup_choose_path_warning)
    override fun ok() = s(Res.string.ok)
    
    override fun errorNetwork() = s(Res.string.errorNetwork)
    override fun errorInternal() = s(Res.string.errorInternal)
    override fun errorParse() = s(Res.string.errorParse)
    override fun errorValidation() = s(Res.string.errorValidation)
    override fun errorUnknownPrefix() = s(Res.string.errorUnknownPrefix)
    override fun errorBiometricAuthFailed() = s(Res.string.errorBiometricAuthFailed)
    override fun errorSecretAddFailed() = s(Res.string.errorSecretAddFailed)
    override fun errorRecoverDeclined() = s(Res.string.errorRecoverDeclined)
    override fun acceptRequestOnOtherDevice() = s(Res.string.accept_request_on_other_device)
    override fun nameOccupiedJoinPrompt() = s(Res.string.name_occupied_join_prompt)
    override fun recoverPendingExists() = s(Res.string.recoverPendingExists)
    override fun recoverRequestSent() = s(Res.string.recoverRequestSent)
    override fun removeDeviceSuccess() = s(Res.string.deviceRemovedSuccess)
    override fun removeDeviceSelfError() = s(Res.string.removeDeviceSelfError)
    override fun removeDeviceLastMemberError() = s(Res.string.removeDeviceLastMemberError)
    override fun removeDeviceGenericError() = s(Res.string.removeDeviceGenericError)
}

