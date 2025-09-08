package core.metaSecretCore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.JoinClusterRequest
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.VaultEvents
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import core.KeyChainInterface
import core.LogTags

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
        println("✅" + LogTags.APP_MANAGER + ": is Master key exist: ${masterKey != null}")
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = withContext(Dispatchers.IO) {
                    metaSecretCore.initAppManager(masterKey)
                }
                println("✅" + LogTags.APP_MANAGER + ": is initiated: $appManagerResult")
                InitResult.Success(appManagerResult)
            } catch (e: Exception) {
                println("❌" + LogTags.APP_MANAGER + ": init error: ${e.message}")
                InitResult.Error(e.message ?: "Unknown error")
            }
        } else {
            println("❌" + LogTags.APP_MANAGER + ": init error: No master key found")
            keyChainInterface.clearAll(isCleanDB = true)
            InitResult.Error("No master key found")
        }
    }

    override fun getStateModel(): AppStateModel? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            println("✅" + LogTags.APP_MANAGER + ": currentState is $currentState")
            currentState
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse state JSON: ${e.message}")
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
            println("✅" + LogTags.APP_MANAGER + ": vaultInfo is $vaultState")
            vaultState
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse vaultInfo JSON: ${e.message}")
            null
        }
    }

    override fun getVaultEventsModel(): VaultEvents? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            println("✅" + LogTags.APP_MANAGER + ": vaultEvents is $vaultEvents")
            vaultEvents
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse vaultEvents JSON: ${e.message}")
            null
        }
    }

    override fun getJoinRequestsCount(): Int? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            val requestsCount = vaultEvents?.getJoinRequestsCount()
            println("✅" + LogTags.APP_MANAGER + ": requestsCount is $requestsCount")
            requestsCount
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse requestsCount JSON: ${e.message}")
            null
        }
    }

    override fun getJoinRequestsCandidate(): List<JoinClusterRequest>? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultEvents = currentState.getVaultEvents()
            val requests = vaultEvents?.getJoinRequests()
            println("✅" + LogTags.APP_MANAGER + ": getJoinRequests is $requests")
            requests
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse getJoinRequests JSON: ${e.message}")
            null
        }
    }

    override fun getVaultSummary(): VaultSummary? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultSummary = currentState.getVaultSummary()
            println("✅" + LogTags.APP_MANAGER + ": vaultSummary is $vaultSummary")
            vaultSummary
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse vaultSummary JSON: ${e.message}")
            null
        }
    }

    override fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? {
        val updateResult = metaSecretCore.updateMembership(candidate, actionUpdate)
        return try {
            val result = CommonResponseModel.fromJson(updateResult)
            println("✅" + LogTags.APP_MANAGER + ": update candidate result is $result")
            result
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse updatecandidate candidate JSON: ${e.message}")
            null
        }
    }

    override fun getUserDataBy(deviceId: String): UserData? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val deviceIdResult = currentState.getUserDataByDeviceId(deviceId)
            println("✅" + LogTags.APP_MANAGER + ": getUserDataBy deviceId $deviceId is $deviceIdResult")
            deviceIdResult
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse getUserDataById JSON: ${e.message}")
            null
        }
    }

    override suspend fun checkAuth(): AuthState {
        return when (initWithSavedKey()) {
            is InitResult.Success -> {
                return when (getStateModel()?.getAppState()) {
                    is State.Vault -> {
                        when (getStateModel()?.getVaultFullInfo()) {
                            is VaultFullInfo.Member -> AuthState.COMPLETED
                            else -> AuthState.NOT_YET_COMPLETED
                        }
                    }

                    else -> {
                        AuthState.NOT_YET_COMPLETED
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