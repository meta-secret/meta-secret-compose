package core

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metasecret.project.com.R

class BackupCoordinatorInterfaceAndroid(
    private val activity: FragmentActivity,
    private val keyChain: KeyChainInterface,
    private val databasePathProvider: DatabasePathProviderInterface,
    private val logger: DebugLoggerInterface
) : BackupCoordinatorInterface {
    private companion object {
        private const val LEGACY_BACKUP_KEY = "bdBackUp"
        private const val BACKUP_KEY_PREFIX = "bdBackUp"
        private const val BACKUP_PREFS_NAME = "metasecret_backup_prefs"
    }

    private var createDocLauncher: ActivityResultLauncher<String>? = null

    init {
        createDocLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                try {
                    val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    activity.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    logger.log(LogTag.BackupCoordinator.Message.TakePersistableUriPermissionFailed, "${e.message}", success = false)
                }
                CoroutineScope(Dispatchers.Main).launch {
                    val backupKey = resolveWriteBackupKey()
                    val scopedSaved = saveBackupPath(backupKey, uri.toString())
                    val legacySaved = if (backupKey != LEGACY_BACKUP_KEY) saveBackupPath(LEGACY_BACKUP_KEY, uri.toString()) else false
                    val saved = scopedSaved || legacySaved
                    logger.log(
                        LogTag.BackupCoordinator.Message.BackupUriSaved,
                        "key=$backupKey scoped=$scopedSaved legacy=$legacySaved uri=$uri",
                        success = saved
                    )
                }
            } else {
                showWarningAlert()
            }
        }
    }

    override fun ensureBackupDestinationSelected() {
        CoroutineScope(Dispatchers.Main).launch {
            val backupPath = getBackupPathWithMigration()
            if (!backupPath.isNullOrEmpty()) {
                logger.log(LogTag.BackupCoordinator.Message.BackupUriSaved, "already configured path=$backupPath", success = true)
                return@launch
            }
            logger.log(LogTag.BackupCoordinator.Message.EnsureBackupDestinationSelected, success = true)
            showInitialAlert()
        }
    }

    override suspend fun restoreIfNeeded() {
        logger.log(LogTag.BackupCoordinator.Message.RestoreIfNeeded, success = true)
        withContext(Dispatchers.IO) {
            try {
                val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@withContext
                val dbFile = File(activity.applicationContext.getDatabasePath(dbFileName).path)
                if (dbFile.exists()) {
                    logger.log(LogTag.BackupCoordinator.Message.LocalDbExists, "skipping restore", success = true)
                    return@withContext
                }
                val path = getBackupPathWithMigration()
                if (path.isNullOrEmpty()) {
                    logger.log(LogTag.BackupCoordinator.Message.NoBackupUriSet, success = false)
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@withContext
                }
                val uri = path.toUri()
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.parentFile?.mkdirs()
                    FileOutputStream(dbFile).use { output -> input.copyTo(output) }
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
                val path = getBackupPathWithMigration()
                if (path.isNullOrEmpty()) {
                    logger.log(LogTag.BackupCoordinator.Message.NoBackupUriSet, success = false)
                    withContext(Dispatchers.Main) { showNoDestinationAlert() }
                    return@launch
                }
                val uri = path.toUri()

                val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@launch
                val dbFile = File(activity.applicationContext.getDatabasePath(dbFileName).path)
                if (!dbFile.exists()) {
                    logger.log(LogTag.BackupCoordinator.Message.LocalDbNotExist, success = false)
                    return@launch
                }

                val lastModified = dbFile.lastModified()
                logger.log(LogTag.BackupCoordinator.Message.LocalDbLastModified, "${java.util.Date(lastModified)}", success = true)

                activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(dbFile).use { input -> input.copyTo(output) }
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
            val writeKey = resolveWriteBackupKey()
            val path = getBackupPath(writeKey) ?: getBackupPath(LEGACY_BACKUP_KEY)
            if (!path.isNullOrEmpty()) {
                try {
                    val deleted = activity.contentResolver.delete(path.toUri(), null, null)
                    if (deleted >= 1) {
                        removeBackupPath(writeKey)
                        if (writeKey != LEGACY_BACKUP_KEY) {
                            removeBackupPath(LEGACY_BACKUP_KEY)
                        }
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
        val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@withContext false
        File(activity.applicationContext.getDatabasePath(dbFileName).path).exists()
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

    private suspend fun resolveWriteBackupKey(): String {
        val masterKey = keyChain.getString("master_key")
        return if (masterKey.isNullOrEmpty()) LEGACY_BACKUP_KEY else "$BACKUP_KEY_PREFIX$masterKey"
    }

    private suspend fun getBackupPathWithMigration(): String? {
        val writeKey = resolveWriteBackupKey()

        val scopedPath = getBackupPath(writeKey)
        if (!scopedPath.isNullOrEmpty()) {
            logger.log(LogTag.BackupCoordinator.Message.BackupUriSaved, "found scoped key=$writeKey in prefs", success = true)
            return scopedPath
        }

        val legacyPath = getBackupPath(LEGACY_BACKUP_KEY)
        if (!legacyPath.isNullOrEmpty()) {
            if (writeKey != LEGACY_BACKUP_KEY) {
                val migrated = saveBackupPath(writeKey, legacyPath)
                logger.log(
                    LogTag.BackupCoordinator.Message.BackupUriSaved,
                    "found legacy key in prefs, migrate to scoped=$migrated writeKey=$writeKey",
                    success = migrated
                )
            } else {
                logger.log(LogTag.BackupCoordinator.Message.BackupUriSaved, "found legacy key in prefs", success = true)
            }
            return legacyPath
        }

        val legacyFromKeychain = keyChain.getString(LEGACY_BACKUP_KEY)
        if (!legacyFromKeychain.isNullOrEmpty()) {
            val migratedLegacy = saveBackupPath(LEGACY_BACKUP_KEY, legacyFromKeychain)
            val migratedScoped = if (writeKey != LEGACY_BACKUP_KEY) saveBackupPath(writeKey, legacyFromKeychain) else true
            logger.log(
                LogTag.BackupCoordinator.Message.BackupUriSaved,
                "migrated from keychain legacy=$migratedLegacy scoped=$migratedScoped writeKey=$writeKey",
                success = migratedLegacy || migratedScoped
            )
            return legacyFromKeychain
        }

        val scopedFromKeychain = keyChain.getString(writeKey)
        if (!scopedFromKeychain.isNullOrEmpty()) {
            val migratedScoped = saveBackupPath(writeKey, scopedFromKeychain)
            logger.log(
                LogTag.BackupCoordinator.Message.BackupUriSaved,
                "migrated scoped from keychain=$migratedScoped writeKey=$writeKey",
                success = migratedScoped
            )
            return scopedFromKeychain
        }

        logger.log(
            LogTag.BackupCoordinator.Message.BackupUriSaved,
            "not found writeKey=$writeKey legacyKey=$LEGACY_BACKUP_KEY",
            success = false
        )
        return null
    }

    private fun backupPrefs() = activity.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveBackupPath(key: String, value: String): Boolean {
        return backupPrefs().edit().putString(key, value).commit()
    }

    private fun getBackupPath(key: String): String? {
        return backupPrefs().getString(key, null)
    }

    private fun removeBackupPath(key: String): Boolean {
        return backupPrefs().edit().remove(key).commit()
    }
}
