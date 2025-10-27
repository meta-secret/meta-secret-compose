package core

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import models.apiModels.SecretApiModel

class KeyValueStorageImpl(
    private val deviceInfoProvider: DeviceInfoProviderInterface
): KeyValueStorageInterface {
    private val settings: Settings by lazy { Settings() }

    override var isOnboardingCompleted: Boolean
        get() = settings.getBoolean(StorageKeys.ONBOARDING_INFO.key, defaultValue = false)
        set(value) {
            settings[StorageKeys.ONBOARDING_INFO.key] = value
        }

    override var isWarningVisible: Boolean
        get() = settings.getBoolean(StorageKeys.WARNING_INFO.key, defaultValue = true)
        set(value) {
            settings[StorageKeys.WARNING_INFO.key] = value
        }

    override var isBiometricEnabled: Boolean
        get() = settings.getBoolean(StorageKeys.BIOMETRIC_ENABLED.key, defaultValue = false)
        set(value) {
            settings[StorageKeys.BIOMETRIC_ENABLED.key] = value
        }

    override var cachedDeviceId: String?
        get() = settings.getStringOrNull(StorageKeys.CACHED_DEVICE_ID.key)
        set(value) {
            if (value != null) {
                settings[StorageKeys.CACHED_DEVICE_ID.key] = value
            } else {
                settings.remove(StorageKeys.CACHED_DEVICE_ID.key)
            }
        }

    override var cachedVaultName: String?
        get() = settings.getStringOrNull(StorageKeys.CACHED_VAULT_NAME.key)
        set(value) {
            if (value != null) {
                settings[StorageKeys.CACHED_VAULT_NAME.key] = value
            } else {
                settings.remove(StorageKeys.CACHED_VAULT_NAME.key)
            }
        }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override var signInInfo: LoginInfo?
        get() = settings.decodeValueOrNull(LoginInfo.serializer(), StorageKeys.LOGIN_INFO.key)
        set(value) {
            if (value != null) {
                settings.encodeValue(LoginInfo.serializer(), StorageKeys.LOGIN_INFO.key, value)
            }
        }

    /*--- Mutable List of Secrets ---*/

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    private val _secretData = MutableStateFlow(
        settings.decodeValue(
            ListSerializer(Secret.serializer()), StorageKeys.SECRET_DATA.key,
            defaultValue = emptyList(),
        )
    )

    override var secretData: StateFlow<List<Secret>> = _secretData

    override fun addSecret(secret: Secret) {
        _secretData.update { currentList ->
            currentList.plus(secret)
        }
        saveToStorage()
    }

    override fun removeSecret(secret: Secret) {
        _secretData.update { currentList ->
            currentList.toMutableList().apply {
                remove(secret)
            }
        }
        saveToStorage()
    }

    override fun syncSecretsFromVault(apiSecrets: List<SecretApiModel>) {
        val mappedSecrets = apiSecrets.map { apiSecret ->
            Secret(
                secretId = apiSecret.id,
                secretName = apiSecret.name
            )
        }
        _secretData.value = mappedSecrets
        saveToStorage()
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    private fun saveToStorage() {
        settings.encodeValue(
            ListSerializer(Secret.serializer()),
            StorageKeys.SECRET_DATA.key,
            _secretData.value
        )
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    private val _deviceData = MutableStateFlow(
        settings.decodeValue(
            ListSerializer(Device.serializer()), StorageKeys.DEVICE_DATA.key,
            defaultValue = listOf(
                Device(
                    deviceInfoProvider.getDeviceMake(),
                    signInInfo?.username.toString()
                )
            )
        )
    )

    override var deviceData: StateFlow<List<Device>> = _deviceData

    override fun addDevice(device: Device) {
        _deviceData.update { currentList ->
            currentList.plus(device)
        }
        saveDeviceToStorage()
    }

    override fun removeDevice(index: Int) {
        _deviceData.update { currentList ->
            currentList.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
        }
        saveDeviceToStorage()
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    private fun saveDeviceToStorage() {
        settings.encodeValue(
            ListSerializer(Device.serializer()),
            StorageKeys.DEVICE_DATA.key,
            _deviceData.value
        )
    }

    override fun cleanStorage() {
        settings.clear()
    }

    override fun resetKeyValueStorage() {
        settings[StorageKeys.WARNING_INFO.key] = true
        settings[StorageKeys.WARNING_INFO.key] = true
    }
}
