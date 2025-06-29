package sharedData

import com.metasecret.core.MetaSecretNative
import android.content.Context
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
    
    private fun cleanDB() {
        println("CLEAN DB (Android)")
        try {
            val dbFile = File(context.getDatabasePath("meta-secret.db").path)
            if (dbFile.exists()) {
                val deleted = dbFile.delete()
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