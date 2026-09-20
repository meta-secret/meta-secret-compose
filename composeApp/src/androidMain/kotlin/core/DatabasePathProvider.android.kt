package core

import com.metasecret.core.MetaSecretNative

class DatabasePathProviderAndroid(
    private val keyChain: KeyChainInterface
) : DatabasePathProviderInterface {
    
    override suspend fun getDatabaseFileName(): String? {
        val masterKey = keyChain.getString("master_key") ?: return null
        return MetaSecretNative.databaseFileName(masterKey)
    }
}
