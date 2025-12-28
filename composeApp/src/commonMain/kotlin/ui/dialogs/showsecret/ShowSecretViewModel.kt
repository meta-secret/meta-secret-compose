package ui.dialogs.showsecret

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import core.KeyValueStorageInterface
import core.LogTag
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class ShowSecretViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val mainScreenViewModel: ui.scenes.mainscreen.MainScreenViewModel,
    private val vaultStatsProvider: VaultStatsProviderInterface,
) : CommonViewModel() {

    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _recoveredSecret = MutableStateFlow<String?>(null)
    val recoveredSecret: StateFlow<String?> = _recoveredSecret

    init {
        viewModelScope.launch {
            mainScreenViewModel.secretIdToShow.collect { secretId ->
                if (secretId != null) {
                    showRecoveredSecret(secretId)
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        logger.log(LogTag.ShowSecretVM.Message.HandleEvent, "$event", success = true)
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.RecoverSecretId, "${event.secretId}", success = true)
                    val currentSecretIdToShow = mainScreenViewModel.secretIdToShow.value
                    if (currentSecretIdToShow == event.secretId) {
                        logger.log(LogTag.ShowSecretVM.Message.SecretIdMatches, success = true)
                        showRecoveredSecret(event.secretId)
                    } else {
                        logger.log(LogTag.ShowSecretVM.Message.SecretIdNotMatches, success = true)
                        recoverSecret(event.secretId)
                    }
                }

                ShowSecretEvents.HideSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.HideSecret, success = true)
                    _recoveredSecret.value = null
                    mainScreenViewModel.clearSecretIdToShow()
                }
            }
        }
    }

    private fun recoverSecret(secretId: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartRecovering, success = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    socketHandler.actionsToFollow(
                        null,
                        listOf(SocketRequestModel.WAIT_FOR_RECOVER_REQUEST)
                    )
                    metaSecretAppManager.recover(secretModel = SecretModel(secretId, null))
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.RecoverFailed, "${t.message}", success = false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun showRecoveredSecret(secretId: String) {
        logger.log(LogTag.ShowSecretVM.Message.StartShowingRecovered, success = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val recoveredSecretValue = metaSecretAppManager.showRecovered(SecretModel(secretId, null))
                    withContext(Dispatchers.Main) {
                        if (recoveredSecretValue != null) {
                            _recoveredSecret.value = recoveredSecretValue
                            logger.log(LogTag.ShowSecretVM.Message.RecoveredSecretLoaded, success = true)
                        } else {
                            logger.log(LogTag.ShowSecretVM.Message.FailedToRecoverSecret, success = false)
                        }
                    }
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
    data class ShowSecret(val secretId: String) : ShowSecretEvents()
    object HideSecret: ShowSecretEvents()
}