package ui.scenes.profilescreen

import androidx.lifecycle.viewModelScope
import core.AppStateCacheProviderInterface
import core.DeviceInfoProviderInterface
import core.KeyChainInterface
import core.VaultStatsProviderInterface
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val keyChainManager: KeyChainInterface,
    private val appStateCacheProvider: AppStateCacheProviderInterface,
) : CommonViewModel() {

    val vaultName: StateFlow<String?> = vaultStatsProvider.vaultName
    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount
    val secretsCount: StateFlow<Int> = vaultStatsProvider.secretsCount
    private val _navigationEvent = MutableStateFlow<ProfileNavigationEvent>(ProfileNavigationEvent.Idle)
    val navigationEvent: StateFlow<ProfileNavigationEvent> = _navigationEvent

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

                ProfileEvents.ResetAllData -> {
                    viewModelScope.launch {
                        resetAllData()
                    }
                }
            }
        }
    }

    fun consumeNavigationEvent() {
        _navigationEvent.value = ProfileNavigationEvent.Idle
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

    private suspend fun resetAllData() {
        logger.log(LogTag.ProfileVM.Message.ResetAllData, success = true)
        try {
            appStateCacheProvider.clearCache()
            val isCleared = keyChainManager.clearAll(isCleanDB = true)
            if (isCleared) {
                _navigationEvent.value = ProfileNavigationEvent.NavigateToSignIn
            }
        } catch (t: Throwable) {
            logger.log(LogTag.ProfileVM.Message.ResetAllDataFailed, "${t.message}", success = false)
        }
    }
}

sealed class ProfileEvents : CommonViewModelEventsInterface {
    data object LoadProfileData : ProfileEvents()
    data object ResetAllData : ProfileEvents()
}

sealed class ProfileNavigationEvent {
    data object Idle : ProfileNavigationEvent()
    data object NavigateToSignIn : ProfileNavigationEvent()
}
