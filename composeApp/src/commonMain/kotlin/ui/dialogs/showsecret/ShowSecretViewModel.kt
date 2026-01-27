package ui.dialogs.showsecret

import androidx.lifecycle.viewModelScope
import core.LogTag
import core.NotificationCoordinatorInterface
import core.StringProviderInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.ClaimStatus
import models.apiModels.DistributionType
import models.apiModels.VaultFullInfo
import models.appInternalModels.SecretModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class ShowSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _recoveredSecret = MutableStateFlow<String?>(null)
    val recoveredSecret: StateFlow<String?> = _recoveredSecret
    
    private var currentSecretName: String? = null
    private var userRequestedRecovery = false

    override fun handle(event: CommonViewModelEventsInterface) {
        logger.log(LogTag.ShowSecretVM.Message.HandleEvent, "$event", success = true)
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.RecoverSecretId, event.secretName, success = true)
                    currentSecretName = event.secretName
                    userRequestedRecovery = true
                    recoverSecret(event.secretName)
                }
                
                is ShowSecretEvents.SecretReadyToShow -> {
                    if (!userRequestedRecovery) {
                        logger.log(LogTag.ShowSecretVM.Message.IgnoringAutoRecovery, event.secretId, success = true)
                        return
                    }
                    logger.log(LogTag.ShowSecretVM.Message.SecretIdMatches, event.secretId, success = true)
                    if (event.secretId == currentSecretName) {
                        showRecoveredSecret(event.secretId)
                    }
                }

                ShowSecretEvents.HideSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.HideSecret, success = true)
                    _recoveredSecret.value = null
                    currentSecretName = null
                    userRequestedRecovery = false
                }
            }
        }
    }

    private fun recoverSecret(secretName: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartRecovering, success = true)
        
        socketHandler.clearProcessedRecoverClaims()
        
        if (devicesCount.value < 2) {
            logger.log(LogTag.ShowSecretVM.Message.SingleDeviceMode, success = true)
            showRecoveredSecret(secretName)
            return
        }
        
        viewModelScope.launch {
            try {
                val existingClaim = withContext(Dispatchers.IO) {
                    metaSecretAppManager.findClaim(secretName)
                }
                
                if (existingClaim?.claimId != null) {
                    val isClaimUsed = checkIfClaimAlreadyUsed(existingClaim.claimId)
                    
                    if (isClaimUsed) {
                        logger.log(LogTag.ShowSecretVM.Message.ClaimAlreadyUsed, existingClaim.claimId, success = true)
                        handleNoValidClaim(secretName)
                    } else {
                        logger.log(LogTag.ShowSecretVM.Message.ExistingClaimFound, existingClaim.claimId, success = true)
                        showRecoveredSecret(secretName)
                    }
                } else {
                    handleNoValidClaim(secretName)
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.RecoverFailed, "${t.message}", success = false)
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun handleNoValidClaim(secretName: String) {
        val hasPendingClaim = checkForPendingClaim(secretName)
        if (hasPendingClaim) {
            logger.log(LogTag.ShowSecretVM.Message.PendingClaimExists, secretName, success = true)
            _isLoading.value = false
            notificationCoordinator.showSuccess(stringProvider.recoverPendingExists())
        } else {
            logger.log(LogTag.ShowSecretVM.Message.NoExistingClaim, success = true)
            withContext(Dispatchers.IO) {
                metaSecretAppManager.recover(secretModel = SecretModel(secretName, null))
            }
            notificationCoordinator.showSuccess(stringProvider.recoverRequestSent())
        }
    }
    
    private suspend fun checkIfClaimAlreadyUsed(claimId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val appState = metaSecretAppManager.getStateModel() ?: return@withContext false
            val currentDeviceId = appState.getCurrentDeviceId() ?: return@withContext false
            val vaultFullInfo = appState.getVaultFullInfo()
            
            if (vaultFullInfo is VaultFullInfo.Member) {
                val claims = vaultFullInfo.member.ssClaims?.claims ?: return@withContext false
                val claim = claims[claimId] ?: return@withContext false
                
                if (claim.sender == currentDeviceId) {
                    val senderStatus = claim.status.statuses[currentDeviceId]
                    return@withContext senderStatus == ClaimStatus.DELIVERED
                }
            }
            false
        }
    }
    
    private suspend fun checkForPendingClaim(secretName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val appState = metaSecretAppManager.getStateModel() ?: return@withContext false
            val currentDeviceId = appState.getCurrentDeviceId() ?: return@withContext false
            val vaultFullInfo = appState.getVaultFullInfo()
            
            if (vaultFullInfo is VaultFullInfo.Member) {
                val claims = vaultFullInfo.member.ssClaims?.claims ?: return@withContext false
                
                claims.values.any { claim ->
                    val isRecoverType = claim.distributionType == DistributionType.RECOVER
                    val isForThisSecret = claim.distClaimId.passId.name == secretName
                    val isSenderForThisDevice = claim.sender == currentDeviceId
                    val hasPendingStatus = claim.receivers.any { receiverId ->
                        claim.status.statuses[receiverId] == ClaimStatus.PENDING
                    }
                    isRecoverType && isForThisSecret && isSenderForThisDevice && hasPendingStatus
                }
            } else {
                false
            }
        }
    }

    private fun showRecoveredSecret(secretId: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartShowingRecovered, success = true)
        viewModelScope.launch {
            try {
                val recoveredSecretValue = withContext(Dispatchers.IO) {
                    metaSecretAppManager.showRecovered(SecretModel(secretId, null))
                }
                if (recoveredSecretValue != null) {
                    _recoveredSecret.value = recoveredSecretValue
                    userRequestedRecovery = false
                    logger.log(LogTag.ShowSecretVM.Message.RecoveredSecretLoaded, success = true)
                } else {
                    logger.log(LogTag.ShowSecretVM.Message.FailedToRecoverSecret, success = false)
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.ShowRecoveredFailed, "${t.message}", success = false)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

sealed class ShowSecretEvents : CommonViewModelEventsInterface {
    data class ShowSecret(val secretName: String) : ShowSecretEvents()
    data class SecretReadyToShow(val secretId: String) : ShowSecretEvents()
    data object HideSecret : ShowSecretEvents()
}