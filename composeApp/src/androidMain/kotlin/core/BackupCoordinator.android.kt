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

class BackupCoordinatorInterfaceAndroid(
    private val activity: FragmentActivity,
    private val keyChain: KeyChainInterface
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
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android:  ❌ takePersistableUriPermission failed: ${e.message}")
                }
                CoroutineScope(Dispatchers.Main).launch {
                    val saved = keyChain.saveString("bdBackUp", uri.toString())
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ✅ backup URI saved: $saved")
                }
            } else {
                showWarningAlert()
            }
        }
    }

    override fun ensureBackupDestinationSelected() {
        showInitialAlert()
    }

    override fun restoreIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbFile = File(activity.applicationContext.getDatabasePath("meta-secret.db").path)
                if (dbFile.exists()) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ✅ local DB exists, skipping restore")
                    return@launch
                }
                val path = keyChain.getString("bdBackUp")
                if (path.isNullOrEmpty()) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ no backup URI set")
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@launch
                }
                val uri = Uri.parse(path)
                if (!isGoogleDriveUri(uri)) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ backup URI is not Google Drive")
                    withContext(Dispatchers.Main) { showProviderInvalidAlert() }
                    return@launch
                }
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.parentFile?.mkdirs()
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ✅ restore completed")
                } ?: run {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ restore failed: openInputStream null")
                    withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_restore_failed) }
                }
            } catch (e: Exception) {
                println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ restore exception: ${e.message}")
                withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_restore_failed) }
            }
        }
    }

    override fun backupIfChanged() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: backupIfChanged - Local DB updated, starting backup")
                val path = keyChain.getString("bdBackUp")
                if (path.isNullOrEmpty()) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ no backup URI set")
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@launch
                }
                val uri = Uri.parse(path)
                if (!isGoogleDriveUri(uri)) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ backup URI is not Google Drive")
                    withContext(Dispatchers.Main) { showProviderInvalidAlert() }
                    return@launch
                }

                val dbFile = File(activity.applicationContext.getDatabasePath("meta-secret.db").path)
                if (!dbFile.exists()) {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ local DB does not exist")
                    return@launch
                }

                // Log DB modification time
                val lastModified = dbFile.lastModified()
                println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: 📅 Local DB last modified: ${java.util.Date(lastModified)}")

                activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(output)
                    }
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ✅ backup completed successfully")
                } ?: run {
                    println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ backup failed: openOutputStream null")
                    withContext(Dispatchers.Main) { showErrorAlert(R.string.backup_write_failed) }
                }
            } catch (e: Exception) {
                println("\uD83D\uDCE5\uD83E\uDD16 BackupCoordinator: Android: ❌ backup exception: ${e.message}")
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
                    val uri = android.net.Uri.parse(path)
                    val deleted = activity.contentResolver.delete(uri, null, null)
                    if (deleted >= 1) {
                        keyChain.removeKey(key)
                        return@launch
                    }
                } catch (e: Exception) {
                }
            }
            showWarningAlert()
        }
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
        createDocLauncher?.launch("meta-secret.db")
    }

    private fun isGoogleDriveUri(uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        return authority.startsWith("com.google.android.apps.docs")
    }
}


