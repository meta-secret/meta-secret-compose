package core.metaSecretCore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.AppErrors

data class AppStateResult (
    val appState: AppState?,
    val error: AppErrors?
)

enum class VaultAvailability {
    AVAILABLE,
    EXISTS,
}

data class PrepareSignUpResult(
    val availability: VaultAvailability?,
    val error: AppErrors?
)

internal fun mapVaultAvailability(vaultInfo: VaultFullInfo?): VaultAvailability? {
    return when (vaultInfo) {
        is VaultFullInfo.NotExists -> VaultAvailability.AVAILABLE
        is VaultFullInfo.Outsider -> VaultAvailability.EXISTS
        is VaultFullInfo.Member -> VaultAvailability.AVAILABLE
        null -> null
    }
}

interface MetaSecretStateResolverInterface {
    suspend fun prepareSignUp(
        vaultName: String
    ): PrepareSignUpResult
    suspend fun continueSignUp(): AppStateResult
    fun clearPreparedSignUp()
}

interface AppState

open class LocalState(
    private val vaultName: String,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface
) : AppState {
    suspend fun new(): LocalState? {
        logger.log(core.LogTag.StateResolver.Message.StartGetAppState, success = true)
        val jsonResult = withContext(Dispatchers.IO) {
            metaSecretCore.getAppState() // Uses only once
        }
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger, null)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getCurrentAppState()
        logger.setVaultState(stateModel?.description())

        val result: LocalState? = if (isSuccess && stateModel is State.Local) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsLocal, success = true)
            LocalState(vaultName, metaSecretCore, logger)
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithLocalState, success = false)
            null
        }

        return result
    }

    suspend fun generateNewCreds(): PrepareSignUpResult {
        logger.log(core.LogTag.StateResolver.Message.StartGenerateNewCreds, success = true)
        val jsonResult = withContext(Dispatchers.IO) {
            metaSecretCore.generateUserCreds(vaultName)
        }
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger, null)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getCurrentAppState()
        val vaultInfo = coreStateModel.getVaultFullInfo()
        logger.setVaultState(stateModel?.description())

        val result: PrepareSignUpResult = if (isSuccess && stateModel is State.Vault) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsVault, success = true)
            when (mapVaultAvailability(vaultInfo)) {
                VaultAvailability.AVAILABLE -> {
                    PrepareSignUpResult(VaultAvailability.AVAILABLE, null)
                }
                VaultAvailability.EXISTS -> {
                    logger.log(core.LogTag.StateResolver.Message.CurrentStateIsOutsider, success = true)
                    when ((vaultInfo as? VaultFullInfo.Outsider)?.outsider?.status) {
                        UserDataOutsiderStatus.NON_MEMBER -> {
                            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsNonMember, success = true)
                            PrepareSignUpResult(VaultAvailability.EXISTS, null)
                        }
                        UserDataOutsiderStatus.PENDING -> {
                            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsPending, success = true)
                            PrepareSignUpResult(VaultAvailability.EXISTS, null)
                        }
                        UserDataOutsiderStatus.DECLINED -> {
                            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsDeclined, success = true)
                            PrepareSignUpResult(VaultAvailability.EXISTS, null)
                        }
                        null -> PrepareSignUpResult(null, AppErrors.CredsGenerationError)
                    }
                }
                null -> PrepareSignUpResult(null, AppErrors.CredsGenerationError)
            }
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithVaultState, success = false)
            PrepareSignUpResult(null, AppErrors.CredsGenerationError)
        }

        return result
    }
}

class VaultState(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface
) : AppState {
    suspend fun signUp(): AppState? {
        logger.log(core.LogTag.StateResolver.Message.StartSignUp, success = true)
        val jsonResult = withContext(Dispatchers.IO) {
            metaSecretCore.signUp()
        }
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger, null)

        val isSuccess = coreStateModel.success
        val vaultInfo = coreStateModel.getVaultFullInfo()
        val stateModel = coreStateModel.getCurrentAppState()
        logger.setVaultState(stateModel?.description())

        val result: AppState? = if (isSuccess && vaultInfo is VaultFullInfo.Member) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsMember, success = true)
            MemberState()
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsOutsider, success = true)
            OutsiderState()
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithMemberState, success = false)
            null
        }

        return result
    }
}

class  MemberState : AppState

class  OutsiderState : AppState
