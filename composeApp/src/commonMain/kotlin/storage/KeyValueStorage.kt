package storage

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface KeyValueStorage {
    var isOnboardingCompleted: Boolean
    var isSignInCompleted: Boolean
    var signInInfo: LoginInfo?
    var isWarningVisible: Boolean
    var secretData: StateFlow<List<Secret>>
    var deviceData: StateFlow<List<Device>>
//    val observableToken: Flow<String>

    fun cleanStorage()
    fun resetKeyValueStorage()
    fun addSecret(secret: Secret)
    fun addDevice(device: Device)
}

@Serializable
data class LoginInfo(val username: String, val password: String)

@Serializable
data class Secret( val secretName: String, val password: String)

@Serializable
data class Device(val deviceMake: String, val username: String)

enum class StorageKeys {
    ONBOARDING_INFO,
    SIGNIN_INFO,
    LOGIN_INFO,
    WARNING_INFO,
    SECRET_DATA,
    DEVICE_DATA;

    val key get() = name
}