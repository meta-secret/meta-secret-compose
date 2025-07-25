package sharedData

import com.metasecret.core.MetaSecretNative
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.UserData
import org.koin.java.KoinJavaComponent.inject
import sharedData.metaSecretCore.MetaSecretCoreInterface
import java.io.File

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {

    private val context: Context by inject(Context::class.java)
    
    companion object {
        init {
            try {
                System.loadLibrary("metasecret_mobile")
                println("Metasecret_mobile library has been loaded successfully")
            } catch (e: Exception) {
                println("Error during loading of the Metasecret_mobile library: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun generateMasterKey(): String {
        try {
            println("✅ Calling Android generateMasterKey")
            val masterKey = MetaSecretNative.generateMasterKey()
            println("✅ Android Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("Android Master key generation error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun initAppManager(masterKey: String): String {
        try {
            println("✅ Calling Android initAppManager with: $masterKey")
            val result = MetaSecretNative.initAppManager(masterKey)
            println("✅ AppManager Android: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager Android initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun getAppState(): String {
        try {
            println("✅ Calling Android getState")
            val result = MetaSecretNative.getAppState()
            println("✅ App Android State: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager Android initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun generateUserCreds(vaultName: String): String {
        try {
            println("✅ Calling Android generateUserCreds")
            val result = MetaSecretNative.generateUserCreds(vaultName)
            println("✅ App Android generateUserCreds: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager Android generateUserCreds error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun signUp(): String {
        try {
            println("✅ Calling Android signUp")
            val result = MetaSecretNative.signUp()
            println("✅ App Android signUp State: $result")
            return result
        } catch (e: Exception) {
            println("⛔ AppManager Android initialization error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun updateMembership(candidate: UserData, actionUpdate: String): String {
        try {
            println("\uF8FF ✅ Android: Calling updateMembership")

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
            println("\uF8FF ✅ Android: Formatted userData Json: $userDataJson")

            if (actionUpdate.isBlank()) {
                throw IllegalArgumentException("actionUpdate cannot be blank")
            }
            val jsonActionUpdate = "\"" + actionUpdate.lowercase() + "\""
            println("\uF8FF ✅ Android: Formatted actionUpdate: $jsonActionUpdate")
            
            val result = MetaSecretNative.updateMembership(userDataJson, jsonActionUpdate)
            println("\uF8FF ✅ Android: updateMembership result: $result")
            return result
        } catch (e: Exception) {
            println("\uF8FF ⛔ Android: updateMembership error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun cleanDB() {
        println("CLEAN DB (Android)")
        try {
            val dbFile = File(context.getDatabasePath("meta-secret.db").path)
            if (dbFile.exists()) {
                val deleted = dbFile.delete()
                MetaSecretNative.cleanUpDatabase()
                println("✅ DB file deleted: $deleted")
            } else {
                println("✅ DB file does not exist")
            }
        } catch (e: Exception) {
            println("⛔ Error cleaning DB: ${e.message}")
            e.printStackTrace()
        }
    }
}