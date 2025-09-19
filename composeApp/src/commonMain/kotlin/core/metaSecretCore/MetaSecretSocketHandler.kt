package core.metaSecretCore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import models.apiModels.AppStateModel
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class MetaSecretSocketHandler(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val appManager: MetaSecretAppManagerInterface
): MetaSecretSocketHandlerInterface {
    private val _socketActionType = MutableStateFlow(SocketActionModel.NONE)
    override val socketActionType: StateFlow<SocketActionModel> = _socketActionType

    private val _socketActions = MutableSharedFlow<SocketActionModel>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val socketActions: SharedFlow<SocketActionModel> = _socketActions

    private var actionsToFollow = mutableSetOf<SocketRequestModel>()
    private var isLocked = false
    private var timerJob: Job? = null
    private val timerScope = CoroutineScope(Dispatchers.Default)

    init {
        println("✅" + core.LogTags.SOCKET_HANDLER + ": init")
        startFollowing()
    }

    override fun actionsToFollow(
        add: List<SocketRequestModel>?,
        exclude: List<SocketRequestModel>?
    ) {
        println("✅" + core.LogTags.SOCKET_HANDLER + ": Update actions to follow")

        exclude?.let { toExclude ->
            actionsToFollow.removeAll(toExclude.toSet())
        }
        
        add?.let { toAdd ->
            actionsToFollow.addAll(toAdd)
        }

        println("✅" + core.LogTags.SOCKET_HANDLER + ": Actual actions to follow: $actionsToFollow")
    }

    private fun startFollowing() {
        println("✅" + core.LogTags.SOCKET_HANDLER + ": Timer is started")
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
            println("✅" + core.LogTags.SOCKET_HANDLER + ": Fire the timer!")
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
                println("✅" + core.LogTags.SOCKET_HANDLER + ": AppState is $state, hasJoinRequest is $hasJoinRequests")

                if (state?.success == true && hasJoinRequests) {
                    println("✅" + core.LogTags.SOCKET_HANDLER + ": Need to show Ask to join pop up")
                    _socketActionType.value = SocketActionModel.ASK_TO_JOIN
                }
            }

            if (actionsToFollow.contains(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)) {
                println("✅" + core.LogTags.SOCKET_HANDLER + ": Waiting for join response")

                when (currentState.getVaultFullInfo()) {
                    is VaultFullInfo.Member -> _socketActionType.value = SocketActionModel.JOIN_REQUEST_ACCEPTED
                    is VaultFullInfo.NotExists -> _socketActionType.value = SocketActionModel.NONE
                    is VaultFullInfo.Outsider -> {
                        when (currentState.getOutsiderStatus()) {
                            UserDataOutsiderStatus.NON_MEMBER -> { _socketActionType.value = SocketActionModel.NONE }
                            UserDataOutsiderStatus.PENDING -> { _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING }
                            UserDataOutsiderStatus.DECLINED -> { _socketActionType.value = SocketActionModel.JOIN_REQUEST_DECLINED }
                            null -> _socketActionType.value = SocketActionModel.NONE
                        }
                    }
                    null -> _socketActionType.value = SocketActionModel.JOIN_REQUEST_PENDING
                }
            }

            if (actionsToFollow.contains(SocketRequestModel.GET_STATE)) {
                println("✅" + core.LogTags.SOCKET_HANDLER + ": Waiting for state response")
                _socketActions.tryEmit(SocketActionModel.UPDATE_SECRETS)
            }

            isLocked = false
        } else {
            println("✅" + core.LogTags.SOCKET_HANDLER + ": NO any subscriptions")
        }
    }

    private fun stopTimer() {
        println("✅" + core.LogTags.SOCKET_HANDLER + ": Timer is stopped")
        timerJob?.cancel()
        timerJob = null
    }

    fun dispose() {
        stopTimer()
        timerScope.cancel()
    }
}