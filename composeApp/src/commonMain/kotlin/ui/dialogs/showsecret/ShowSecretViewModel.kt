package ui.dialogs.showsecret

import androidx.lifecycle.viewModelScope
import core.LogTag
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SecretModel
import models.apiModels.ClaimStatus
import models.apiModels.VaultFullInfo
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class ShowSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
) : CommonViewModel() {

    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _recoveredSecret = MutableStateFlow<String?>(null)
    val recoveredSecret: StateFlow<String?> = _recoveredSecret
    
    private var currentSecretName: String? = null

    override fun handle(event: CommonViewModelEventsInterface) {
        logger.log(LogTag.ShowSecretVM.Message.HandleEvent, "$event", success = true)
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.RecoverSecretId, event.secretName, success = true)
                    currentSecretName = event.secretName
                    recoverSecret(event.secretName)
                }
                
                is ShowSecretEvents.SecretReadyToShow -> {
                    logger.log(LogTag.ShowSecretVM.Message.SecretIdMatches, event.secretId, success = true)
                    if (event.secretId == currentSecretName) {
                        showRecoveredSecret(event.secretId)
                    }
                }

                ShowSecretEvents.HideSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.HideSecret, success = true)
                    _recoveredSecret.value = null
                    currentSecretName = null
                }
            }
        }
    }

    private fun recoverSecret(secretName: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartRecovering, success = true)
        
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
                    val vaultInfo = withContext(Dispatchers.IO) {
                        metaSecretAppManager.getVaultFullInfoModel()
                    }
                    val currentDeviceId = if (vaultInfo is VaultFullInfo.Member) {
                        vaultInfo.member.member.member.userData.device.deviceId
                    } else null
                    
                    val claimStatus = if (vaultInfo is VaultFullInfo.Member && currentDeviceId != null) {
                        val claim = vaultInfo.member.ssClaims?.claims?.get(existingClaim.claimId)
                        claim?.status?.statuses?.get(currentDeviceId)
                    } else null
                    
                    if (claimStatus == ClaimStatus.DELIVERED) {
                        logger.log(LogTag.ShowSecretVM.Message.ExistingClaimFound, "${existingClaim.claimId} (Delivered, creating new)", success = true)
                        withContext(Dispatchers.IO) {
                            metaSecretAppManager.recover(secretModel = SecretModel(secretName, null))
                        }
                    } else {
                        logger.log(LogTag.ShowSecretVM.Message.ExistingClaimFound, existingClaim.claimId, success = true)
                        showRecoveredSecret(secretName)
                    }
                } else {
                    logger.log(LogTag.ShowSecretVM.Message.NoExistingClaim, success = true)
                    withContext(Dispatchers.IO) {
                        metaSecretAppManager.recover(secretModel = SecretModel(secretName, null))
                    }
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.RecoverFailed, "${t.message}", success = false)
                _isLoading.value = false
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