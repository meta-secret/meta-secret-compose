package core

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import metasecret.project.com.R
import androidx.core.net.toUri

class BackupCoordinatorInterfaceAndroid(
    private val activity: FragmentActivity,
    private val keyChain: KeyChainInterface,
    private val databasePathProvider: DatabasePathProviderInterface,
    private val logger: DebugLoggerInterface
) : BackupCoordinatorInterface {

    private var createDocLauncher: ActivityResultLauncher<String>? = null

    init {
        createDocLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                if (!isGoogleDriveUri(uri)) {
                    showProviderInvalidAlert()
                    return@registerForActivityResult
                }
                try {
                    val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    logger.log(LogTag.BackupCoordinator.Message.TakePersistableUriPermissionFailed, "${e.message}", success = false)
                }
                CoroutineScope(Dispatchers.Main).launch {
                    val saved = keyChain.saveString("bdBackUp", uri.toString())
                    logger.log(LogTag.BackupCoordinator.Message.BackupUriSaved, "$saved", success = saved)
                }
            } else {
                showWarningAlert()
            }
        }
    }

    override fun ensureBackupDestinationSelected() {
        showInitialAlert()
    }

    override suspend fun restoreIfNeeded() {
        logger.log(LogTag.BackupCoordinator.Message.RestoreIfNeeded, success = true)
        withContext(Dispatchers.IO) {
            try {
                val dbFileName = databasePathProvider.getDatabaseFileName()
                val dbFile = File(activity.applicationContext.getDatabasePath(dbFileName).path)
                if (dbFile.exists()) {
                    logger.log(LogTag.BackupCoordinator.Message.LocalDbExists, success = true)
                    return@withContext
                }
                val path = keyChain.getString("bdBackUp")
                if (path.isNullOrEmpty()) {
                    logger.log(LogTag.BackupCoordinator.Message.NoBackupUriSet, success = false)
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@withContext
                }
                val uri = path.toUri()
                if (!isGoogleDriveUri(uri)) {
                    logger.log(LogTag.BackupCoordinator.Message.BackupUriNotGoogleDrive, success = false)
                    withContext(Dispatchers.Main) { showProviderInvalidAlert() }
                    return@withContext
                }
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.parentFile?.mkdirs()
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                    logger.log(LogTag.BackupCoordinator.Message.RestoreCompleted, success = true)
                } ?: run {
                    logger.log(LogTag.BackupCoordinator.Message.RestoreFailed, "openInputStream null", success = false)
                    withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_restore_failed) }
                }
            } catch (e: Exception) {
                logger.log(LogTag.BackupCoordinator.Message.RestoreException, "${e.message}", success = false)
                withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_restore_failed) }
            }
        }
    }

    override suspend fun backupIfChanged() {
        logger.log(LogTag.BackupCoordinator.Message.BackupIfChanged, success = true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val path = keyChain.getString("bdBackUp")
                if (path.isNullOrEmpty()) {
                    logger.log(LogTag.BackupCoordinator.Message.NoBackupUriSet, success = false)
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@launch
                }
                val uri = path.toUri()
                if (!isGoogleDriveUri(uri)) {
                    logger.log(LogTag.BackupCoordinator.Message.BackupUriNotGoogleDrive, success = false)
                    withContext(Dispatchers.Main) { showProviderInvalidAlert() }
                    return@launch
                }

                val dbFileName = databasePathProvider.getDatabaseFileName()
                val dbFile = File(activity.applicationContext.getDatabasePath(dbFileName).path)
                if (!dbFile.exists()) {
                    logger.log(LogTag.BackupCoordinator.Message.LocalDbNotExist, success = false)
                    return@launch
                }

                val lastModified = dbFile.lastModified()
                logger.log(LogTag.BackupCoordinator.Message.LocalDbLastModified, "${java.util.Date(lastModified)}", success = true)

                activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(output)
                    }
                    logger.log(LogTag.BackupCoordinator.Message.BackupCompleted, success = true)
                } ?: run {
                    logger.log(LogTag.BackupCoordinator.Message.BackupFailed, "openOutputStream null", success = false)
                    withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_write_failed) }
                }
            } catch (e: Exception) {
                logger.log(LogTag.BackupCoordinator.Message.BackupException, "${e.message}", success = false)
                withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_write_failed) }
            }
        }
    }

    override fun clearAllBackups() {
        CoroutineScope(Dispatchers.Main).launch {
            val key = "bdBackUp"
            val path = keyChain.getString(key)
            if (!path.isNullOrEmpty()) {
                try {
                    val uri = path.toUri()
                    val deleted = activity.contentResolver.delete(uri, null, null)
                    if (deleted >= 1) {
                        keyChain.removeKey(key)
                        return@launch
                    }
                } catch (e: Exception) {
                    logger.log(LogTag.BackupCoordinator.Message.BackupUriSaved, "${e.message}", success = false)
                }
            }
            showWarningAlert()
        }
    }

    override suspend fun hasDatabaseFile(): Boolean = withContext(Dispatchers.IO) {
        val dbFileName = databasePathProvider.getDatabaseFileName()
        val dbFile = File(activity.applicationContext.getDatabasePath(dbFileName).path)
        dbFile.exists()
    }

    private fun showInitialAlert() {
        AlertDialog.Builder(activity)
            .setMessage(R.string.backup_choose_path_message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                openPicker()
            }
            .setCancelable(true)
            .show()
    }

    private fun showWarningAlert() {
        AlertDialog.Builder(activity)
            .setMessage(R.string.backup_choose_path_warning)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                openPicker()
            }
            .setCancelable(true)
            .show()
    }

    private fun showProviderInvalidAlert() {
        AlertDialog.Builder(activity)
            .setMessage(R.string.backup_invalid_provider)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                openPicker()
            }
            .setCancelable(true)
            .show()
    }

    private fun showErrorAlert(messageRes: Int) {
        AlertDialog.Builder(activity)
            .setMessage(messageRes)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }

    private fun showNoDestinationAlert() {
        AlertDialog.Builder(activity)
            .setMessage(R.string.backup_no_destination)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                openPicker()
            }
            .setCancelable(true)
            .show()
    }

    private fun openPicker() {
        CoroutineScope(Dispatchers.Main).launch {
            val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@launch
            createDocLauncher?.launch(dbFileName)
        }
    }

    private fun isGoogleDriveUri(uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        return authority.startsWith("com.google.android.apps.docs")
    }
}


