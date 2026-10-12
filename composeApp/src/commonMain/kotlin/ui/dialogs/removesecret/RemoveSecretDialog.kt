package ui.dialogs.removesecret

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import core.AppColors
import core.AppString
import core.Secret
import core.appString
import models.apiModels.SecretDeletionState
import org.koin.compose.viewmodel.koinViewModel

data class SecretDeletionDialogInput(
    val secret: Secret,
    val text: AnnotatedString,
    val deletionState: SecretDeletionState,
    val incoming: Boolean = false,
)

@Composable
fun RemoveSecret(input: SecretDeletionDialogInput, onDismiss: () -> Unit) {
    val (secret, text, deletionState, incoming) = input
    val viewModel: RemoveSecretViewModel = koinViewModel()
    val busy by viewModel.busy.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val close = onDismiss
    AlertDialog(
        modifier = Modifier.testTag("secret-deletion-dialog"),
        containerColor = AppColors.PopUp,
        titleContentColor = AppColors.White,
        textContentColor = AppColors.White75,
        onDismissRequest = { if (!busy) close() },
        title = { Text(appString(if (incoming) AppString.deletionApproval else AppString.removeSecret) + "?", style = ui.theme.AppTextStyles.SectionTitle()) },
        text = {
            Column {
                Text(text, style = ui.theme.AppTextStyles.Paragraph())
                if (!incoming && deletionState.requiresApproval) Text(appString(AppString.deletionRemoteRequired), style = ui.theme.AppTextStyles.Paragraph())
                if (busy) CircularProgressIndicator()
                if (failed) Text(appString(AppString.deletionFailed), style = ui.theme.AppTextStyles.Paragraph(), color = AppColors.RedError)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, modifier = Modifier.testTag("confirm-secret-deletion"), onClick = {
                if (incoming) viewModel.decide(deletionState, true, close)
                else viewModel.removeSecret(secret, close)
            }) { Text(appString(if (incoming) AppString.accept else AppString.remove), style = ui.theme.AppTextStyles.BodyStrong(), color = AppColors.RedError) }
        },
        dismissButton = {
            Column {
                if (incoming) TextButton(enabled = !busy, modifier = Modifier.testTag("decline-secret-deletion"), onClick = {
                    viewModel.decide(deletionState, false, close)
                }) { Text(appString(AppString.decline), style = ui.theme.AppTextStyles.BodyStrong()) }
                TextButton(enabled = !busy, onClick = close) { Text(appString(AppString.cancel), style = ui.theme.AppTextStyles.BodyStrong()) }
            }
        },
    )
}
