package ui.scenes.profilescreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.DeviceInfoProviderInterface
import kotlinx.coroutines.flow.StateFlow
import core.KeyValueStorageInterface
import core.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class ProfileScreenViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    val deviceInfoProvider: DeviceInfoProviderInterface,
    private val appManager: MetaSecretAppManagerInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface
) : ViewModel(), CommonViewModel {

    private val _vaultName = MutableStateFlow<String?>(null)
    val vaultName: StateFlow<String?> = _vaultName

    private val _devicesCount = MutableStateFlow(0)
    val devicesCount: StateFlow<Int> = _devicesCount

    private val _secretsCount = MutableStateFlow(0)
    val secretsCount: StateFlow<Int> = _secretsCount

    init {
        println("✅${LogTags.PROFILE_VM}: Start to follow GET_STATE for profile updates")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.GET_STATE),
            exclude = null
        )

        viewModelScope.launch {
            socketHandler.socketActions.collect { actionType ->
                println("✅${LogTags.PROFILE_VM}: Socket action type is $actionType")
                if (actionType == SocketActionModel.UPDATE_STATE) {
                    println("✅${LogTags.PROFILE_VM}: New state received, refreshing profile data")
                    loadProfileData()
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is ProfileEvents) {
            when (event) {
                ProfileEvents.LoadProfileData -> {
                    loadProfileData()
                }
            }
        }
    }

    private fun loadProfileData() {
        println("✅${LogTags.PROFILE_VM}: loadProfileData")
        viewModelScope.launch {
            try {
                _vaultName.value = keyValueStorage.cachedVaultName
                println("✅${LogTags.PROFILE_VM}: vaultName = ${_vaultName.value}")

                val vaultSummary = withContext(Dispatchers.Default) {
                    appManager.getVaultSummary()
                }

                if (vaultSummary != null) {
                    _secretsCount.value = vaultSummary.secretsCount
                    _devicesCount.value = vaultSummary.users.size
                    println("✅${LogTags.PROFILE_VM}: secretsCount = ${_secretsCount.value}, devicesCount = ${_devicesCount.value}")
                } else {
                    println("❌${LogTags.PROFILE_VM}: vaultSummary is null")
                }
            } catch (t: Throwable) {
                println("❌${LogTags.PROFILE_VM}: loadProfileData failed: ${t.message}")
            }
        }
    }
}

sealed class ProfileEvents : CommonViewModelEventsInterface {
    data object LoadProfileData : ProfileEvents()
}