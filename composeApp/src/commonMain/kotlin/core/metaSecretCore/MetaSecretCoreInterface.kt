package core.metaSecretCore

import models.apiModels.UserData

interface MetaSecretCoreInterface {
    fun generateMasterKey(): String
    fun initAppManager(masterKey: String): String
    fun getAppState(): String
    fun generateUserCreds(vaultName: String): String
    fun signUp(): String
    fun updateMembership(candidate: UserData, actionUpdate: String): String
    fun splitSecret(secretId: String, secret: String): String
    fun findClaim(secretId: String): String
    fun recover(secretId: String): String
    fun acceptRecover(claimId: String): String
    fun showRecovered(secretId: String): String
}