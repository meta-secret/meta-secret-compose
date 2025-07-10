package sharedData

import kotlinx.cinterop.ExperimentalForeignApi
import sharedData.metaSecretCore.MetaSecretCoreInterface
import com.metaSecret.ios.SwiftBridge
import models.apiModels.UserData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalForeignApi::class)
class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    private val swiftBridge = SwiftBridge()

    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            println("\uF8FF ✅ iOS: Calling generateMasterKey")
            val masterKey = swiftBridge.generateMasterKey()
            println("\uF8FF ✅ iOS: Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: Master key generation error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun initAppManager(masterKey: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling initWithMasterKey with: $masterKey")
            val result = swiftBridge.initWithMasterKey(masterKey)
            println("\uF8FF ✅ iOS: AppManager: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getAppState(): String {
        try {
            println("\uF8FF ✅ iOS: Calling getState")
            val result = swiftBridge.getState()
            println("\uF8FF ✅ iOS: App State: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun generateUserCreds(vaultName: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling generateUserCreds")
            val result = swiftBridge.generateUserCredsWithVaultName(vaultName)
            println("\uF8FF ✅ iOS: App generateUserCreds: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager generateUserCreds error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun signUp(): String {
        try {
            println("\uF8FF ✅ iOS: Calling signUp")
            val result = swiftBridge.signUp()
            println("\uF8FF ✅ iOS: SignUp result: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: SignUp error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun updateMembership(candidate: UserData, actionUpdate: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling updateMembership")
            val userDataJson = Json.encodeToString(candidate)
            val result = swiftBridge.updateMembership(userDataJson, actionUpdate)
            println("\uF8FF ✅ iOS: SignUp result: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: SignUp error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}