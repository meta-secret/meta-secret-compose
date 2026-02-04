package core.metaSecretCore

import models.appInternalModels.AppErrors
import core.DebugLoggerInterface
import core.LogTag

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val logger: DebugLoggerInterface
) : MetaSecretStateResolverInterface {

    override suspend fun startFirstSignUp(
        vaultName: String
    ): AppStateResult {
        logger.log(LogTag.StateResolver.Message.FirstSignUp, success = true)

        val localStateResult = LocalState(vaultName, metaSecretCore, logger).new()
            ?: return AppStateResult(null, AppErrors.CreateLocalError)

        logger.log(LogTag.StateResolver.Message.LocalState, success = true)

        val userCredsResult = localStateResult.generateNewCreds()
            ?: return AppStateResult(null, AppErrors.CredsGenerationError)

        logger.log(LogTag.StateResolver.Message.VaultState, success = true)

        val memberResult = userCredsResult.signUp()
            ?:  return AppStateResult(null, AppErrors.SignUpError)

        logger.log(LogTag.StateResolver.Message.MemberState, success = true)

        return AppStateResult(memberResult, null)
    }

}
