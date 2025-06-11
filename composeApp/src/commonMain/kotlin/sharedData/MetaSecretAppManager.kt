package sharedData

class MetaSecretAppManager(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
) {

    suspend fun initWithSavedKey(): Boolean {
        val masterKey = keyChainInterface.getString("master_key")
        return if (!masterKey.isNullOrEmpty()) {
            metaSecretCoreInterface.initAppManager(masterKey)
            println("✅ AppManager is initiated")
            true
        } else {
            println("⛔ AppManager init error")
            keyChainInterface.clearAll()
            false
        }
    }

    fun getState(): String {
        return metaSecretCoreInterface.getAppState()
    }

}