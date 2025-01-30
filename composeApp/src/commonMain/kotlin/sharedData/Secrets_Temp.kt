package sharedData



object SecretRepository {

    data class Secret(val SecretName: String, val Password: String)

    private val mutableSecretsList: MutableList<Secret> by lazy {
        mutableListOf(
            Secret("Secret","123456"),// getNickName().toString()),
        )
    }
    val secrets: List<Secret> get() = mutableSecretsList
}


//mutableDeviceList.add(Secret(TODO, TODO))
//mutableDeviceList.removeAt(0)