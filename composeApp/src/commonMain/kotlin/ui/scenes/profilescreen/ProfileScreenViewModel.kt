package ui.scenes.profilescreen

import androidx.lifecycle.viewModelScope
import core.DeviceInfoProviderInterface
import core.VaultStatsProviderInterface
import kotlinx.coroutines.flow.StateFlow
import core.LogTag
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.launch
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class ProfileScreenViewModel(
    val deviceInfoProvider: DeviceInfoProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface
) : CommonViewModel() {

    val vaultName: StateFlow<String?> = vaultStatsProvider.vaultName
    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount
    val secretsCount: StateFlow<Int> = vaultStatsProvider.secretsCount

    init {
        logger.log(LogTag.ProfileVM.Message.FollowGetState, success = true)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.GET_STATE),
            exclude = null
        )

        viewModelScope.launch {
            socketHandler.socketActions.collect { actionType ->
                if (actionType == SocketActionModel.UPDATE_STATE) {
                    logger.log(LogTag.ProfileVM.Message.NewStateReceived, success = true)
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
        logger.log(LogTag.ProfileVM.Message.LoadProfileData, success = true)
        viewModelScope.launch {
            try {
                vaultStatsProvider.refresh()
            } catch (t: Throwable) {
                logger.log(LogTag.ProfileVM.Message.LoadProfileDataFailed, "${t.message}", success = false)
            }
        }
    }
}

sealed class ProfileEvents : CommonViewModelEventsInterface {
    data object LoadProfileData : ProfileEvents()
}