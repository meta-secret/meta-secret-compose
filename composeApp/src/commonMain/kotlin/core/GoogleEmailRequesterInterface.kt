package core

interface GoogleEmailRequesterInterface {
    suspend fun requestGoogleEmail(): GoogleEmailAuthResult
}

sealed class GoogleEmailAuthResult {
    data object Cancelled : GoogleEmailAuthResult()
    data class Success(val email: String) : GoogleEmailAuthResult()
    data class Error(val message: String) : GoogleEmailAuthResult()
}
