package core.metaSecretCore

import models.appInternalModels.AppErrors

class MetaSecretStateResolver(
    private val metaSecretCore: MetaSecretCoreInterface
) : MetaSecretStateResolverInterface {

    override fun startFirstSignUp(
        vaultName: String
    ): AppStateResult {
        println("✅" + core.LogTags.STATE_RESOLVER + ": first sign up")

        val localStateResult = LocalState(vaultName, metaSecretCore).new()
            ?: return AppStateResult(null, AppErrors.CreateLocalError)

        println("✅" + core.LogTags.STATE_RESOLVER + ": Local State")

        val userCredsResult = localStateResult.generateNewCreds()
            ?: return AppStateResult(null, AppErrors.CredsGenerationError)

        println("✅" + core.LogTags.STATE_RESOLVER + ": Vault state")

        val memberResult = userCredsResult.signUp()
            ?:  return AppStateResult(null, AppErrors.SignUpError)

        println("✅" + core.LogTags.STATE_RESOLVER + ": Member State")

        return AppStateResult(memberResult, null)
    }

}
