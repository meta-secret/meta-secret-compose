package scenes.mainscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import org.koin.core.component.KoinComponent
import sharedData.metaSecretCore.MetaSecretCoreInterface
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import ui.TabStateHolder

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretCore: MetaSecretCoreInterface
) : ViewModel(), KoinComponent {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _showJoinRequestDialog = MutableStateFlow(false)
    val showJoinRequestDialog: StateFlow<Boolean> = _showJoinRequestDialog

    init {
        println("✅ Start to follow RESPONSIBLE_TO_ACCEPT_JOIN")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )
        
        viewModelScope.launch {
            socketHandler.actionType.collectLatest { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    println("✅ New state for Join request has been gotten")
                    _showJoinRequestDialog.value = true
                }
            }
        }
    }

    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
    
    fun hideJoinRequestDialog() {
        println("✅ Hide Join Request")
        _showJoinRequestDialog.value = false
    }
    
    fun acceptJoinRequest() {
        println("✅ Accept Join Request pressed")
        metaSecretCore.acceptJoinRequest()
        _showJoinRequestDialog.value = false
    }
    
    fun declineJoinRequest() {
        println("✅ Decline Join Request pressed")
        metaSecretCore.declineJoinRequest()
        _showJoinRequestDialog.value = false
    }
}
