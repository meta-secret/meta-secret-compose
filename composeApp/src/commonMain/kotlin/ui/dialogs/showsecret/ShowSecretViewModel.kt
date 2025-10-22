package ui.dialogs.showsecret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import core.Device
import core.KeyValueStorageInterface
import core.LogTags
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
    private val socketHandler: MetaSecretSocketHandlerInterface
) : ViewModel(), CommonViewModel {

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    override fun handle(event: CommonViewModelEventsInterface) {
        println("✅" + LogTags.SHOW_SECRET_VM + ": need handle event $event")
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    println("✅" + LogTags.SHOW_SECRET_VM + ": recover secretId ${event.secretId}")
                    recoverSecret(event.secretId)
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
                _isLoading.value = false
            }
        }
    }
}

sealed class ShowSecretEvents : CommonViewModelEventsInterface {
    data class ShowSecret(val secretId: String) : ShowSecretEvents()
}