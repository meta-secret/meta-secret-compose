package ui.scenes.secretsscreen

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import core.KeyValueStorageInterface
import core.ScreenMetricsProviderInterface
import core.Secret
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.VaultStatsProviderInterface
import kotlinx.coroutines.launch
import models.appInternalModels.SocketRequestModel
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import models.apiModels.ClientStatus
import models.apiModels.AppStateModel
import models.apiModels.DistributionType
import models.apiModels.VaultFullInfo

class SecretsScreenViewModel(
    private val keyValueStorage: KeyValueStorageInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val stateCache: core.AppStateCacheProviderInterface,
) : CommonViewModel() {

    private val secretsList: StateFlow<List<Secret>> = keyValueStorage.secretData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val secrets: StateFlow<List<Secret>> = secretsList
    val synchronization = stateCache.synchronization
    val deletionStates = stateCache.appState.map { state ->
        (state?.getVaultFullInfo() as? models.apiModels.VaultFullInfo.Member)?.member?.deletionStates.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun retrySynchronization() = socketHandler.refreshAppState()
    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount
    val primaryActions: StateFlow<Map<String, SecretPrimaryActionState>> = stateCache.appState
        .map(::secretPrimaryActionsFromState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            socketHandler.actionsToFollow(
                add = listOf(SocketRequestModel.GET_STATE),
                exclude = null
            )
        }

    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SecretsEvents) {
            when (event) {
                is SecretsEvents.GetSecret -> {
                    getSecret(event.index)
                }

                is SecretsEvents.SetTabIndex -> {
                    setTabIndex(event.index)
                }
            }
        }
    }

    fun getSecret(index: Int): Secret {
        return secretsList.value[index]
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }

}

/** Presentation of Core-projected statuses from one reconciled canonical snapshot. */
internal fun secretPrimaryActionsFromState(state: AppStateModel?): Map<String, SecretPrimaryActionState> {
    val member = (state?.takeIf { it.success }?.getVaultFullInfo() as? VaultFullInfo.Member)?.member
        ?: return emptyMap()
    val sender = member.member.member.userData.device.deviceId
    val vault = member.member.vault
    val claims = member.ssClaims?.claims?.values.orEmpty()
    return vault.secrets.associate { secret ->
        val active = claims.filter { claim ->
            claim.distributionType == DistributionType.RECOVER && claim.sender == sender &&
                claim.vaultName == vault.vaultName && claim.distClaimId.passId.id == secret.id &&
                claim.distClaimId.passId.name == secret.name &&
                claim.clientStatus != ClientStatus.DECLINED && claim.clientStatus != ClientStatus.DONE
        }.singleOrNull()
        secret.id to secretPrimaryActionState(active?.clientStatus, active?.id)
    }
}

enum class SecretPrimaryAction { Show, Recover }

data class SecretPrimaryActionState(
    val action: SecretPrimaryAction,
    val acceptedClaimId: String? = null,
)

internal fun secretPrimaryActionState(
    clientStatus: ClientStatus?,
    claimId: String?,
): SecretPrimaryActionState = if (clientStatus == ClientStatus.ACCEPTED && !claimId.isNullOrBlank()) {
    SecretPrimaryActionState(SecretPrimaryAction.Show, claimId)
} else {
    SecretPrimaryActionState(SecretPrimaryAction.Recover)
}

sealed class SecretsEvents : CommonViewModelEventsInterface {
    data class GetSecret(val index: Int) : SecretsEvents()
    data class SetTabIndex(val index: Int) : SecretsEvents()
}
