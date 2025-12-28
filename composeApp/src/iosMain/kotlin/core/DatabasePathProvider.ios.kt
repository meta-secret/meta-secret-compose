package core

class DatabasePathProviderIos(
    private val keyChain: KeyChainInterface,
) : DatabasePathProviderInterface {

    override suspend fun getDatabaseFileName(): String? {
        val masterKey = keyChain.getString("master_key") ?: return null
        return "meta-secret-${masterKey}.db"
    }
}

