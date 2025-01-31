package sharedData

import storage.KeyValueStorage

class DeviceRepository (private val keyValueStorage: KeyValueStorage) {
    data class Device(
        val deviceMake: String,
        val name: String
    )

    val devices: List<Device> get() = mutableDeviceList

    private val mutableDeviceList: MutableList<Device> by lazy {
        mutableListOf(
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
            Device(getDeviceMake(), keyValueStorage.signInInfo?.username.toString()),
//          Device(getDeviceMake(), "f")// getNickName().toString())
        )
    }
}