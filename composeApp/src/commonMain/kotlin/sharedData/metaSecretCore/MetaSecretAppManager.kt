package sharedData.metaSecretCore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.MetaSecretCoreStateModel
import sharedData.KeyChainInterface

sealed class InitResult {
    object Loading : InitResult()
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
) {

    suspend fun initWithSavedKey(): InitResult {
        val masterKey = keyChainInterface.getString("master_key")
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = withContext(Dispatchers.IO) {
                    metaSecretCoreInterface.initAppManager(masterKey)
                }
                println("✅AppManager is initiated: $appManagerResult")
                InitResult.Success(appManagerResult)
            } catch (e: Exception) {
                println("⛔ AppManager init error: ${e.message}")
                InitResult.Error(e.message ?: "Unknown error")
            }
        } else {
            println("⛔ AppManager init error: No master key found")
            keyChainInterface.clearAll()
            InitResult.Error("No master key found")
        }
    }

    fun getState(): MetaSecretCoreStateModel {
        val stateJson = metaSecretCoreInterface.getAppState()
        return MetaSecretCoreStateModel.fromJson(stateJson)
    }

}