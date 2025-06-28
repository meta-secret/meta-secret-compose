package sharedData

import kotlinx.cinterop.ExperimentalForeignApi
import sharedData.metaSecretCore.MetaSecretCoreInterface
import com.metaSecret.ios.SwiftBridge

@OptIn(ExperimentalForeignApi::class)
class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    private val swiftBridge = SwiftBridge()

    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            println("✅ Calling iOS generateMasterKey")
            val masterKey = swiftBridge.generateMasterKey()
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
            val result = swiftBridge.initWithMasterKey(masterKey)
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
            val result = swiftBridge.getState()
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
            val result = swiftBridge.generateUserCreds()
            println("✅ App iOS generateUserCreds: $result")
            return result
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
            val result = swiftBridge.signUp()
            println("✅ SignUp result: $result")
            return result
        } catch (e: Exception) {
            println("⛔ SignUp error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}