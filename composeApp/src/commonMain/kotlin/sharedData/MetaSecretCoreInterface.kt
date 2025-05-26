package sharedData

interface MetaSecretCoreInterface {
    fun generateMasterKey(): String
    fun initAppManager(masterKey: String): String
    fun getAppState(): String
    fun signUp(name: String)
}