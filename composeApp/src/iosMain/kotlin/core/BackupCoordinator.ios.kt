package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import org.jetbrains.compose.resources.ExperimentalResourceApi

class BackupCoordinatorInterfaceIos(
    private val stringProvider: StringProviderInterface
) : BackupCoordinatorInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun ensureBackupDestinationSelected() {
        val bridge = SwiftBridge()
        val key = "bdBackUp"
        val path = bridge.getStringWithKey(key)
        println("\uD83D\uDCE5 BackupCoordinator: iOS: path is $path")
        if (!path.isNullOrEmpty()) {
            val exists = NSFileManager.defaultManager.fileExistsAtPath(path)
            println("\uD83D\uDCE5 BackupCoordinator: iOS: back exists: $exists")
            if (exists) return
        }
        println("\uD83D\uDCE5 BackupCoordinator: iOS: need alert")
        presentUsingUIBridge(bridge)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun restoreIfNeeded() {
        SwiftBridge().restoreBackupIfNeeded()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun backupIfChanged() {
        SwiftBridge().backupIfChanged()
    }

    @OptIn(ExperimentalResourceApi::class, ExperimentalForeignApi::class)
    private fun presentUsingUIBridge(bridge: SwiftBridge) {
        val msg = stringProvider.backupChoosePathMessage()
        val warn = stringProvider.backupChoosePathWarning()
        val okText = stringProvider.ok()

        bridge.presentBackupPickerWithInitialMessage(
            initialMessage = msg,
            okTitle = okText,
            warningMessage = warn,
            warningOkTitle = okText,
            warningCancelTitle = okText
        )
    }

}


