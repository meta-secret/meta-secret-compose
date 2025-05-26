package sharedData

import com.metasecret.core.MetaSecretNative

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {

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
            val masterKey = MetaSecretNative.generateMasterKey()
            println("Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("Master key generation error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun initAppManager(masterKey: String): String {
        try {
            val initResult = MetaSecretNative.initAppManager(masterKey)
            println("Initial result is: $initResult")
            return initResult
        } catch (e: Exception) {
            println("Initial result error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun getAppState(): String {
        TODO("Not yet implemented")
    }

    override fun signUp(name: String) {
        TODO("Not yet implemented")
    }
}