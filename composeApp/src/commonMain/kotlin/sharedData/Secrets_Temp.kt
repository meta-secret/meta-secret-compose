package sharedData

import storage.KeyValueStorage


class SecretRepository(private val keyValueStorage: KeyValueStorage) {

    data class Secret(
        val secretName: String,
        val password: String
    )

    private val mutableSecretsList: MutableList<Secret> by lazy {
        mutableListOf(
            Secret("getSecret()", "getPassword")
        )
    }
    val secrets: List<Secret> get() = mutableSecretsList
}
