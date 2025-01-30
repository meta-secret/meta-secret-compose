package sharedData



object DeviceRepository {

    data class Device(val deviceMake: String, val name: String)

    private val mutableDeviceList: MutableList<Device> by lazy {
        mutableListOf(
            Device(getDeviceMake(),"d"),// getNickName().toString()),
//            Device(getDeviceMake(),"a"),// getNickName().toString()),
//            Device(getDeviceMake(), "f")// getNickName().toString())
        )
    }

    val devices: List<Device> get() = mutableDeviceList

//    private fun getNickName(): String? {
//        return keyValueStorage.signInInfo?.username
//    }
}


//mutableDeviceList.add(Device(getDeviceMake(), getNickName()))
//mutableDeviceList.removeAt(0)