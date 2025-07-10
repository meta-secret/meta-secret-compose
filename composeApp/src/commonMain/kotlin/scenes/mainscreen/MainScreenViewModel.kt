package scenes.mainscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import models.appInternalModels.UpdateMemberActionModel
import org.koin.core.component.KoinComponent
import scenes.common.CommonViewModel
import scenes.common.CommonViewModelEventsInterface
import sharedData.metaSecretCore.MetaSecretAppManagerInterface
import sharedData.metaSecretCore.MetaSecretCoreInterface
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import ui.TabStateHolder

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val metaSecretCore: MetaSecretCoreInterface
) : ViewModel(), KoinComponent, CommonViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount

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

                    _joinRequestsCount.value = metaSecretAppManager.getJoinRequestsCount()
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is MainViewEvents) {
            when (event) {
                is MainViewEvents.SetTabIndex -> setTabIndex(event.index)
                is MainViewEvents.AcceptJoinRequest -> acceptJoinRequest()
                is MainViewEvents.DeclineJoinRequest -> declineJoinRequest()
            }
        }
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
    
    private fun hideJoinRequestDialog() {
        println("✅ Hide Join Request")
        _joinRequestsCount.value = null
    }
    
    private fun acceptJoinRequest() {
        println("✅ Accept Join Request pressed")
        val userUpdateAction = Json.encodeToString(UpdateMemberActionModel.Accept)
        val candidates = metaSecretAppManager.getJoinRequestsCandidate()
        candidates?.first()?.let {
            metaSecretCore.updateMembership(it.candidate, userUpdateAction)
        }
        _joinRequestsCount.value = null
    }

    private fun declineJoinRequest() {
        println("✅ Decline Join Request pressed")
        val userUpdateAction = Json.encodeToString(UpdateMemberActionModel.Decline)
        val candidates = metaSecretAppManager.getJoinRequestsCandidate()
        candidates?.first()?.let {
            metaSecretCore.updateMembership(it.candidate, userUpdateAction)
        }
        _joinRequestsCount.value = null
    }
}

sealed class MainViewEvents : CommonViewModelEventsInterface {
    data class SetTabIndex(val index: Int) : MainViewEvents()
    data object AcceptJoinRequest : MainViewEvents()
    data object DeclineJoinRequest : MainViewEvents()
}