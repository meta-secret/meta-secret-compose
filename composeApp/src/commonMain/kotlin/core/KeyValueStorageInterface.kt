package core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface KeyValueStorageInterface {
    var isOnboardingCompleted: Boolean
    var signInInfo: LoginInfo?
    var isWarningVisible: Boolean
    var secretData: StateFlow<List<Secret>>
    var deviceData: StateFlow<List<Device>>
    var isBiometricEnabled: Boolean

    fun cleanStorage()
    fun resetKeyValueStorage()
    fun addSecret(secret: Secret)
    fun removeSecret(secret: Secret)
    fun addDevice(device: Device)
    fun removeDevice(index: Int)
}

@Serializable
data class LoginInfo(val username: String, val password: String)

@Serializable
data class Secret(val secretName: String, val password: String)

@Serializable
data class Device(val deviceMake: String, val username: String)

enum class StorageKeys {
    ONBOARDING_INFO,
    LOGIN_INFO,
    WARNING_INFO,
    SECRET_DATA,
    DEVICE_DATA,
    BIOMETRIC_ENABLED;

    val key get() = name
}