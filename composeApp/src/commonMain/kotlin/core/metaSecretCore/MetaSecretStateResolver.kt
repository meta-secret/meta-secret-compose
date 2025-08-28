package core.metaSecretCore

import models.appInternalModels.AppErrors

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface
) : MetaSecretStateResolverInterface {

    override fun startFirstSignUp(
        vaultName: String
    ): AppStateResult {
        println("\uD83D\uDDFD State Resolver: first sign up")

        val localStateResult = LocalState(vaultName, metaSecretCore).new()
            ?: return AppStateResult(null, AppErrors.CreateLocalError)

        println("\uD83D\uDDFD State Resolver: Local State")

        val userCredsResult = localStateResult.generateNewCreds()
            ?: return AppStateResult(null, AppErrors.CredsGenerationError)

        println("\uD83D\uDDFD State Resolver: Vault state")

        val memberResult = userCredsResult.signUp()
            ?:  return AppStateResult(null, AppErrors.SignUpError)

        println("\uD83D\uDDFD State Resolver: Memner State")

        return AppStateResult(memberResult, null)
    }

}
