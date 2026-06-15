package core

interface StringProviderInterface {
    fun getString(key: AppString): String

    fun biometricTitle(): String = getString(AppString.biometric_title)
    fun biometricSubtitle(): String = getString(AppString.biometric_subtitle)
    fun biometricDescription(): String = getString(AppString.biometric_description)
    fun biometricFallback(): String = getString(AppString.biometric_fallback)
    fun biometricNotAvailable(): String = getString(AppString.biometric_not_available)
    fun biometricErrorNoHardware(): String = getString(AppString.biometric_error_no_hardware)
    fun biometricErrorNoEnrolled(): String = getString(AppString.biometric_error_no_enrolled)
    fun biometricPermissionRequired(): String = getString(AppString.biometric_permission_required)
    fun biometricPromptReason(): String = getString(AppString.biometric_prompt)
    fun biometricPermissionSettings(): String = getString(AppString.biometric_permission_settings)

    fun backupChoosePathMessage(): String = getString(AppString.backup_choose_path_message)
    fun backupChoosePathWarning(): String = getString(AppString.backup_choose_path_warning)
    fun ok(): String = getString(AppString.ok)

    fun errorNetwork(): String = getString(AppString.errorNetwork)
    fun errorInternal(): String = getString(AppString.errorInternal)
    fun errorParse(): String = getString(AppString.errorParse)
    fun errorValidation(): String = getString(AppString.errorValidation)
    fun errorUnknownPrefix(): String = getString(AppString.errorUnknownPrefix)
    fun errorBiometricAuthFailed(): String = getString(AppString.errorBiometricAuthFailed)
    fun errorSecretAddFailed(): String = getString(AppString.errorSecretAddFailed)
    fun errorRecoverDeclined(): String = getString(AppString.errorRecoverDeclined)

    fun yes(): String = getString(AppString.yes)
    fun no(): String = getString(AppString.no)
    fun resetAllData(): String = getString(AppString.resetAllData)
    fun resetAllDataWarning(): String = getString(AppString.resetAllDataWarning)

    fun emailSelectionCouldNotGetEmail(): String = getString(AppString.emailSelectionCouldNotGetEmail)
    fun emailSelectionInvalidEmail(): String = getString(AppString.emailSelectionInvalidEmail)
    fun emailSelectionPrivateRelayMessage(): String = getString(AppString.emailSelectionPrivateRelayMessage)
    fun emailSelectionManualHintLine1(): String = getString(AppString.emailSelectionManualHintLine1)
    fun emailSelectionManualHintLine2(): String = getString(AppString.emailSelectionManualHintLine2)

    fun acceptRequestOnOtherDevice(): String = getString(AppString.accept_request_on_other_device)
    fun nameOccupiedJoinPrompt(): String = getString(AppString.name_occupied_join_prompt)
    fun recoverPendingExists(): String = getString(AppString.recoverPendingExists)
    fun recoverRequestSent(): String = getString(AppString.recoverRequestSent)
}
