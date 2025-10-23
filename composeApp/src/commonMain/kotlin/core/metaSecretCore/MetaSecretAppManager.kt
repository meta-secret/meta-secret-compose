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
import core.KeyValueStorageInterface
import core.LogTags
import models.apiModels.RecoveredSecretModel
import models.apiModels.SearchClaimModel
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel

sealed class InitResult {
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
    private val keyValueStorage: KeyValueStorageInterface,
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
            
            cacheDeviceAndVaultInfoIfNeeded(currentState)
            
            currentState
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse state JSON: ${e.message}")
            AppStateModel(
                null,
                false
            )
        }
    }
    
    private fun cacheDeviceAndVaultInfoIfNeeded(appState: AppStateModel) {
        if (keyValueStorage.cachedDeviceId != null && keyValueStorage.cachedVaultName != null) {
            return
        }
        
        val vaultInfo = appState.getVaultFullInfo()
        if (vaultInfo is VaultFullInfo.Member) {
            val deviceId = vaultInfo.member.member.member.userData.device.deviceId
            val vaultName = vaultInfo.member.member.member.userData.vaultName
            
            if (keyValueStorage.cachedDeviceId == null) {
                keyValueStorage.cachedDeviceId = deviceId
                println("✅" + LogTags.APP_MANAGER + ": Cached deviceId: $deviceId")
            }
            
            if (keyValueStorage.cachedVaultName == null) {
                keyValueStorage.cachedVaultName = vaultName
                println("✅" + LogTags.APP_MANAGER + ": Cached vaultName: $vaultName")
            }
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

    override fun splitSecret(secretModel: SecretModel): CommonResponseModel? {
        println("✅" + LogTags.APP_MANAGER + ": split Secret started")
        if (secretModel.secretName == null || secretModel.secret == null) {
            return null
        }
        val splitResult = metaSecretCore.splitSecret(secretModel.secretName, secretModel.secret)
        return try {
            val result = CommonResponseModel.fromJson(splitResult)
            println("✅" + LogTags.APP_MANAGER + ": split Secret result is $result")
            result
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse split Secret JSON: ${e.message}")
            null
        }
    }

    override fun findClaim(secretId: String): ClaimModel? {
        println("✅" + LogTags.APP_MANAGER + ": find claim started")
        val searchResult = metaSecretCore.findClaim(secretId)
        return try {
            val result = SearchClaimModel.fromJson(searchResult)
            println("✅" + LogTags.APP_MANAGER + ": find Claim result is $result")
            if (result.claimId == null) {
                return null
            }
            ClaimModel(result.claimId)
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse find claim JSON: ${e.message}")
            null
        }
    }

    override fun recover(secretModel: SecretModel): CommonResponseModel? {
        if (secretModel.secretName == null) {
            println("✅" + LogTags.APP_MANAGER + ": recover secret Id is Null")
            return null
        }
        val recoverRequestResult = metaSecretCore.recover(secretModel.secretName)
        return try {
            val result = CommonResponseModel.fromJson(recoverRequestResult)
            println("✅" + LogTags.APP_MANAGER + ": recover request result is $result")
            result
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse recover request JSON: ${e.message}")
            null
        }
    }

    override fun acceptRecover(claim: ClaimModel): CommonResponseModel? {
        println("✅" + LogTags.APP_MANAGER + ": Accept recover started")
        if (claim.claimId == null) {
            return null
        }
        val acceptResult = metaSecretCore.acceptRecover(claim.claimId)
        return try {
            val result = CommonResponseModel.fromJson(acceptResult)
            println("✅" + LogTags.APP_MANAGER + ": Accept recover result is $result")
            result
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse Accept Recover JSON: ${e.message}")
            null
        }
    }

    override fun showRecovered(secretModel: SecretModel): String? {
        println("✅" + LogTags.APP_MANAGER + ": showRecovered")
        if (secretModel.secretName == null) {
            return null
        }
        val showRecoveredResult = metaSecretCore.showRecovered(secretModel.secretName)
        return try {
            val result = RecoveredSecretModel.fromJson(showRecoveredResult)
            println("✅" + LogTags.APP_MANAGER + ": showRecovered result is $result")
            result.message?.secret
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse showRecovered JSON: ${e.message}")
            null
        }
    }

    override fun getSecretsFromVault(): List<models.apiModels.SecretApiModel>? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson)
            val vaultInfo = currentState.getVaultFullInfo()
            val secrets = when (vaultInfo) {
                is VaultFullInfo.Member -> vaultInfo.member.member.vault.secrets
                else -> emptyList()
            }
            println("✅" + LogTags.APP_MANAGER + ": getSecretsFromVault result is $secrets")
            secrets
        } catch (e: Exception) {
            println("❌" + LogTags.APP_MANAGER + ": Failed to parse getSecretsFromVault JSON: ${e.message}")
            null
        }
    }
}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}