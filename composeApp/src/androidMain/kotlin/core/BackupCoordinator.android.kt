package core

import android.app.AlertDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                CoroutineScope(Dispatchers.Main).launch {
                    keyChain.saveString("bdBackUp", uri.toString())
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
        // TODO: implement URI read into local DB if needed
    }

    override fun backupIfChanged() {
        // TODO: implement write local DB to URI if changed
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

    private fun openPicker() {
        createDocLauncher?.launch("meta-secret.db")
    }
}


