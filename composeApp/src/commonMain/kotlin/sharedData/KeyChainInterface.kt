package sharedData

interface KeyChainInterface {
    suspend fun saveString(key: String, value: String): Boolean
    suspend fun getString(key: String): String?
    suspend fun removeKey(key: String): Boolean
    suspend fun containsKey(key: String): Boolean
    suspend fun clearAll(): Boolean
}