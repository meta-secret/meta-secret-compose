package core.metaSecretCore

interface FfiSynchronizerInterface {
    val isAppManagerInitialized: Boolean
    
    suspend fun <T> withFfiLock(block: suspend () -> T): T
    
    suspend fun <T> withFfiLockIfInitialized(block: suspend () -> T): T?
    
    fun markAppManagerInitialized()
    
    fun resetInitialization()
}

