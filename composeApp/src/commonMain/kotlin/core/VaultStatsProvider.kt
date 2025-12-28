package core

import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import models.apiModels.UserStatus
import core.LogTag

class VaultStatsProvider(
    private val appManager: MetaSecretAppManagerInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val logger: DebugLoggerInterface,
) : VaultStatsProviderInterface {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _secretsCount = MutableStateFlow(0)
    override val secretsCount: StateFlow<Int> = _secretsCount.asStateFlow()

    private val _devicesCount = MutableStateFlow(0)
    override val devicesCount: StateFlow<Int> = _devicesCount.asStateFlow()

    private val _vaultName = MutableStateFlow<String?>(null)
    override val vaultName: StateFlow<String?> = _vaultName.asStateFlow()

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    override val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            logger.log(LogTag.VaultStatsProvider.Message.StartFollow, success = true)
            socketHandler.actionsToFollow(add = listOf(SocketRequestModel.GET_STATE, SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN), exclude = null)
        }

        scope.launch {
            socketHandler.socketActions.collect { actionType ->
                if (actionType == SocketActionModel.UPDATE_STATE) {
                    logger.log(LogTag.VaultStatsProvider.Message.UpdateStateReceived, success = true)
                    refresh()
                }
            }
        }

        scope.launch {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    logger.log(LogTag.VaultStatsProvider.Message.AskToJoinSignal, success = true)
                    refresh()
                }
            }
        }

        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                val vaultSummary = appManager.getVaultSummary()
                if (vaultSummary != null) {
                    _secretsCount.value = vaultSummary.secretsCount
                    _devicesCount.value = vaultSummary.users.values.count { it.status == UserStatus.MEMBER }
                    _vaultName.value = vaultSummary.vaultName
                    _joinRequestsCount.value = appManager.getJoinRequestsCount()
                    logger.log(LogTag.VaultStatsProvider.Message.StatsUpdated, "secrets=${_secretsCount.value}, devices(MEMBER)=${_devicesCount.value}, vaultName=${_vaultName.value}, joinRequestsCount = ${_joinRequestsCount.value}", success = true)
                } else {
                    logger.log(LogTag.VaultStatsProvider.Message.VaultSummaryNull, success = false)
                }
            } catch (t: Throwable) {
                logger.log(LogTag.VaultStatsProvider.Message.FailedToRefreshStats, "${t.message}", success = false)
            }
        }
    }
}


