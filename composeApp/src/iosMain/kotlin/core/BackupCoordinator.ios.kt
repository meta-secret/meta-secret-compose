package core

import androidx.lifecycle.viewModelScope
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
    private val stringProvider: StringProviderInterface
) : BackupCoordinatorInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun ensureBackupDestinationSelected() {
        val bridge = SwiftBridge()
        CoroutineScope(Dispatchers.IO).launch {
            val masterKey = keyChain.getString("master_key")
            val backupKey = "bdBackUp${masterKey}"
            withContext(Dispatchers.Main) {
                presentUsingUIBridge(bridge, backupKey)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun restoreIfNeeded() {
        SwiftBridge().restoreBackupIfNeeded()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun backupIfChanged() {
        SwiftBridge().backupIfChanged()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun clearAllBackups() {
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = SwiftBridge()
            val masterKey = keyChain.getString("master_key")
            val key = "bdBackUp${masterKey}"
            val path = bridge.getStringWithKey(key)
            println("\uD83D\uDCE5 BackupCoordinator: iOS: path is $path")
            if (!path.isNullOrEmpty()) {
                val exists = NSFileManager.defaultManager.fileExistsAtPath(path)
                println("\uD83D\uDCE5 BackupCoordinator: iOS: back exists: $exists")
                if (exists) {
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    bridge.removeKeyWithKey(key)
                    return@launch
                }
            }
        }
    }

    @OptIn(ExperimentalResourceApi::class, ExperimentalForeignApi::class)
    private fun presentUsingUIBridge(bridge: SwiftBridge, backupKey: String) {
        val msg = stringProvider.backupChoosePathMessage()
        val warn = stringProvider.backupChoosePathWarning()
        val okText = stringProvider.ok()

        bridge.presentBackupPickerWithInitialMessage(
            initialMessage = msg,
            okTitle = okText,
            warningMessage = warn,
            warningOkTitle = okText,
            warningCancelTitle = okText,
            backupKey = backupKey
        )
    }

}


