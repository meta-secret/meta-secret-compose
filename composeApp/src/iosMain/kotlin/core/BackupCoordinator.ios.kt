package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import org.jetbrains.compose.resources.ExperimentalResourceApi

class BackupCoordinatorInterfaceIos(
    private val keyChain: KeyChainInterface,
    private val stringProvider: StringProviderInterface,
    private val databasePathProvider: DatabasePathProviderInterface,
    private val logger: DebugLoggerInterface
) : BackupCoordinatorInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun ensureBackupDestinationSelected() {
        logger.log(LogTag.BackupCoordinator.Message.EnsureBackupDestinationSelected, success = true)
        val bridge = SwiftBridge()
        CoroutineScope(Dispatchers.IO).launch {
            val masterKey = keyChain.getString("master_key") ?: return@launch
            val backupKey = "bdBackUp${masterKey}"
            val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@launch
            withContext(Dispatchers.Main) {
                presentUsingUIBridge(bridge, backupKey, dbFileName)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun restoreIfNeeded() {
        withContext(Dispatchers.IO) {
            val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@withContext
            SwiftBridge().restoreBackupIfNeededWithDbFileName(dbFileName)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun backupIfChanged() {
        withContext(Dispatchers.IO) {
            val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@withContext
            SwiftBridge().backupIfChangedWithDbFileName(dbFileName)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun clearAllBackups() {
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = SwiftBridge()
            val masterKey = keyChain.getString("master_key")
            val key = "bdBackUp${masterKey}"
            val path = bridge.getStringWithKey(key)
            logger.log(LogTag.BackupCoordinator.Message.PathIs, path ?: "null", success = path != null)
            if (!path.isNullOrEmpty()) {
                val exists = NSFileManager.defaultManager.fileExistsAtPath(path)
                logger.log(LogTag.BackupCoordinator.Message.BackExists, "$exists", success = exists)
                if (exists) {
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    bridge.removeKeyWithKey(key)
                    return@launch
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun hasDatabaseFile(): Boolean = withContext(Dispatchers.IO) {
        val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@withContext false
        val bridge = SwiftBridge()
        bridge.hasDatabaseFile(dbFileName)
    }

    @OptIn(ExperimentalResourceApi::class, ExperimentalForeignApi::class)
    private fun presentUsingUIBridge(bridge: SwiftBridge, backupKey: String, dbFileName: String) {
        val msg = stringProvider.backupChoosePathMessage()
        val warn = stringProvider.backupChoosePathWarning()
        val okText = stringProvider.ok()

        bridge.presentBackupPickerWithInitialMessage(
            initialMessage = msg,
            okTitle = okText,
            warningMessage = warn,
            warningOkTitle = okText,
            warningCancelTitle = okText,
            backupKey = backupKey,
            dbFileName = dbFileName,
            completion = {
                val isDataBaseExists = bridge.hasDatabaseFile(dbFileName)
                logger.setBackupDbExists(isDataBaseExists)
            }
        )
    }

}


