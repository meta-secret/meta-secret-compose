package ui.scenes.secretsscreen

import core.AppString

import core.appString

import core.AppImage
import core.ImageProviderInterface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.noSecrets
import kotlinproject.composeapp.generated.resources.noSecretsHeader
import kotlinproject.composeapp.generated.resources.secretAdded
import kotlinproject.composeapp.generated.resources.secretRemoved
import kotlinproject.composeapp.generated.resources.secretsHeader
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import ui.scenes.mainscreen.DevicesTab
import ui.scenes.mainscreen.MainScreenViewModel
import core.AppColors
import ui.AddButton
import ui.dialogs.addsecret.AddSecret
import ui.dialogs.showsecret.ShowSecret
import core.NotificationCoordinatorInterface
import kotlinx.coroutines.delay
import kotlinproject.composeapp.generated.resources.secretAddFailed
import org.koin.compose.koinInject
import ui.screenContent.CommonBackground
import ui.screenContent.SecretsContent
import ui.theme.AppTextStyles

class SecretsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: SecretsScreenViewModel = koinViewModel()
        val mainScreenViewModel: MainScreenViewModel = koinInject()
        val imageProvider: ImageProviderInterface = koinInject()
        val secretsList by viewModel.secrets.collectAsState()
        val synchronization by viewModel.synchronization.collectAsState()
        val deletionStates by viewModel.deletionStates.collectAsState()
        var deletionTarget by remember { mutableStateOf<core.Secret?>(null) }
        var deletionIncoming by remember { mutableStateOf(false) }
        val devicesCount by viewModel.devicesCount.collectAsState()
        val primaryActions by viewModel.primaryActions.collectAsState()
        var isAddSecretDialogVisible by remember { mutableStateOf(false) }
        var isShowSecretDialogVisible by remember { mutableStateOf(false) }
        var selectedSecret: core.Secret? by remember { mutableStateOf(null) }
        var selectedPrimaryAction by remember { mutableStateOf(SecretPrimaryAction.Show) }
        var selectedAcceptedClaimId by remember { mutableStateOf<String?>(null) }
        val isRedirected by remember { mutableStateOf(false) }
        val notificationCoordinator: NotificationCoordinatorInterface = koinInject()
        val recoveredSecretTarget by mainScreenViewModel.recoveredSecretTarget.collectAsState()
        val pendingRecoveryRequests by mainScreenViewModel.pendingRecoveryRequests.collectAsState()
        
        val secretAddSuccessText = appString(AppString.secretAdded)
        val secretAddFailedText = appString(AppString.secretAddFailed)

        if (isRedirected) {
            LocalTabNavigator.current.current = DevicesTab
            viewModel.handle(SecretsEvents.SetTabIndex(index = 1))
        }

        val blocked = synchronization.loading || synchronization.failed
        LaunchedEffect(secretsList, blocked) {
            if (selectedSecret != null && secretsList.none { it.secretId == selectedSecret?.secretId }) {
                selectedSecret = null
                isShowSecretDialogVisible = false
                mainScreenViewModel.clearSecretIdToShow()
            }
        }
        val deletionInstruction = deletionTarget?.let { target ->
            if (deletionIncoming) {
                models.apiModels.currentDeletionState(deletionStates, target.secretId)?.takeIf {
                    it.clientStatus == models.apiModels.SecretDeletionStatus.NeedApprove
                }
            } else models.apiModels.deletionPolicyState(deletionStates, target.secretId)
        }
        if (deletionInstruction != null) {
            deletionTarget?.let { target ->
                ui.dialogs.removesecret.RemoveSecret(
                    input = ui.dialogs.removesecret.SecretDeletionDialogInput(
                        secret = target,
                        text = androidx.compose.ui.text.AnnotatedString(target.secretName + " — " + appString(AppString.fromAllDevices)),
                        deletionState = deletionInstruction, incoming = deletionIncoming,
                    ),
                    onDismiss = { deletionTarget = null },
                )
            }
        }

        CommonBackground(AppString.secretsHeader) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(secretsList.sortedBy { it.secretName }) { _, secret ->
                    val requestsForSecret = pendingRecoveryRequests.filter { it.secretId == secret.secretName }
                    SecretsContent(
                        state = ui.screenContent.SecretCardState(
                            secret = secret,
                            devicesCount = devicesCount,
                            deletionState = models.apiModels.currentDeletionState(deletionStates, secret.secretId),
                            pendingRecoveryRequests = requestsForSecret.size,
                            primaryAction = if (devicesCount <= 2) SecretPrimaryAction.Show
                                else primaryActions[secret.secretId]?.action ?: SecretPrimaryAction.Recover,
                            pendingRequestSender = requestsForSecret.firstOrNull()?.senderType,
                        ),
                        onDelete = { deletionTarget = secret; deletionIncoming = false },
                        onOpenDeletionRequest = { deletionTarget = secret; deletionIncoming = true },
                        onPrimaryAction = {
                            selectedSecret = secret
                            selectedPrimaryAction = if (devicesCount <= 2) {
                                SecretPrimaryAction.Show
                            } else {
                                primaryActions[secret.secretId]?.action ?: SecretPrimaryAction.Recover
                            }
                            selectedAcceptedClaimId = if (devicesCount <= 2) {
                                null
                            } else {
                                primaryActions[secret.secretId]?.acceptedClaimId
                            }
                            isShowSecretDialogVisible = true
                        },
                        onOpenRecoveryRequest = {
                            // Resolve the current claim at click time. Do not
                            // capture requestsForSecret: after a repeated
                            // recovery the badge count can stay at one while
                            // the claim id changes between recompositions.
                            mainScreenViewModel.openRecoveryRequest(secret.secretName)
                        }
                    )
                }
            }
        }

        AddButton { isAddSecretDialogVisible = it }

        AnimatedVisibility(
            visible = isAddSecretDialogVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250)
            )
        ) {
            AddSecret(
                viewModel.screenMetricsProvider,
                dialogVisibility = { isAddSecretDialogVisible = it },
                onResult = { isSuccess ->
                    val message = if (isSuccess) {
                        secretAddSuccessText
                    } else {
                        secretAddFailedText
                    }
                    if (isSuccess) {
                        notificationCoordinator.showSuccess(message)
                    } else {
                        notificationCoordinator.showError(message)
                    }
                }
            )
        }

        if (isShowSecretDialogVisible && selectedSecret != null && secretsList.any { it.secretId == selectedSecret?.secretId }) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 1500)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 1000)
                )
            ) {
                selectedSecret?.let { secret ->
                    ShowSecret(
                        secret = secret,
                        primaryAction = selectedPrimaryAction,
                        acceptedClaimId = selectedAcceptedClaimId,
                        recoveredSecretTarget = recoveredSecretTarget,
                        onDismiss = {
                            isShowSecretDialogVisible = false
                        },
                        onClearSecretId = { mainScreenViewModel.clearSecretIdToShow() }
                    )
                }
            }
        }

        if (secretsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = imageProvider.getPainter(AppImage.Executioner),
                            contentDescription = null,
                            modifier = Modifier
                                .size((viewModel.screenMetricsProvider.heightFactor() * 220).dp)
                                .align(Alignment.Center),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = appString(AppString.noSecretsHeader),
                        color = AppColors.White,
                        style = AppTextStyles.SubsectionTitle(),
                        modifier = Modifier
                            .padding(top = 14.dp)
                    )
                    Text(
                        text = appString(AppString.noSecrets),
                        color = AppColors.White75,
                        textAlign = TextAlign.Center,
                        style = AppTextStyles.Paragraph(),
                        modifier = Modifier
                            .padding(vertical = 7.dp)
                    )
                }
            }
        }
        if (blocked) VaultSynchronizationOverlay(synchronization) { viewModel.retrySynchronization() }

    }
}
