package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class DatabasePathProviderIos(
    private val keyChain: KeyChainInterface,
) : DatabasePathProviderInterface {

    override suspend fun getDatabaseFileName(): String? {
        val masterKey = keyChain.getString("master_key") ?: return null
        return SwiftBridge().databaseFileName(masterKey)
    }
}
