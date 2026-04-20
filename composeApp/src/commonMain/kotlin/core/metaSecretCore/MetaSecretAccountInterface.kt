package core.metaSecretCore

import models.apiModels.UserData

interface MetaSecretAccountInterface {
    fun generateMasterKey(): String
    fun initAppManager(masterKey: String): String
    fun getAppState(): String
    fun generateUserCreds(vaultName: String): String
    fun signUp(): String
    fun updateMembership(candidate: UserData, actionUpdate: String): String
}
