package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyChainManagerIos : KeyChainInterface {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun saveString(key: String, value: String): Boolean = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().saveStringWithKey(key, value) ?: false
        } catch (e: Exception) {
            println("Error saving to KeyChain: ${e.message}")
            false
        }
    }
    
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getString(key: String): String? = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().getStringWithKey(key)
        } catch (e: Exception) {
            println("Error reading from KeyChain: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun removeKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().removeKeyWithKey(key)
        } catch (e: Exception) {
            println("Error removing from KeyChain: ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun containsKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            false
            SwiftBridge().containsKeyWithKey(key)
        } catch (e: Exception) {
            println("Error checking KeyChain: ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun clearAll(isCleanDB: Boolean): Boolean = withContext(Dispatchers.Main) {
        try {
            println("🗝️KeyChainManager: Starting clearAll process (isCleanDB: $isCleanDB)")
            val masterKey = getString("master_key")
            if (masterKey != null) {
                removeKey("bdBackUp${masterKey}")
                removeKey(masterKey)
            }
            if (isCleanDB) {
                SwiftBridge().clearAll()
            }

            println("🗝️KeyChainManager: ✅ clearAll completed successfully")
            return@withContext true
        } catch (e: Exception) {
            println("🗝️KeyChainManager: ❌ Error clearing KeyChain: ${e.message}")
            false
        }
    }
}
