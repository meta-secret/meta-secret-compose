package sharedData.metaSecretCore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.StateType
import sharedData.KeyChainInterface

sealed class InitResult {
    object Loading : InitResult()
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
) {

    suspend fun initWithSavedKey(): InitResult {
        val masterKey = keyChainInterface.getString("master_key")
        println("\uD83D\uDEE0\uFE0F AppManager: is Master key exist: ${masterKey != null}")
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = withContext(Dispatchers.IO) {
                    metaSecretCore.initAppManager(masterKey)
                }
                println("\uD83D\uDEE0\uFE0F ✅AppManager: is initiated: $appManagerResult")
                InitResult.Success(appManagerResult)
            } catch (e: Exception) {
                println("\uD83D\uDEE0\uFE0F ⛔ AppManager: init error: ${e.message}")
                InitResult.Error(e.message ?: "Unknown error")
            }
        } else {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: init error: No master key found")
            keyChainInterface.clearAll()
            InitResult.Error("No master key found")
        }
    }

    fun getState(): StateType? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = MetaSecretCoreStateModel.fromJson(stateJson).getState()
            println("\uD83D\uDEE0\uFE0F AppManager: currentState is $currentState")
            currentState
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse state JSON: ${e.message}")
            MetaSecretCoreStateModel(
                null,
                false
            ).getState()
        }
    }

}