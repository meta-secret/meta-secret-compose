package core.metaSecretCore

import models.appInternalModels.AppErrors
import core.DebugLoggerInterface
import core.LogTag

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: DebugLoggerInterface
) : MetaSecretStateResolverInterface {
    private var preparedVaultState: VaultState? = null

    override suspend fun prepareSignUp(
        vaultName: String
    ): PrepareSignUpResult {
        logger.log(LogTag.StateResolver.Message.FirstSignUp, success = true)

        val localStateResult = LocalState(vaultName, metaSecretCore, logger).new()
            ?: return PrepareSignUpResult(null, AppErrors.CreateLocalError)

        logger.log(LogTag.StateResolver.Message.LocalState, success = true)

        val userCredsResult = localStateResult.generateNewCreds()
        if (userCredsResult.error != null || userCredsResult.availability == null) {
            return PrepareSignUpResult(null, userCredsResult.error ?: AppErrors.CredsGenerationError)
        }
        preparedVaultState = VaultState(metaSecretCore, logger)

        logger.log(LogTag.StateResolver.Message.VaultState, success = true)
        return userCredsResult
    }

    override suspend fun continueSignUp(): AppStateResult {
        val vaultState = preparedVaultState ?: return AppStateResult(null, AppErrors.SignUpError)
        val memberResult = vaultState.signUp()
        if (memberResult.appState == null) {
            return AppStateResult(null, AppErrors.SignUpError, memberResult.errorMessage)
        }

        if (memberResult.appState is MemberState) {
            preparedVaultState = null
        }

        logger.log(LogTag.StateResolver.Message.MemberState, success = true)

        return AppStateResult(memberResult.appState, null)
    }

    override fun clearPreparedSignUp() {
        preparedVaultState = null
    }
}
