package core

interface AppleEmailRequesterInterface {
    suspend fun requestAppleEmail(): AppleEmailAuthResult
}

sealed class AppleEmailAuthResult {
    data object Cancelled : AppleEmailAuthResult()
    data class Success(val email: String) : AppleEmailAuthResult()
    data class Error(val message: String) : AppleEmailAuthResult()
}
