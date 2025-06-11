package sharedData

class MetaSecretAppManager(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
) {

    suspend fun initWithSavedKey(): Boolean {
        val masterKey = keyChainInterface.getString("master_key")
        return if (!masterKey.isNullOrEmpty()) {
            println("✅ AppManager is initiated")
            metaSecretCoreInterface.initAppManager(masterKey)
            true
        } else {
            println("⛔ AppManager init error")
            false
        }
    }

    fun getState(): String {
        return metaSecretCoreInterface.getAppState()
    }

}