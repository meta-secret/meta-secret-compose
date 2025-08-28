package ui.scenes.mainscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.component.KoinComponent
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.BackupCoordinatorInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.Device
import core.KeyValueStorage
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyValueStorage: KeyValueStorage,
    private val backupCoordinatorInterface: BackupCoordinatorInterface,
) : ViewModel(), KoinComponent, CommonViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount

    private val _isWarningShown = MutableStateFlow(false)
    val isWarningShown: StateFlow<Boolean> = _isWarningShown

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        checkBackup()
        println("✅ MainScreenVM: Start to follow RESPONSIBLE_TO_ACCEPT_JOIN")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )
        
        viewModelScope.launch {

            socketHandler.actionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    println("✅ New state for Join request has been gotten")

                    _joinRequestsCount.value = metaSecretAppManager.getJoinRequestsCount()
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is MainViewEvents) {
            when (event) {
                is MainViewEvents.SetTabIndex -> setTabIndex(event.index)
                is MainViewEvents.ShowWarning -> changeWarningVisibilityTo(event.isToShow)
            }
        }
    }

    private fun checkBackup() {
        backupCoordinatorInterface.ensureBackupDestinationSelected()
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }

    private fun changeWarningVisibilityTo(state: Boolean) {
        _isWarningShown.value = state
    }
}

sealed class MainViewEvents : CommonViewModelEventsInterface {
    data class SetTabIndex(val index: Int) : MainViewEvents()
    data class ShowWarning(val isToShow: Boolean) : MainViewEvents()
}