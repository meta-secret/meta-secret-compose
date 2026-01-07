package core.metaSecretCore

import core.DebugLoggerInterface
import core.LogTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FfiSynchronizer(
    private val logger: DebugLoggerInterface
) : FfiSynchronizerInterface {
    
    private val ffiMutex = Mutex()
    private var _isAppManagerInitialized = false
    
    override val isAppManagerInitialized: Boolean
        get() = _isAppManagerInitialized
    
    override suspend fun <T> withFfiLock(block: suspend () -> T): T {
        logger.log(LogTag.FfiSync.Message.AcquiringLock, success = true)
        return ffiMutex.withLock {
            logger.log(LogTag.FfiSync.Message.LockAcquired, success = true)
            try {
                block()
            } finally {
                logger.log(LogTag.FfiSync.Message.LockReleased, success = true)
            }
        }
    }
    
    override suspend fun <T> withFfiLockIfInitialized(block: suspend () -> T): T? {
        if (!_isAppManagerInitialized) {
            logger.log(LogTag.FfiSync.Message.SkippedNotInitialized, success = true)
            return null
        }
        return withFfiLock(block)
    }
    
    override fun markAppManagerInitialized() {
        logger.log(LogTag.FfiSync.Message.MarkedInitialized, success = true)
        _isAppManagerInitialized = true
    }
    
    override fun resetInitialization() {
        logger.log(LogTag.FfiSync.Message.ResetInitialization, success = true)
        _isAppManagerInitialized = false
    }
}

