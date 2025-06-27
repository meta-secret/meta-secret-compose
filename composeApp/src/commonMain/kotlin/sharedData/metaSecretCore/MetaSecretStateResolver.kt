package sharedData.metaSecretCore

import models.appInternalModels.AppErrors

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface
) : MetaSecretStateResolverInterface {

    override fun startFirstSignUp(
        vaultName: String
    ): AppStateResult {
        val localStateResult = LocalState(vaultName, metaSecretCore).new()
            ?: return AppStateResult(null, AppErrors.CreateLocalError)

        val userCredsResult = localStateResult.generateNewCreds()
            ?: return AppStateResult(null, AppErrors.CredsGenerationError)

        val memberResult = userCredsResult.signUp()
            ?:  return AppStateResult(null, AppErrors.SignUpError)

        return AppStateResult(memberResult, null)
    }

}
