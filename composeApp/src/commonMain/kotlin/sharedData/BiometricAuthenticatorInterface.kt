package sharedData

interface BiometricAuthenticatorInterface {
    fun isBiometricAvailable(): Boolean
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    )

    fun openAppSettings()
}

sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    object NotAvailable : BiometricResult()
    object Fallback : BiometricResult()
}

sealed class BiometricState {
    object Idle : BiometricState()
    object Success : BiometricState()
    object NeedRegistration : BiometricState()
    data class Error(val message: String) : BiometricState()
}