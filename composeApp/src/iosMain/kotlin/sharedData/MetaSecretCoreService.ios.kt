package sharedData

import kotlinx.cinterop.ExperimentalForeignApi
import sharedData.metaSecretCore.MetaSecretCoreInterface
import com.metaSecret.ios.SwiftBridge

// TODO: Do not use init SwiftBridge() every time. Need to store only one example of this type

class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            println("✅ Calling iOS generateMasterKey")
            val masterKey = SwiftBridge().generateMasterKey()
            println("✅ iOS Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("⛔ iOS Master key generation error: ${e.message}")
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

    @OptIn(ExperimentalForeignApi::class)
    override fun generateUserCreds(vaultName: String): String {
        try {
            println("✅ Calling iOS generateUserCreds")
            val result = SwiftBridge().generateUserCreds()
            println("✅ App iOS generateUserCreds: result")
            return "result"
        } catch (e: Exception) {
            println("⛔ AppManager iOS generateUserCreds error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun signUp(): String {
        try {
            println("✅ Calling signUp")
            val result = SwiftBridge().signUp()
            println("✅ SignUp result: $result")
            return result
        } catch (e: Exception) {
            println("⛔ SignUp error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}