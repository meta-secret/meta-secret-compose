package core.metaSecretCore

import models.appInternalModels.AppErrors
import core.DebugLoggerInterface
import core.LogTag
import core.metaSecretCore.liveboard.NativeOperationPort
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: DebugLoggerInterface,
    private val readOwner: NativeCanonicalReadOwner,
    private val operations: NativeOperationPort,
) : MetaSecretStateResolverInterface {
    private var preparedVaultState: VaultState? = null

    override suspend fun prepareSignUp(
        vaultName: String
    ): PrepareSignUpResult {
        val expected = operations.capture() ?: return PrepareSignUpResult(null, AppErrors.CreateLocalError)
        logger.log(LogTag.StateResolver.Message.FirstSignUp, success = true)

        val localStateResult = LocalState(vaultName, metaSecretCore, logger, readOwner, operations).new()
            ?: return PrepareSignUpResult(null, AppErrors.CreateLocalError)

        if (!operations.isCurrent(expected)) return PrepareSignUpResult(null, AppErrors.CreateLocalError)
        logger.log(LogTag.StateResolver.Message.LocalState, success = true)

        val userCredsResult = localStateResult.generateNewCreds()
        if (userCredsResult.error != null || userCredsResult.availability == null) {
            return PrepareSignUpResult(null, userCredsResult.error ?: AppErrors.CredsGenerationError)
        }
        if (!operations.isCurrent(expected)) return PrepareSignUpResult(null, AppErrors.CredsGenerationError)
        preparedVaultState = VaultState(metaSecretCore, logger, operations, expected)

        logger.log(LogTag.StateResolver.Message.VaultState, success = true)
        return if (operations.isCurrent(expected)) userCredsResult else PrepareSignUpResult(null, AppErrors
            .CredsGenerationError)
    }

    override suspend fun continueSignUp(): AppStateResult {
        val vaultState = preparedVaultState ?: return AppStateResult(null, AppErrors.SignUpError)
        if (!vaultState.isCurrent()) return AppStateResult(null, AppErrors.SignUpError)
        val memberResult = vaultState.signUp()
        if (!vaultState.isCurrent() || preparedVaultState !== vaultState) return AppStateResult(null, AppErrors
            .SignUpError)
        if (memberResult.appState == null) {
            return AppStateResult(null, AppErrors.SignUpError, memberResult.errorMessage)
        }

        if (memberResult.appState is MemberState) {
            preparedVaultState = null
        }

        logger.log(LogTag.StateResolver.Message.MemberState, success = true)

        return if (vaultState.isCurrent()) AppStateResult(memberResult.appState, null)
        else AppStateResult(null, AppErrors.SignUpError)
    }

    override fun restorePreparedSignUp(): Boolean {
        val expected = operations.capture() ?: return false
        preparedVaultState = VaultState(metaSecretCore, logger, operations, expected)
        return true
    }

    override fun clearPreparedSignUp() {
        preparedVaultState = null
    }
}
