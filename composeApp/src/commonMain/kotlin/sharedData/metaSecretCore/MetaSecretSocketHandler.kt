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
import models.apiModels.AppStateModel
import models.apiModels.JoinClusterRequest
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.UserStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val appManager: MetaSecretAppManagerInterface
): MetaSecretSocketHandlerInterface {
    private val _actionType = MutableStateFlow(SocketActionModel.NONE)
    override val actionType: StateFlow<SocketActionModel> = _actionType

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()

    private var isLocked = false
    private var timerJob: Job? = null
    private val timerScope = CoroutineScope(Dispatchers.Default)

    init {
        println("\uD83D\uDD0C Socket: init")
        startFollowing()
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        println("✅\uD83D\uDD0C Socket: Update actions to follow")

        exclude?.let { toExclude ->
            actionsToFollow.removeAll(toExclude.toSet())
        }
        
        add?.let { toAdd ->
            actionsToFollow.addAll(toAdd)
        }

        println("✅\uD83D\uDD0C Socket: Actual actions to follow: $actionsToFollow")
    }

    private fun startFollowing() {
        println("⏱\uFE0F \uD83D\uDD0C Socket: Timer is started")
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
            println("\uD83D\uDD0C ✅Socket: Fire the timer!")
            isLocked = true
            val stateJson = metaSecretCore.getAppState()
            val currentState = AppStateModel.fromJson(stateJson)

            if (!currentState.success) {
                isLocked = false
                return
            }

            if (actionsToFollow.contains(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN)) {
                val state = appManager.getStateModel()
                val hasJoinRequests = state?.getVaultEvents()?.hasJoinRequests() == true
                println("✅\uD83D\uDD0C Socket: AppState is $state, hasJoinRequest is $hasJoinRequests")

                if (state?.success == true && hasJoinRequests) {
                    println("\uD83D\uDD0C ✅Socket: Need to show Ask to join pop up")
                    _actionType.value = SocketActionModel.ASK_TO_JOIN
                }
            }

            if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
                println("\uD83D\uDD0C ✅Socket: Waiting for join response")

                when (currentState.getVaultFullInfo()) {
                    is VaultFullInfo.Member -> _actionType.value = SocketActionModel.JOIN_REQUEST_ACCEPTED
                    is VaultFullInfo.NotExists -> _actionType.value = SocketActionModel.NONE
                    is VaultFullInfo.Outsider -> {
                        when (currentState.getOutsiderStatus()) {
                            UserDataOutsiderStatus.NON_MEMBER -> { _actionType.value = SocketActionModel.NONE }
                            UserDataOutsiderStatus.PENDING -> { _actionType.value = SocketActionModel.JOIN_REQUEST_PENDING }
                            UserDataOutsiderStatus.DECLINED -> { _actionType.value = SocketActionModel.JOIN_REQUEST_DECLINED }
                            null -> _actionType.value = SocketActionModel.NONE
                        }
                    }
                    null -> _actionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                }
            }

            isLocked = false
        } else {
            println("\uD83D\uDD0C ✅Socket: NO any subscriptions")
        }
    }

    private fun stopTimer() {
        println("⏱\uFE0F \uD83D\uDD0C Socket: Timer is stopped")
        timerJob?.cancel()
        timerJob = null
    }

    fun dispose() {
        stopTimer()
        timerScope.cancel()
    }
}