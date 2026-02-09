package core

import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SocketRequestModel
import models.apiModels.UserStatus
import models.apiModels.VaultFullInfo

class VaultStatsProvider(
    private val appStateCacheProvider: AppStateCacheProviderInterface,
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
            appStateCacheProvider.appState
                .filterNotNull()
                .collect { appState ->
                    logger.log(LogTag.VaultStatsProvider.Message.AppStateUpdated, success = true)
                    updateStatsFromState(appState)
                }
        }
    }

    private fun updateStatsFromState(appState: models.apiModels.AppStateModel) {
        try {
            val vaultInfo = appState.getVaultFullInfo()
            if (vaultInfo is VaultFullInfo.Member) {
                val vaultSummary = appState.getVaultSummary()
                if (vaultSummary != null) {
                    _secretsCount.value = vaultSummary.secretsCount
                    _devicesCount.value = vaultSummary.users.values.count { it.status == UserStatus.MEMBER }
                    _vaultName.value = vaultSummary.vaultName
                    _joinRequestsCount.value = vaultInfo.member.vaultEvents?.getJoinRequestsCount()
                } else {
                    logger.log(LogTag.VaultStatsProvider.Message.VaultSummaryNull, success = false)
                }
            } else {
                logger.log(LogTag.VaultStatsProvider.Message.AppStateNull, success = false)
            }
        } catch (t: Throwable) {
            logger.log(LogTag.VaultStatsProvider.Message.FailedToRefreshStats, "${t.message}", success = false)
        }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val cachedState = appStateCacheProvider.appState.value
            if (cachedState != null) {
                updateStatsFromState(cachedState)
            } else {
                logger.log(LogTag.VaultStatsProvider.Message.AppStateNull, success = false)
            }
        }
    }
}


