package storage

import kotlinx.serialization.Serializable

interface KeyValueStorage {
    var isOnboardingCompleted: Boolean
    var isSignInCompleted: Boolean
    var signInInfo: LoginInfo?
    var isWarningVisible: Boolean
//    val observableToken: Flow<String>

    fun cleanStorage()
    fun resetKeyValueStorage()
}

@Serializable
data class LoginInfo(val username: String, val password: String)

enum class StorageKeys {
    ONBOARDING_INFO,
    SIGNIN_INFO,
    LOGIN_INFO,
    WARNING_INFO;

    val key get() = this.name
}