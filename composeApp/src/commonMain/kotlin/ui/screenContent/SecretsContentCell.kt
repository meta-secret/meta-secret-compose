package ui.screenContent

import core.AppString
import core.AppImage
import core.ImageProviderInterface

import core.appString

import ui.theme.AppTextStyles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinproject.composeapp.generated.resources.device
import kotlinproject.composeapp.generated.resources.devices_4
import kotlinproject.composeapp.generated.resources.devices_5
import kotlinproject.composeapp.generated.resources.level_1
import kotlinproject.composeapp.generated.resources.level_2
import kotlinproject.composeapp.generated.resources.level_3
import kotlinproject.composeapp.generated.resources.manrope_bold
import kotlinproject.composeapp.generated.resources.manrope_regular
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import core.AppColors
import models.appInternalModels.DevicesQuantity
import core.Secret
import org.koin.compose.koinInject
import ui.ClassicButton
import ui.scenes.secretsscreen.SecretPrimaryAction

data class SecretCardState(
    val secret: Secret,
    val devicesCount: Int,
    val primaryAction: SecretPrimaryAction,
    val pendingRecoveryRequests: Int = 0,
    val pendingRequestSender: String? = null,
    val deletionState: models.apiModels.SecretDeletionState? = null,
)

@Composable
fun SecretsContent(
    state: SecretCardState,
    onPrimaryAction: () -> Unit,
    onOpenRecoveryRequest: () -> Unit,
    onDelete: () -> Unit,
    onOpenDeletionRequest: () -> Unit,
) {
    val (secret, devicesCount, primaryAction, pendingRecoveryRequests, pendingRequestSender, deletionState) = state
    val imageProvider: ImageProviderInterface = koinInject()
    val deviceText = when {
        devicesCount == 0 || devicesCount > 4 -> appString(AppString.devices_5)
        devicesCount in 2..4 -> appString(AppString.devices_4)
        else -> appString(AppString.device)
    }

    var protectionLevelShield = imageProvider.getPainter(AppImage.ShieldL1)
    var protectionLevelText = appString(AppString.level_1)

    when (devicesCount) {
        DevicesQuantity.OneDevice.amount -> {
            protectionLevelShield = imageProvider.getPainter(AppImage.ShieldL1)
            protectionLevelText = appString(AppString.level_1)
        }

        DevicesQuantity.TwoDevices.amount -> {
            protectionLevelShield = imageProvider.getPainter(AppImage.ShieldL2)
            protectionLevelText = appString(AppString.level_2)
        }

        DevicesQuantity.ThreeDevices.amount -> {
            protectionLevelShield = imageProvider.getPainter(AppImage.ShieldL3)
            protectionLevelText = appString(AppString.level_3)
        }
    }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .testTag("secret-row-${secret.secretName}")
                    .background(AppColors.White5, RoundedCornerShape(12.dp)).height(205.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.height(22.dp),
                                text = secret.secretName,
                                style = AppTextStyles.Strong18().copy(color = AppColors.White)
                            )
                            if (pendingRecoveryRequests > 0) {
                                Text(
                                    text = "✉ $pendingRecoveryRequests",
                                    color = AppColors.White,
                                    style = AppTextStyles.Micro(),
                                    modifier = Modifier
                                        .testTag("recovery-request-badge-${secret.secretName}")
                                        .background(AppColors.RedError, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                painter = imageProvider.getPainter(AppImage.DevicesLogo),
                                contentDescription = null,
                                tint = AppColors.White75
                            )
                            Text(
                                text = "$devicesCount $deviceText",
                                style = AppTextStyles.Paragraph().copy(color = AppColors.White75),
                                modifier = Modifier.testTag("secret-devices-count-${secret.secretName}"),
                            )
                        }
                    }
                      Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = protectionLevelShield,
                            contentDescription = null,
                            modifier = Modifier.height(27.dp),
                        )
                        Text(
                            modifier = Modifier.height(22.dp),
                            text = protectionLevelText,
                            style = AppTextStyles.Micro().copy(color = AppColors.White75)
                        )
                      }
                    }
                    if (pendingRecoveryRequests > 0 && pendingRequestSender != null) {
                        Text(
                            text = "$pendingRequestSender ${appString(AppString.requestsRecovery)}",
                            color = AppColors.White75,
                            style = AppTextStyles.Micro(),
                            modifier = Modifier.testTag("recovery-request-notice-${secret.secretName}"),
                        )
                    }
                    when (deletionState?.clientStatus) {
                        models.apiModels.SecretDeletionStatus.Pending -> Text(
                            appString(AppString.deletionPending), style = AppTextStyles.Micro(), color = AppColors.White75,
                            modifier = Modifier.testTag("deletion-pending-${secret.secretName}"))
                        models.apiModels.SecretDeletionStatus.NeedApprove -> TextButton(
                            onClick = onOpenDeletionRequest,
                            modifier = Modifier.testTag("open-deletion-request-${secret.secretName}")) {
                            Text(appString(AppString.deletionApproval), style = AppTextStyles.BodyStrong(), color = AppColors.RedError)
                        }
                        else -> TextButton(
                            enabled = deletionState != null,
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete-secret-${secret.secretName}")) {
                            Text(appString(AppString.removeSecret), style = AppTextStyles.BodyStrong(), color = AppColors.RedError)
                        }
                    }
                    if (deletionState?.clientStatus == models.apiModels.SecretDeletionStatus.Declined) {
                        Text(appString(AppString.deletionDeclined), style = AppTextStyles.Micro(), color = AppColors.White75)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pendingRecoveryRequests > 0) {
                            ClassicButton(
                                action = onOpenRecoveryRequest,
                                text = appString(AppString.openRecoveryRequest),
                                fillMaxWidth = false,
                                modifier = Modifier.weight(1f).testTag("open-recovery-request-${secret.secretName}"),
                            )
                        }
                        ClassicButton(
                            action = onPrimaryAction,
                            // Keep the card action short: the full “Recover
                            // secret” label belongs in the dialog, while the
                            // compact card must remain usable beside Open request.
                            text = if (primaryAction == SecretPrimaryAction.Show) appString(AppString.show) else appString(AppString.recover),
                            fillMaxWidth = false,
                            modifier = Modifier.weight(1f).testTag("secret-primary-action-${secret.secretName}"),
                        )
                    }
                }
            }
}
