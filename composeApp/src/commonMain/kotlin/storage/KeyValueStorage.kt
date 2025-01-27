package storage

import kotlinx.serialization.Serializable

interface KeyValueStorage {
    var isOnboardingCompleted: Boolean
    var isSignInCompleted: Boolean
    var signInInfo: LoginInfo?
//    val observableToken: Flow<String>

    fun cleanStorage()
}

@Serializable
data class LoginInfo(val username: String, val password: String)

enum class StorageKeys {
    ONBOARDING_INFO,
    SIGNIN_INFO,
    LOGIN_INFO;

    val key get() = this.name
}