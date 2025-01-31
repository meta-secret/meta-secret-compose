package sharedData

import storage.KeyValueStorage


class SecretRepository(private val keyValueStorage: KeyValueStorage) {

    data class Secret(
        val secretName: String,
        val password: String
    )

    private val mutableSecretsList: MutableList<Secret> by lazy {
        mutableListOf(
//            Secret("Random Secret Name", "getPassword"),
//            Secret("Random Secret Name", "getPassword"),
//            Secret("Random Secret Name", "getPassword"),
//            Secret("Random Secret Name", "getPassword"),
        )
    }
    val secrets: List<Secret> get() = mutableSecretsList

}
