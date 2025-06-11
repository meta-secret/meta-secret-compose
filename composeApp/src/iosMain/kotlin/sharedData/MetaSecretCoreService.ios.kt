package sharedData

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

// TODO: Do not use init SwiftBridge() every time. Need to store only one example of this type

class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            val masterKey = SwiftBridge().generateMasterKey()
            println("✅ Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("⛔ Master key generation error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun initAppManager(masterKey: String): String {
        try {
            val result = SwiftBridge().initWithMasterKey(masterKey)
            println("✅ Appmanager: $result")
            return "$result"
        } catch (e: Exception) {
            println("⛔ Appmanager initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getAppState(): String {
        try {
//            val result = SwiftBridge().initWithMasterKey(masterKey)
//            println("✅ Appmanager: $result")
            return "result"
        } catch (e: Exception) {
            println("⛔ Appmanager initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun signUp(name: String) {
        TODO("Not yet implemented")
    }

}