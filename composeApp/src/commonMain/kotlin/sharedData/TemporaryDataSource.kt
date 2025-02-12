package sharedData

import androidx.compose.runtime.mutableStateListOf
import storage.KeyValueStorage


class Repository(private val keyValueStorage: KeyValueStorage) {
    data class Device(
        val deviceMake: String,
        val name: String
    )

    data class Secret(
        val secretName: String,
        val password: String
    )

    val devices: MutableList<Device> get() = mutableDeviceList
    val secrets: MutableList<Secret> get() = mutableSecretsList

    private val mutableDeviceList: MutableList<Device> by lazy {
        mutableStateListOf(
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString())
        )
    }

    private val mutableSecretsList: MutableList<Secret> by lazy {
        mutableStateListOf(
            Secret("Random", "getPassword"),
            Secret("Secret", "getPassword"),
            Secret("Name", "getPassword"),
        )
    }

    fun addDevice(device: Device) {
        mutableDeviceList.add(device)
    }

    fun addSecret(secret: Secret) {
        mutableSecretsList.add(secret)
    }
}