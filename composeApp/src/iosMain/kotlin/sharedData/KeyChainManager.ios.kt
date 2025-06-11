package sharedData

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyChainManagerIos : KeyChainInterface {
    override suspend fun saveString(key: String, value: String): Boolean = withContext(Dispatchers.Main) {
        try {
            KeychainBridge().saveString(key, value) ?: false
        } catch (e: Exception) {
            println("Error saving to KeyChain: ${e.message}")
            false
        }
    }
    
    override suspend fun getString(key: String): String? = withContext(Dispatchers.Main) {
        try {
            KeychainBridge().getString(key)
        } catch (e: Exception) {
            println("Error reading from KeyChain: ${e.message}")
            null
        }
    }
    
    override suspend fun removeKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            KeychainBridge().removeKey(key) ?: false
        } catch (e: Exception) {
            println("Error removing from KeyChain: ${e.message}")
            false
        }
    }
    
    override suspend fun containsKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            KeychainBridge().containsKey(key) ?: false
        } catch (e: Exception) {
            println("Error checking KeyChain: ${e.message}")
            false
        }
    }
    
    override suspend fun clearAll(): Boolean = withContext(Dispatchers.Main) {
        try {
            KeychainBridge().clearAll() ?: false
        } catch (e: Exception) {
            println("Error clearing KeyChain: ${e.message}")
            false
        }
    }
}
