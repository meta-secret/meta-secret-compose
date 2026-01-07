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
    private val ffiSynchronizer: FfiSynchronizerInterface,
): MetaSecretAppManagerInterface {

    override suspend fun initWithSavedKey(): InitResult {
        if (ffiSynchronizer.isAppManagerInitialized) {
            logger.log(LogTag.AppManager.Message.IsInitiated, "Already initialized", success = true)
            return InitResult.Success("Already initialized")
        }
        
        val masterKey = keyChainInterface.getString("master_key")
        logger.log(LogTag.AppManager.Message.MasterKeyExist, "${masterKey != null}", success = true)
        logger.setMasterKeyGenerated(!masterKey.isNullOrEmpty())
        
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = ffiSynchronizer.withFfiLock {
                    withContext(Dispatchers.IO) {
                        metaSecretCore.initAppManager(masterKey)
                    }
                }
                logger.log(LogTag.AppManager.Message.IsInitiated, appManagerResult, success = true)
                logger.setAppManagerCreated(true)
                ffiSynchronizer.markAppManagerInitialized()
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

    override suspend fun getStateModel(): AppStateModel? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                logger.log(LogTag.AppManager.Message.CurrentState, "$currentState", success = true)
                
                val appState = currentState.getAppState()
                logger.setVaultState(appState?.description())
                
                cacheDeviceAndVaultInfoIfNeeded(currentState)
                
                currentState
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseStateJson, "${e.message}", success = false)
                logger.setVaultState(null)
                AppStateModel(null, false)
            }
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
                logger.log(LogTag.AppManager.Message.CachedDeviceId, deviceId, success = true)
            }
            
            if (keyValueStorage.cachedVaultName == null) {
                keyValueStorage.cachedVaultName = vaultName
                logger.log(LogTag.AppManager.Message.CachedVaultName, vaultName, success = true)
            }
        }
    }

    override suspend fun getVaultFullInfoModel(): VaultFullInfo? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                val vaultState = currentState.getVaultFullInfo()
                logger.log(LogTag.AppManager.Message.VaultInfo, "$vaultState", success = true)
                vaultState
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseVaultInfoJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun getVaultEventsModel(): VaultEvents? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                val vaultEvents = currentState.getVaultEvents()
                logger.log(LogTag.AppManager.Message.VaultEvents, "$vaultEvents", success = true)
                vaultEvents
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseVaultEventsJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun getJoinRequestsCount(): Int? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
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
    }

    override suspend fun getJoinRequestsCandidate(): List<JoinClusterRequest>? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
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
    }

    override suspend fun getVaultSummary(): VaultSummary? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                val vaultSummary = currentState.getVaultSummary()
                logger.log(LogTag.AppManager.Message.VaultSummary, "$vaultSummary", success = true)
                vaultSummary
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseVaultSummaryJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? {
        return ffiSynchronizer.withFfiLock {
            val updateResult = withContext(Dispatchers.IO) {
                metaSecretCore.updateMembership(candidate, actionUpdate)
            }
            try {
                val result = CommonResponseModel.fromJson(updateResult)
                logger.log(LogTag.AppManager.Message.UpdateCandidateResult, "$result", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseUpdateCandidateJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun getUserDataBy(deviceId: String): UserData? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                val deviceIdResult = currentState.getUserDataByDeviceId(deviceId)
                logger.log(LogTag.AppManager.Message.GetUserDataByDeviceId, "$deviceId is $deviceIdResult", success = true)
                deviceIdResult
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseGetUserDataByIdJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun checkAuth(): AuthState {
        return when (initWithSavedKey()) {
            is InitResult.Success -> {
                val stateModel = getStateModel()
                val appState = stateModel?.getAppState()
                logger.setVaultState(appState?.description())
                
                return when (appState) {
                    is State.Vault -> {
                        when (stateModel.getVaultFullInfo()) {
                            is VaultFullInfo.Member -> AuthState.COMPLETED
                            else -> AuthState.NOT_YET_COMPLETED
                        }
                    }

                    else -> {
                        AuthState.NOT_YET_COMPLETED
                    }
                }
            }

            else -> {
                logger.setVaultState(null)
                AuthState.NOT_YET_COMPLETED
            }
        }
    }

    override suspend fun splitSecret(secretModel: SecretModel): CommonResponseModel? {
        logger.log(LogTag.AppManager.Message.SplitSecretStarted, success = true)
        if (secretModel.secretName == null || secretModel.secret == null) {
            return null
        }
        return ffiSynchronizer.withFfiLock {
            val splitResult = withContext(Dispatchers.IO) {
                metaSecretCore.splitSecret(secretModel.secretName, secretModel.secret)
            }
            try {
                val result = CommonResponseModel.fromJson(splitResult)
                logger.log(LogTag.AppManager.Message.SplitSecretResult, "$result", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseSplitSecretJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun findClaim(secretId: String): ClaimModel? {
        logger.log(LogTag.AppManager.Message.FindClaimStarted, success = true)
        return ffiSynchronizer.withFfiLockIfInitialized {
            val searchResult = withContext(Dispatchers.IO) {
                metaSecretCore.findClaim(secretId)
            }
            try {
                val result = SearchClaimModel.fromJson(searchResult)
                logger.log(LogTag.AppManager.Message.FindClaimResult, "$result", success = true)
                if (result.claimId == null) {
                    null
                } else {
                    ClaimModel(result.claimId)
                }
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseFindClaimJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? {
        if (secretModel.secretName == null) {
            logger.log(LogTag.AppManager.Message.RecoverSecretIdNull, success = true)
            return null
        }
        return ffiSynchronizer.withFfiLock {
            val recoverRequestResult = withContext(Dispatchers.IO) {
                metaSecretCore.recover(secretModel.secretName)
            }
            try {
                val result = CommonResponseModel.fromJson(recoverRequestResult)
                logger.log(LogTag.AppManager.Message.RecoverRequestResult, "$result", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseRecoverRequestJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun acceptRecover(claim: ClaimModel): CommonResponseModel? {
        logger.log(LogTag.AppManager.Message.AcceptRecoverStarted, success = true)
        if (claim.claimId == null) {
            return null
        }
        return ffiSynchronizer.withFfiLock {
            val acceptResult = withContext(Dispatchers.IO) {
                metaSecretCore.acceptRecover(claim.claimId)
            }
            try {
                val result = CommonResponseModel.fromJson(acceptResult)
                logger.log(LogTag.AppManager.Message.AcceptRecoverResult, "$result", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseAcceptRecoverJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun showRecovered(secretModel: SecretModel): String? {
        logger.log(LogTag.AppManager.Message.ShowRecovered, success = true)
        if (secretModel.secretName == null) {
            return null
        }
        return ffiSynchronizer.withFfiLockIfInitialized {
            val showRecoveredResult = withContext(Dispatchers.IO) {
                metaSecretCore.showRecovered(secretModel.secretName)
            }
            try {
                val parsed = RecoveredSecretModel.fromJson(showRecoveredResult)
                logger.log(LogTag.AppManager.Message.ShowRecoveredSuccess, "success=${parsed.success} hasSecret=${parsed.message?.secret != null}", success = true)
                parsed.message?.secret
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseShowRecoveredJson, "${e.message}", success = false)
                null
            }
        }
    }

    override suspend fun getSecretsFromVault(): List<models.apiModels.SecretApiModel>? {
        return ffiSynchronizer.withFfiLockIfInitialized {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            try {
                val currentState = AppStateModel.fromJson(stateJson, logger)
                val secrets = when (val vaultInfo = currentState.getVaultFullInfo()) {
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
}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}
