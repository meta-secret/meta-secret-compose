package sharedData

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

// TODO: Do not use init SwiftBridge() every time. Need to store only one example of this type

class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            println("✅ Calling generateMasterKey")
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
            println("✅ Calling iOS initWithMasterKey with: $masterKey")
            val result = SwiftBridge().initWithMasterKey(masterKey)
            println("✅ AppManager iOS: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager iOS initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getAppState(): String {
        try {
            println("✅ Calling iOS getState")
            val result = SwiftBridge().getState()
            println("✅ App iOS State: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager iOS initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun signUp(name: String) {
        TODO("Not yet implemented")
    }

}