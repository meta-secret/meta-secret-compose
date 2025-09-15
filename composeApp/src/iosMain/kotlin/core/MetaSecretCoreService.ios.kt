package core

import kotlinx.cinterop.ExperimentalForeignApi
import core.metaSecretCore.MetaSecretCoreInterface
import com.metaSecret.ios.SwiftBridge
import models.apiModels.UserData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive

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
            // Trigger backup after DB modification
            swiftBridge.backupIfChanged()
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
            swiftBridge.backupIfChanged()
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
            val jsonObject = buildJsonObject {
                put("vaultName", JsonPrimitive(candidate.vaultName))
                putJsonObject("device") {
                    put("deviceId", JsonPrimitive(candidate.device.deviceId))
                    put("deviceName", JsonPrimitive(candidate.device.deviceName))
                    putJsonObject("keys") {
                        put("dsaPk", JsonPrimitive(candidate.device.keys.dsaPk))
                        put("transportPk", JsonPrimitive(candidate.device.keys.transportPk))
                    }
                }
            }
            val userDataJson = jsonObject.toString()
            println("\uF8FF ✅ iOS: Formatted userData Json: $userDataJson")

            if (actionUpdate.isBlank()) {
                throw IllegalArgumentException("actionUpdate cannot be blank")
            }
            val jsonActionUpdate = "\"" + actionUpdate.lowercase() + "\""
            println("\uF8FF ✅ iOS: Formatted actionUpdate: $jsonActionUpdate")

            val result = swiftBridge.updateMembership(userDataJson, jsonActionUpdate)
            println("\uF8FF ✅ iOS: updateMembership result: $result")
            swiftBridge.backupIfChanged()
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: updateMembership error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun splitSecret(secretId: String, secret: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling splitSecret with: $secretId")
            val result = swiftBridge.splitSecret(secretId, secret)
            println("\uF8FF ✅ iOS: AppManager: splitSecret result $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager splitSecret error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    override fun findClaim(secretId: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling findClaim with: $secretId")
            val result = swiftBridge.findClaim(secretId)
            println("\uF8FF ✅ iOS: AppManager: findClaim result $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager findClaim error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun recover(secretId: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling recover with: $secretId")
            val result = swiftBridge.recover(secretId)
            println("\uF8FF ✅ iOS: AppManager: recover result $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager recover error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun acceptRecover(claimId: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling acceptRecover with: $claimId")
            val result = swiftBridge.acceptRecover(claimId)
            println("\uF8FF ✅ iOS: AppManager: acceptRecover result $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager acceptRecover error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun showRecovered(secretId: String): String {
        try {
            println("\uF8FF ✅ iOS: Calling showRecovered with: $secretId")
            val result = swiftBridge.showRecovered(secretId)
            println("\uF8FF ✅ iOS: AppManager: showRecovered result $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ iOS: AppManager showRecovered error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}