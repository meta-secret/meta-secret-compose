package core

interface BiometricAuthenticatorInterface {
    fun isBiometricAvailable(): Boolean
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    )

    fun openAppSettings()
}

sealed class BiometricState {
    object Idle : BiometricState()
    object Success : BiometricState()
    data class Error(val message: String) : BiometricState()
}