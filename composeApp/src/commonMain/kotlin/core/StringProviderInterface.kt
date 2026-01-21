package core

interface StringProviderInterface {
    fun biometricTitle(): String
    fun biometricSubtitle(): String
    fun biometricDescription(): String
    fun biometricFallback(): String
    fun biometricNotAvailable(): String
    fun biometricErrorNoHardware(): String
    fun biometricErrorNoEnrolled(): String
    fun biometricPermissionRequired(): String
    fun biometricPromptReason(): String
    fun biometricPermissionSettings(): String

    fun backupChoosePathMessage(): String
    fun backupChoosePathWarning(): String
    fun ok(): String
    
    fun errorNetwork(): String
    fun errorInternal(): String
    fun errorParse(): String
    fun errorValidation(): String
    fun errorUnknownPrefix(): String
    fun errorBiometricAuthFailed(): String
    fun errorSecretAddFailed(): String
    
    fun acceptRequestOnOtherDevice(): String
}


