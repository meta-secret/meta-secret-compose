package sharedData

interface BiometricAuthenticator {
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