package sharedData.metaSecretCore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.StateType
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface
): MetaSecretSocketHandlerInterface {
    private val _actionType = MutableStateFlow(SocketActionModel.NONE)
    val actionType: StateFlow<SocketActionModel> = _actionType

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()

    private var isLocked = false
    private var timerJob: Job? = null
    private val timerScope = CoroutineScope(Dispatchers.Default)

    init {
        startFollowing()
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        println("✅ Update actions to follow")

        exclude?.let { toExclude ->
            actionsToFollow.removeAll(toExclude.toSet())
        }
        
        add?.let { toAdd ->
            actionsToFollow.addAll(toAdd)
        }

        println("✅ Actual actions to follow: $actionsToFollow")
    }

    private fun startFollowing() {
        println("⏱\uFE0F Timer is started")
        stopTimer()
        timerJob = timerScope.launch {
            while (isActive) {
                searchRequest()
                delay(5000)
            }
        }
    }

    private fun searchRequest() {
        if (!isLocked && actionsToFollow.isNotEmpty()) {
            println("✅ Fire the timer!")
            isLocked = true
            val stateJson = metaSecretCore.getAppState()
            val currentState = MetaSecretCoreStateModel.fromJson(stateJson)
//            val stateType = currentState.getState()

            if (!currentState.success) {
                isLocked = false
                return
            }

            if (actionsToFollow.contains(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)) {
                println("✅ Need to show Ask to join pop up")
                _actionType.value = SocketActionModel.ASK_TO_JOIN
            }

            if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
                println("✅ Waiting for join response")
                _actionType.value = SocketActionModel.JOIN_REQUEST_ACCEPTED
            }

            isLocked = false
        }
    }

    private fun stopTimer() {
        println("⏱\uFE0F Timer is stopped")
        timerJob?.cancel()
        timerJob = null
    }

    fun dispose() {
        stopTimer()
        timerScope.cancel()
    }
}