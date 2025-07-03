package sharedData.metaSecretCore

interface MetaSecretCoreInterface {
    fun generateMasterKey(): String
    fun initAppManager(masterKey: String): String
    fun getAppState(): String
    fun generateUserCreds(vaultName: String): String
    fun signUp(): String
    fun acceptJoinRequest(): String
    fun declineJoinRequest(): String
}