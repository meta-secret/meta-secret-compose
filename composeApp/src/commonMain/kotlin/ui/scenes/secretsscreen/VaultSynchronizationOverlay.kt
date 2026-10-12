package ui.scenes.secretsscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import core.AppColors
import core.AppString
import core.VaultSynchronization
import core.appString
import ui.ClassicButton
import ui.theme.AppTextStyles

/** Covers cached content without restarting an in-flight reveal on its own refresh. */
@Composable
internal fun VaultSynchronizationOverlay(state: VaultSynchronization, onRetry: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(modifier = Modifier.fillMaxSize().background(AppColors.DarkBlue).testTag("vault-sync-blocker"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.testTag("vault-sync-loader"))
                Text(appString(AppString.vaultSynchronizing), style = AppTextStyles.Paragraph(), color = AppColors.White)
            } else {
                Text(appString(AppString.vaultSyncFailed), style = AppTextStyles.Paragraph(), color = AppColors.White)
                ClassicButton(action = onRetry, text = appString(AppString.retrySync),
                    modifier = Modifier.testTag("retry-vault-sync"))
            }
        }
    }
}
