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
}


