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

class VaultStatsProvider(
    private val appManager: MetaSecretAppManagerInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
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
            println("✅" + LogTags.VAULT_STATS_PROVIDER + ": Start to follow GET_STATE and RESPONSIBLE_TO_ACCEPT_JOIN for stats")
            socketHandler.actionsToFollow(add = listOf(SocketRequestModel.GET_STATE, SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN), exclude = null)
        }

        scope.launch {
            socketHandler.socketActions.collect { actionType ->
                if (actionType == SocketActionModel.UPDATE_STATE) {
                    println("✅" + LogTags.VAULT_STATS_PROVIDER + ": UPDATE_STATE received, refreshing stats")
                    refresh()
                }
            }
        }

        scope.launch {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    println("✅" + LogTags.VAULT_STATS_PROVIDER + ": ASK_TO_JOIN signal received, refreshing join requests count")
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
                    _devicesCount.value = vaultSummary.users.size
                    _vaultName.value = vaultSummary.vaultName
                    _joinRequestsCount.value = appManager.getJoinRequestsCount()
                    println("✅" + LogTags.VAULT_STATS_PROVIDER + ": Stats updated: secrets=${_secretsCount.value}, devices=${_devicesCount.value}, vaultName=${_vaultName.value}, joinRequestsCount = ${_joinRequestsCount.value}")
                } else {
                    println("❌" + LogTags.VAULT_STATS_PROVIDER + ": VaultSummary is null during stats refresh")
                }
            } catch (t: Throwable) {
                println("❌" + LogTags.VAULT_STATS_PROVIDER + ": Failed to refresh stats: ${t.message}")
            }
        }
    }
}


