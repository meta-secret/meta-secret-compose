package ui.dialogs.showsecret

import androidx.lifecycle.viewModelScope
import core.LogTag
import core.StringProviderInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.ClientStatus
import models.appInternalModels.SecretModel
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class ShowSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    val devicesCount: StateFlow<Int> = vaultStatsProvider.devicesCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _revealedSecret = MutableStateFlow<RevealedSecretContent?>(null)
    val revealedSecret: StateFlow<RevealedSecretContent?> = _revealedSecret

    private var currentSecretName: String? = null
    private var userRequestedRecovery = false

    init {
        viewModelScope.launch {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType is SocketActionModel.RECOVER_DECLINED &&
                    actionType.secretId == currentSecretName &&
                    _isLoading.value
                ) {
                    _isLoading.value = false
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        logger.log(LogTag.ShowSecretVM.Message.HandleEvent, "$event", success = true)
        if (event is ShowSecretEvents) {
            when (event) {
                is ShowSecretEvents.ShowSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.RecoverSecretId, event.secretName, success = true)
                    currentSecretName = event.secretName
                    userRequestedRecovery = true
                    socketHandler.pauseRefreshes()
                    findClaim(event.secretName)
                }

                is ShowSecretEvents.SecretReadyToShow -> {
                    if (!userRequestedRecovery) {
                        logger.log(LogTag.ShowSecretVM.Message.IgnoringAutoRecovery, event.secretId, success = true)
                        return
                    }
                    logger.log(LogTag.ShowSecretVM.Message.SecretIdMatches, event.secretId, success = true)
                    if (event.secretId == currentSecretName) {
                        showRecoveredSecret(event.secretId)
                    }
                }

                ShowSecretEvents.HideSecret -> {
                    logger.log(LogTag.ShowSecretVM.Message.HideSecret, success = true)
                    _revealedSecret.value = null
                    currentSecretName = null
                    userRequestedRecovery = false
                }
            }
        }
    }

    private fun findClaim(secretName: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartRecovering, success = true)

        // Two-device vaults are fully replicated in core, so the secret should already be local.
        if (devicesCount.value <= 2) {
            logger.log(LogTag.ShowSecretVM.Message.SingleDeviceMode, success = true)
            showRecoveredSecret(secretName)
            socketHandler.resumeRefreshes()
            return
        }

        viewModelScope.launch {
            try {
                val existingClaim = withContext(Dispatchers.IO) {
                    metaSecretAppManager.findClaim(secretName)
                }

                logger.log(LogTag.ShowSecretVM.Message.ExistingClaimFound, "$existingClaim", success = true)
                socketHandler.actionsToFollow(
                    add = listOf(SocketRequestModel.SHOW_SECRET),
                    exclude = null
                )
                socketHandler.setProcessingSecretName(secretName)

                when (existingClaim?.clientStatus) {
                    null -> {
                        logger.log(LogTag.ShowSecretVM.Message.NoExistingClaim, success = true)
                        recoverSecret(secretName)
                    }

                    ClientStatus.PENDING -> {
                        logger.log(
                            LogTag.ShowSecretVM.Message.PendingClaimExists,
                            "claimId=${existingClaim.claimId}",
                            success = true
                        )
                        showNotification(stringProvider.recoverRequestSent(), isError = false)
                        socketHandler.resumeRefreshes()
                    }

                    // ACCEPTED / DECLINED: owned by invalidation refresh on processingSecretName,
                    // avoids double-dispatching showRecoveredSecret()/the declined path from here too.
                    else -> {
                        logger.log(
                            LogTag.ShowSecretVM.Message.AwaitingPollerResolution,
                            "clientStatus=${existingClaim?.clientStatus}",
                            success = true
                        )
                        socketHandler.resumeRefreshes()
                    }
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.RecoverFailed, "${t.message}", success = false)
                _isLoading.value = false
                socketHandler.resumeRefreshes()
            }
        }
    }

    private suspend fun recoverSecret(secretName: String) {
        withContext(Dispatchers.IO) {
            metaSecretAppManager.recover(secretModel = SecretModel(secretName, null))
        }
        showNotification(stringProvider.recoverRequestSent(), isError = false)
        socketHandler.resumeRefreshes()
    }

    private fun showRecoveredSecret(secretId: String) {
        _isLoading.value = true
        logger.log(LogTag.ShowSecretVM.Message.StartShowingRecovered, success = true)
        viewModelScope.launch {
            try {
                val recoveredSecretValue = withContext(Dispatchers.IO) {
                    metaSecretAppManager.showRecovered(SecretModel(secretId, null))
                }
                if (!recoveredSecretValue.isNullOrBlank()) {
                    val parsed = parseSecretValue(recoveredSecretValue)
                    _revealedSecret.value = when (parsed.type) {
                        SecretValueType.PASSWORD -> RevealedSecretContent.Password(recoveredSecretValue)
                        SecretValueType.SEED_PHRASE -> RevealedSecretContent.SeedPhrase(
                            words = parsed.words,
                            count = parsed.count ?: parsed.words.size
                        )
                    }
                    userRequestedRecovery = false
                    logger.log(LogTag.ShowSecretVM.Message.RecoveredSecretLoaded, success = true)
                } else {
                    logger.log(LogTag.ShowSecretVM.Message.FailedToRecoverSecret, success = false)
                }
            } catch (t: Throwable) {
                logger.log(LogTag.ShowSecretVM.Message.ShowRecoveredFailed, "${t.message}", success = false)
            } finally {
                _isLoading.value = false
                socketHandler.actionsToFollow(
                    add = null,
                    exclude = listOf(SocketRequestModel.SHOW_SECRET)
                )
            }
        }
    }
}

sealed class ShowSecretEvents : CommonViewModelEventsInterface {
    data class ShowSecret(val secretName: String) : ShowSecretEvents()
    data class SecretReadyToShow(val secretId: String) : ShowSecretEvents()
    data object HideSecret : ShowSecretEvents()
}

enum class SecretValueType {
    PASSWORD,
    SEED_PHRASE,
}

data class ParsedSecretValue(
    val type: SecretValueType,
    val words: List<String> = emptyList(),
    val count: Int? = null,
)

sealed class RevealedSecretContent {
    data class Password(val value: String) : RevealedSecretContent()
    data class SeedPhrase(val words: List<String>, val count: Int) : RevealedSecretContent()
}

fun parseSecretValue(value: String): ParsedSecretValue {
    val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size == 12 || words.size == 24) {
        ParsedSecretValue(
            type = SecretValueType.SEED_PHRASE,
            words = words,
            count = words.size,
        )
    } else {
        ParsedSecretValue(type = SecretValueType.PASSWORD)
    }
}
