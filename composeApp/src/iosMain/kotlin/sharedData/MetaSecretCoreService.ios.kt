package sharedData

import com.metaSecret.ios.MetaSecretCoreBridge
import kotlinx.cinterop.ExperimentalForeignApi

class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            val masterKey = MetaSecretCoreBridge().generateMasterKey()
            println("Master key: $masterKey")
            return masterKey
        } catch (e: Exception) {
            println("Master key generation error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun initAppManager(masterKey: String): String {
        TODO("Not yet implemented")
    }

    override fun getAppState(): String {
        TODO("Not yet implemented")
    }

    override fun signUp(name: String) {
        TODO("Not yet implemented")
    }

}