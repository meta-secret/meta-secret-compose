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
    private val databasePathProvider: DatabasePathProviderInterface
) : BackupCoordinatorInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun ensureBackupDestinationSelected() {
        println("\uD83D\uDCE5\uF8FF BackupCoordinator: iOS: ensureBackupDestinationSelected")
        val bridge = SwiftBridge()
        CoroutineScope(Dispatchers.IO).launch {
            val masterKey = keyChain.getString("master_key")
            val backupKey = "bdBackUp${masterKey}"
            val dbFileName = databasePathProvider.getDatabaseFileName()
            withContext(Dispatchers.Main) {
                presentUsingUIBridge(bridge, backupKey, dbFileName)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun restoreIfNeeded() {
        val dbFileName = databasePathProvider.getDatabaseFileName()
        SwiftBridge().restoreBackupIfNeededWithDbFileName(dbFileName)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun backupIfChanged() {
        val dbFileName = databasePathProvider.getDatabaseFileName()
        SwiftBridge().backupIfChangedWithDbFileName(dbFileName)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun clearAllBackups() {
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = SwiftBridge()
            val masterKey = keyChain.getString("master_key")
            val key = "bdBackUp${masterKey}"
            val path = bridge.getStringWithKey(key)
            println("\uD83D\uDCE5\uF8FF BackupCoordinator: iOS: path is $path")
            if (!path.isNullOrEmpty()) {
                val exists = NSFileManager.defaultManager.fileExistsAtPath(path)
                println("\uD83D\uDCE5\uF8FF BackupCoordinator: iOS: back exists: $exists")
                if (exists) {
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    bridge.removeKeyWithKey(key)
                    return@launch
                }
            }
        }
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
            dbFileName = dbFileName
        )
    }

}


