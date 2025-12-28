package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyChainManagerIos(
    private val logger: DebugLoggerInterface
) : KeyChainInterface {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun saveString(key: String, value: String): Boolean = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().saveStringWithKey(key, value)
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorSaving, "${e.message}", success = false)
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getString(key: String): String? = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().getStringWithKey(key)
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorReading, "${e.message}", success = false)
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun removeKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().removeKeyWithKey(key)
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorRemoving, "${e.message}", success = false)
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun containsKey(key: String): Boolean = withContext(Dispatchers.Main) {
        try {
            SwiftBridge().containsKeyWithKey(key)
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorChecking, "${e.message}", success = false)
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun clearAll(isCleanDB: Boolean): Boolean = withContext(Dispatchers.Main) {
        try {
            logger.log(LogTag.KeyChainManager.Message.StartingClearAll, "isCleanDB: $isCleanDB", success = true)
            val masterKeyValue = getString("master_key")
            if (masterKeyValue != null) {
                removeKey("bdBackUp${masterKeyValue}")
                removeKey(masterKeyValue)
            }
            if (isCleanDB && masterKeyValue != null) {
                SwiftBridge().clearAllWithDbFileName("meta-secret-${masterKeyValue}.db")
            }

            logger.log(LogTag.KeyChainManager.Message.ClearAllCompleted, success = true)
            return@withContext true
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorClearing, "${e.message}", success = false)
            false
        }
    }
}
