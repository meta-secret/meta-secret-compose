package sharedData.metaSecretCore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.JoinClusterRequest
import models.apiModels.State
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import sharedData.KeyChainInterface

sealed class InitResult {
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
): MetaSecretAppManagerInterface {

    override suspend fun initWithSavedKey(): InitResult {
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

    override fun getStateModel(): AppStateModel? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            println("\uD83D\uDEE0\uFE0F AppManager: currentState is $currentState")
            currentState
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse state JSON: ${e.message}")
            AppStateModel(
                null,
                false
            )
        }
    }

    override fun getVaultFullInfoModel(): VaultFullInfo? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultState = currentState.getVaultFullInfo()
            println("\uD83D\uDEE0\uFE0F AppManager: vaultInfo is $vaultState")
            vaultState
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse vaultInfo JSON: ${e.message}")
            null
        }
    }

    override fun getVaultEventsModel(): VaultEvents? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            println("\uD83D\uDEE0\uFE0F AppManager: vaultEvents is $vaultEvents")
            vaultEvents
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse vaultEvents JSON: ${e.message}")
            null
        }
    }

    override fun getJoinRequestsCount(): Int? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            val requestsCount = vaultEvents?.getJoinRequestsCount()
            println("\uD83D\uDEE0\uFE0F AppManager: requestsCount is $requestsCount")
            requestsCount
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse requestsCount JSON: ${e.message}")
            null
        }
    }

    override fun getJoinRequestsCandidate(): List<JoinClusterRequest>? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            val requests = vaultEvents?.getJoinRequests()
            println("\uD83D\uDEE0\uFE0F AppManager: getJoinRequests is $requests")
            requests
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse getJoinRequests JSON: ${e.message}")
            null
        }
    }

    override fun getVaultSummary(): VaultSummary? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultSummary = currentState.getVaultSummary()
            println("\uD83D\uDEE0\uFE0F AppManager: vaultSummary is $vaultSummary")
            vaultSummary
        } catch (e: Exception) {
            println("\uD83D\uDEE0\uFE0F ⛔ AppManager: Failed to parse vaultSummary JSON: ${e.message}")
            null
        }
    }

    override suspend fun checkAuth(): AuthState {
        return when (initWithSavedKey()) {
            is InitResult.Success -> {
                when (getStateModel()?.getAppState()) {
                    is State.Vault -> {
                        when (getStateModel()?.getVaultFullInfo()) {
                            is VaultFullInfo.Member -> return AuthState.COMPLETED
                            else -> return AuthState.NOT_YET_COMPLETED
                        }
                    }
                    else -> {
                        return AuthState.NOT_YET_COMPLETED
                    }
                }
            }
            else -> AuthState.NOT_YET_COMPLETED
        }
    }

}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}