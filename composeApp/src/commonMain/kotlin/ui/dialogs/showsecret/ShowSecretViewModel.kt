package ui.dialogs.showsecret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import core.KeyValueStorageInterface
import core.LogTags
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
) : ViewModel(), CommonViewModel {

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
        println("✅" + LogTags.SHOW_SECRET_VM + ": need handle event $event")
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    println("✅" + LogTags.SHOW_SECRET_VM + ": recover secretId ${event.secretId}")
                    val currentSecretIdToShow = mainScreenViewModel.secretIdToShow.value
                    if (currentSecretIdToShow == event.secretId) {
                        println("✅" + LogTags.SHOW_SECRET_VM + ": SecretId matches secretIdToShow, showing recovered secret")
                        showRecoveredSecret(event.secretId)
                    } else {
                        println("✅" + LogTags.SHOW_SECRET_VM + ": SecretId does not match secretIdToShow, starting recover process")
                        recoverSecret(event.secretId)
                    }
                }

                ShowSecretEvents.HideSecret -> {
                    println("✅" + LogTags.SHOW_SECRET_VM + ": hide secret")
                    _recoveredSecret.value = null
                    mainScreenViewModel.clearSecretIdToShow()
                }
            }
        }
    }

    private fun recoverSecret(secretId: String) {
        _isLoading.value = true
        println("✅" + LogTags.SHOW_SECRET_VM + ": Start recovering process")
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
                println("❌${LogTags.SHOW_SECRET_VM}: recover failed: ${t.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun showRecoveredSecret(secretId: String) {
        println("✅" + LogTags.SHOW_SECRET_VM + ": Start showing recovered secret")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val recoveredSecretValue = metaSecretAppManager.showRecovered(SecretModel(secretId, null))
                    withContext(Dispatchers.Main) {
                        if (recoveredSecretValue != null) {
                            _recoveredSecret.value = recoveredSecretValue
                            println("✅" + LogTags.SHOW_SECRET_VM + ": Recovered secret loaded successfully")
                        } else {
                            println("❌" + LogTags.SHOW_SECRET_VM + ": Failed to recover secret")
                        }
                    }
                }
            } catch (t: Throwable) {
                println("❌${LogTags.SHOW_SECRET_VM}: showRecovered failed: ${t.message}")
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