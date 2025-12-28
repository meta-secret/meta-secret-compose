package core.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.AppErrors

data class AppStateResult (
    val appState: AppState?,
    val error: AppErrors?
)

interface MetaSecretStateResolverInterface {
    fun startFirstSignUp(
        vaultName: String
    ): AppStateResult
}

interface AppState

open class LocalState(
    private val vaultName: String,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface
) : AppState {
    fun new(): LocalState? {
        logger.log(core.LogTag.StateResolver.Message.StartGetAppState, success = true)
        val jsonResult = metaSecretCore.getAppState()
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getAppState()

        val result: LocalState? = if (isSuccess && stateModel is State.Local) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsLocal, success = true)
            LocalState(vaultName, metaSecretCore, logger)
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithLocalState, success = false)
            null
        }

        return result
    }

    fun generateNewCreds(): VaultState? {
        logger.log(core.LogTag.StateResolver.Message.StartGenerateNewCreds, success = true)
        val jsonResult = metaSecretCore.generateUserCreds(vaultName)
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getAppState()
        val vaultInfo = coreStateModel.getVaultFullInfo()

        val result: VaultState? = if (isSuccess && stateModel is State.Vault) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsVault, success = true)
            VaultState(metaSecretCore, logger)
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsOutsider, success = true)
            when (vaultInfo.outsider.status) {
                UserDataOutsiderStatus.NON_MEMBER -> {
                    logger.log(core.LogTag.StateResolver.Message.CurrentStateIsNonMember, success = true)
                    VaultState(metaSecretCore, logger)
                }
                UserDataOutsiderStatus.PENDING -> {
                    logger.log(core.LogTag.StateResolver.Message.CurrentStateIsPending, success = true)
                    // TODO: #47 Show alert that tells user to accept the request
                    null
                }
                UserDataOutsiderStatus.DECLINED -> {
                    logger.log(core.LogTag.StateResolver.Message.CurrentStateIsDeclined, success = true)
                    //  TODO: #47 Show alert that request has been declined
                    null
                }
            }
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithVaultState, success = false)
            null
        }

        return result
    }
}

class VaultState(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface
) : AppState {
    fun signUp(): AppState? {
        logger.log(core.LogTag.StateResolver.Message.StartSignUp, success = true)
        val jsonResult = metaSecretCore.signUp()
        val coreStateModel = AppStateModel.fromJson(jsonResult, logger)

        val isSuccess = coreStateModel.success
        val vaultInfo = coreStateModel.getVaultFullInfo()

        val result: AppState? = if (isSuccess && vaultInfo is VaultFullInfo.Member) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsMember, success = true)
            MemberState()
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsOutsider, success = true)
            OutsiderState(coreStateModel)
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithMemberState, success = false)
            null
        }

        return result
    }
}

class  MemberState : AppState

class  OutsiderState(val coreStateModel: AppStateModel) : AppState
