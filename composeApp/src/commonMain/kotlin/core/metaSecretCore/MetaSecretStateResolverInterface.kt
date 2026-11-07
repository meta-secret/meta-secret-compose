package core.metaSecretCore

import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.AppErrors
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeReadCall
import core.metaSecretCore.liveboard.NativeOperationPort
import core.metaSecretCore.liveboard.NativeReadScope

data class AppStateResult (
    val appState: AppState?,
    val error: AppErrors?,
    val errorMessage: String? = null
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
    /** Rebuild only the client wrapper after canonical non-member state was restored. */
    fun restorePreparedSignUp(): Boolean = false
    fun clearPreparedSignUp()
}

interface AppState

open class LocalState(
    private val vaultName: String,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface,
    private val readOwner: NativeCanonicalReadOwner,
    private val operations: NativeOperationPort,
) : AppState {
    private var origin: NativeReadScope? = null
    suspend fun new(): LocalState? {
        val expected = operations.capture() ?: return null
        logger.log(core.LogTag.StateResolver.Message.StartGetAppState, success = true)
        val call = readOwner.readAttempt(expected)
        if (!operations.isCurrent(expected) || !readOwner.mayConsumeActualRead(call) || call.frame.scope != expected
            ) return null
        val coreStateModel = when (call) {
            is NativeReadCall.Value -> call.frame.state ?: return null
            is NativeReadCall.Failure -> throw call.cause
        }

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getCurrentAppState()
        logger.setVaultState(stateModel?.description())
        if (!operations.isCurrent(expected)) return null

        val result: LocalState? = if (isSuccess && stateModel is State.Local) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsLocal, success = true)
            LocalState(vaultName, metaSecretCore, logger, readOwner, operations).also { it.origin = expected }
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithLocalState, success = false)
            null
        }

        return result.takeIf { operations.isCurrent(expected) }
    }

    suspend fun generateNewCreds(): PrepareSignUpResult {
        logger.log(core.LogTag.StateResolver.Message.StartGenerateNewCreds, success = true)
        val expected = origin ?: return PrepareSignUpResult(null, AppErrors.CredsGenerationError)
        val coreStateModel = operations.execute(expected, { metaSecretCore.generateUserCreds(vaultName) }) { _, json ->
            AppStateModel.fromJson(json, logger, null)
        } ?: return PrepareSignUpResult(null, AppErrors.CredsGenerationError)
        if (!operations.isCurrent(expected)) return PrepareSignUpResult(null, AppErrors.CredsGenerationError)

        val isSuccess = coreStateModel.success
        val stateModel = coreStateModel.getCurrentAppState()
        val vaultInfo = coreStateModel.getVaultFullInfo()
        logger.setVaultState(stateModel?.description())
        if (!operations.isCurrent(expected)) return PrepareSignUpResult(null, AppErrors.CredsGenerationError)

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

        return if (operations.isCurrent(expected)) result else PrepareSignUpResult(null, AppErrors.CredsGenerationError)
    }
}

class VaultState(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: core.DebugLoggerInterface,
    private val operations: NativeOperationPort,
    private val expected: NativeReadScope,
) : AppState {
    fun isCurrent(): Boolean = operations.isCurrent(expected)

    suspend fun signUp(): SignUpStateResult {
        logger.log(core.LogTag.StateResolver.Message.StartSignUp, success = true)
        val coreStateModel = operations.execute(expected, { metaSecretCore.signUp() }) { _, json ->
            AppStateModel.fromJson(json, logger, null)
        } ?: return SignUpStateResult(null, null)
        if (!operations.isCurrent(expected)) return SignUpStateResult(null, null)

        val isSuccess = coreStateModel.success
        val vaultInfo = coreStateModel.getVaultFullInfo()
        val stateModel = coreStateModel.getCurrentAppState()
        logger.setVaultState(stateModel?.description())
        if (!isCurrent()) return SignUpStateResult(null, null)

        val result: SignUpStateResult = if (isSuccess && vaultInfo is VaultFullInfo.Member) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsMember, success = true)
            SignUpStateResult(MemberState(), null)
        } else if (isSuccess && vaultInfo is VaultFullInfo.Outsider) {
            logger.log(core.LogTag.StateResolver.Message.CurrentStateIsOutsider, success = true)
            SignUpStateResult(OutsiderState(), null)
        } else {
            logger.log(core.LogTag.StateResolver.Message.SwwWithMemberState, success = false)
            SignUpStateResult(null, coreStateModel.error)
        }

        return if (operations.isCurrent(expected)) result else SignUpStateResult(null, null)
    }
}

data class SignUpStateResult(
    val appState: AppState?,
    val errorMessage: String?
)

class  MemberState : AppState

class  OutsiderState : AppState
