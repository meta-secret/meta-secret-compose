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
import core.LogTag
import core.DebugLoggerInterface
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
    private val logger: DebugLoggerInterface,
): MetaSecretAppManagerInterface {

    override suspend fun initWithSavedKey(): InitResult {
        val masterKey = keyChainInterface.getString("master_key")
        logger.log(LogTag.AppManager.Message.MasterKeyExist, "${masterKey != null}", success = true)
        logger.setMasterKeyGenerated(!masterKey.isNullOrEmpty())
        
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = withContext(Dispatchers.IO) {
                    metaSecretCore.initAppManager(masterKey)
                }
                logger.log(LogTag.AppManager.Message.IsInitiated, "$appManagerResult", success = true)
                logger.setAppManagerCreated(true)
                InitResult.Success(appManagerResult)
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.InitError, "${e.message}", success = false)
                logger.setAppManagerCreated(false)
                InitResult.Error(e.message ?: "Unknown error")
            }
        } else {
            logger.log(LogTag.AppManager.Message.InitErrorNoMasterKey, success = false)
            logger.setAppManagerCreated(false)
            keyChainInterface.clearAll(isCleanDB = true)
            InitResult.Error("No master key found")
        }
    }

    override fun getStateModel(): AppStateModel? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            logger.log(LogTag.AppManager.Message.CurrentState, "$currentState", success = true)
            
            cacheDeviceAndVaultInfoIfNeeded(currentState)
            
            currentState
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseStateJson, "${e.message}", success = false)
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
                logger.log(LogTag.AppManager.Message.CachedDeviceId, "$deviceId", success = true)
            }
            
            if (keyValueStorage.cachedVaultName == null) {
                keyValueStorage.cachedVaultName = vaultName
                logger.log(LogTag.AppManager.Message.CachedVaultName, "$vaultName", success = true)
            }
        }
    }

    override fun getVaultFullInfoModel(): VaultFullInfo? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultState = currentState.getVaultFullInfo()
            logger.log(LogTag.AppManager.Message.VaultInfo, "$vaultState", success = true)
            vaultState
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultInfoJson, "${e.message}", success = false)
            null
        }
    }

    override fun getVaultEventsModel(): VaultEvents? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultEvents = currentState.getVaultEvents()
            logger.log(LogTag.AppManager.Message.VaultEvents, "$vaultEvents", success = true)
            vaultEvents
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultEventsJson, "${e.message}", success = false)
            null
        }
    }

    override fun getJoinRequestsCount(): Int? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultEvents = currentState.getVaultEvents()
            val requestsCount = vaultEvents?.getJoinRequestsCount()
            logger.log(LogTag.AppManager.Message.RequestsCount, "$requestsCount", success = true)
            requestsCount
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseRequestsCountJson, "${e.message}", success = false)
            null
        }
    }

    override fun getJoinRequestsCandidate(): List<JoinClusterRequest>? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultEvents = currentState.getVaultEvents()
            val requests = vaultEvents?.getJoinRequests()
            logger.log(LogTag.AppManager.Message.GetJoinRequests, "$requests", success = true)
            requests
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseGetJoinRequestsJson, "${e.message}", success = false)
            null
        }
    }

    override fun getVaultSummary(): VaultSummary? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultSummary = currentState.getVaultSummary()
            logger.log(LogTag.AppManager.Message.VaultSummary, "$vaultSummary", success = true)
            vaultSummary
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultSummaryJson, "${e.message}", success = false)
            null
        }
    }

    override fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? {
        val updateResult = metaSecretCore.updateMembership(candidate, actionUpdate)
        return try {
            val result = CommonResponseModel.fromJson(updateResult)
            logger.log(LogTag.AppManager.Message.UpdateCandidateResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseUpdateCandidateJson, "${e.message}", success = false)
            null
        }
    }

    override fun getUserDataBy(deviceId: String): UserData? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val deviceIdResult = currentState.getUserDataByDeviceId(deviceId)
            logger.log(LogTag.AppManager.Message.GetUserDataByDeviceId, "$deviceId is $deviceIdResult", success = true)
            deviceIdResult
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseGetUserDataByIdJson, "${e.message}", success = false)
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
        logger.log(LogTag.AppManager.Message.SplitSecretStarted, success = true)
        if (secretModel.secretName == null || secretModel.secret == null) {
            return null
        }
        val splitResult = metaSecretCore.splitSecret(secretModel.secretName, secretModel.secret)
        return try {
            val result = CommonResponseModel.fromJson(splitResult)
            logger.log(LogTag.AppManager.Message.SplitSecretResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseSplitSecretJson, "${e.message}", success = false)
            null
        }
    }

    override fun findClaim(secretId: String): ClaimModel? {
        logger.log(LogTag.AppManager.Message.FindClaimStarted, success = true)
        val searchResult = metaSecretCore.findClaim(secretId)
        return try {
            val result = SearchClaimModel.fromJson(searchResult)
            logger.log(LogTag.AppManager.Message.FindClaimResult, "$result", success = true)
            if (result.claimId == null) {
                return null
            }
            ClaimModel(result.claimId)
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseFindClaimJson, "${e.message}", success = false)
            null
        }
    }

    override fun recover(secretModel: SecretModel): CommonResponseModel? {
        if (secretModel.secretName == null) {
            logger.log(LogTag.AppManager.Message.RecoverSecretIdNull, success = true)
            return null
        }
        val recoverRequestResult = metaSecretCore.recover(secretModel.secretName)
        return try {
            val result = CommonResponseModel.fromJson(recoverRequestResult)
            logger.log(LogTag.AppManager.Message.RecoverRequestResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseRecoverRequestJson, "${e.message}", success = false)
            null
        }
    }

    override fun acceptRecover(claim: ClaimModel): CommonResponseModel? {
        logger.log(LogTag.AppManager.Message.AcceptRecoverStarted, success = true)
        if (claim.claimId == null) {
            return null
        }
        val acceptResult = metaSecretCore.acceptRecover(claim.claimId)
        return try {
            val result = CommonResponseModel.fromJson(acceptResult)
            logger.log(LogTag.AppManager.Message.AcceptRecoverResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseAcceptRecoverJson, "${e.message}", success = false)
            null
        }
    }

    override fun showRecovered(secretModel: SecretModel): String? {
        logger.log(LogTag.AppManager.Message.ShowRecovered, success = true)
        if (secretModel.secretName == null) {
            return null
        }
        val showRecoveredResult = metaSecretCore.showRecovered(secretModel.secretName)
        return try {
            val parsed = RecoveredSecretModel.fromJson(showRecoveredResult)
            logger.log(LogTag.AppManager.Message.ShowRecoveredSuccess, "success=${parsed.success} hasSecret=${parsed.message?.secret != null}", success = true)
            parsed.message?.secret
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseShowRecoveredJson, "${e.message}", success = false)
            null
        }
    }

    override fun getSecretsFromVault(): List<models.apiModels.SecretApiModel>? {
        val stateJson = metaSecretCore.getAppState()
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger)
            val vaultInfo = currentState.getVaultFullInfo()
            val secrets = when (vaultInfo) {
                is VaultFullInfo.Member -> vaultInfo.member.member.vault.secrets
                else -> emptyList()
            }
            logger.log(LogTag.AppManager.Message.GetSecretsFromVaultResult, "$secrets", success = true)
            secrets
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseGetSecretsFromVaultJson, "${e.message}", success = false)
            null
        }
    }
}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}