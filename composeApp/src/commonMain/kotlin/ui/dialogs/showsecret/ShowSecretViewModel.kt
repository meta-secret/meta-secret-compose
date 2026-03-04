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
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
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

    init {
        viewModelScope.launch {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType is SocketActionModel.RECOVER_DECLINED &&
                    actionType.secretId == currentSecretName &&
                    _isLoading.value
                ) {
                    _isLoading.value = false
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        logger.log(LogTag.ShowSecretVM.Message.HandleEvent, "$event", success = true)
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.RecoverSecretId, event.secretName, success = true)
                    currentSecretName = event.secretName
                    userRequestedRecovery = true
                    socketHandler.pausePolling()
                    findClaim(event.secretName)
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

    private fun findClaim(secretName: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartRecovering, success = true)

        if (devicesCount.value < 2) { // TODO: Should be resolved inside the Lib
            logger.log(LogTag.ShowSecretVM.Message.SingleDeviceMode, success = true)
            showRecoveredSecret(secretName)
            socketHandler.resumePolling()
            return
        }
        
        viewModelScope.launch {
            try {
                val existingClaim = withContext(Dispatchers.IO) {
                    metaSecretAppManager.findClaim(secretName)
                }

                logger.log(LogTag.ShowSecretVM.Message.ExistingClaimFound, "$existingClaim", success = true)
                socketHandler.actionsToFollow(
                    add = listOf(SocketRequestModel.SHOW_SECRET),
                    exclude = null
                )
                when (existingClaim?.status) {
                    ClaimStatus.PENDING -> {
                        notificationCoordinator.showSuccess(stringProvider.recoverRequestSent())
                        socketHandler.resumePolling()
                    }
                    ClaimStatus.SENT -> {
                        if (existingClaim?.senderStatus == ClaimStatus.DELIVERED) {
                            recoverSecret(secretName)
                        } else {
                            showRecoveredSecret(secretName)
                        }
                        socketHandler.resumePolling()
                    }
                    else -> recoverSecret(secretName)
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.RecoverFailed, "${t.message}", success = false)
                _isLoading.value = false
                socketHandler.resumePolling()
            }
        }
    }
    
    private suspend fun recoverSecret(secretName: String) {
        socketHandler.setProcessingSecretName(secretName)
        withContext(Dispatchers.IO) {
            metaSecretAppManager.recover(secretModel = SecretModel(secretName, null))
        }
        notificationCoordinator.showSuccess(stringProvider.recoverRequestSent())
        socketHandler.resumePolling()
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
                socketHandler.actionsToFollow(
                    add = null,
                    exclude = listOf(SocketRequestModel.SHOW_SECRET)
                )
            }
        }
    }
}

sealed class ShowSecretEvents : CommonViewModelEventsInterface {
    data class ShowSecret(val secretName: String) : ShowSecretEvents()
    data class SecretReadyToShow(val secretId: String) : ShowSecretEvents()
    data object HideSecret : ShowSecretEvents()
}