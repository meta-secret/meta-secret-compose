package sharedData

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

    val devices: List<Device> get() = mutableDeviceList
    val secrets: List<Secret> get() = mutableSecretsList

    private val mutableDeviceList: MutableList<Device> by lazy {
        mutableListOf(
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
        )
    }

    private val mutableSecretsList: MutableList<Secret> by lazy {
        mutableListOf(
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