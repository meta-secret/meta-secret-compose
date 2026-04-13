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
import core.AppStateCacheProviderInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.DebugLoggerInterface
import core.LogFormatterInterface
import models.apiModels.ClaimStatus
import models.apiModels.RecoveredSecretModel
import models.apiModels.SearchClaimModel
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel
import core.NotificationCoordinatorInterface
import core.errors.ErrorMapper

sealed class InitResult {
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val logger: DebugLoggerInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val errorMapper: ErrorMapper,
    private val logFormatter: LogFormatterInterface,
    private val appStateCacheProvider: AppStateCacheProviderInterface
): MetaSecretAppManagerInterface {
    
    private var _isAppManagerInitialized = false

    override suspend fun initWithSavedKey(): InitResult {
        if (_isAppManagerInitialized) {
            logger.log(LogTag.AppManager.Message.IsInitiated, "Already initialized", success = true)
            return InitResult.Success("Already initialized")
        }
        
        val masterKey = keyChainInterface.getString("master_key")
        logger.log(LogTag.AppManager.Message.MasterKeyExist, "${masterKey != null}", success = true)
        logger.setMasterKeyGenerated(!masterKey.isNullOrEmpty())
        
        return if (!masterKey.isNullOrEmpty()) {
            try {
                val appManagerResult = withContext(Dispatchers.IO) {
                    metaSecretCore.initAppManager(masterKey)
                }
                logger.log(LogTag.AppManager.Message.IsInitiated, appManagerResult, success = true)
                logger.setAppManagerCreated(true)
                _isAppManagerInitialized = true
                InitResult.Success(appManagerResult)
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.InitError, "${e.message}", success = false)
                logger.setAppManagerCreated(false)
                val appError = errorMapper.mapExceptionToAppError(e)
                val userMessage = errorMapper.getUserFriendlyMessage(appError)
                notificationCoordinator.showError(userMessage)
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
        if (!_isAppManagerInitialized) {
            return null
        }
        return try {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            val currentState = AppStateModel.fromJson(stateJson, logger, logFormatter)
            val appState = currentState.getCurrentAppState()
            logger.setVaultState(appState?.description())
            
            cacheDeviceAndVaultInfoIfNeeded(currentState)
            updateClaimsStats(currentState)
            
            currentState
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseStateJson, "${e.message}", success = false)
            logger.setVaultState(null)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            AppStateModel(null, false)
        }
    }
    
    private fun updateClaimsStats(appState: AppStateModel) {
        val deviceId = appState.getCurrentDeviceId()
        logger.setDeviceId(deviceId)
        
        val vaultInfo = appState.getVaultFullInfo()
        if (vaultInfo is VaultFullInfo.Member) {
            val joinRequestsCount = vaultInfo.member.vaultEvents?.getJoinRequestsCount() ?: 0
            val claims = vaultInfo.member.ssClaims?.claims?.values ?: emptyList()
            
            var pendingCount = 0
            var sentCount = 0
            var deliveredCount = 0
            
            for (claim in claims) {
                val myStatus = claim.status.statuses[deviceId] ?: continue
                when (myStatus) {
                    ClaimStatus.PENDING -> pendingCount++
                    ClaimStatus.SENT -> sentCount++
                    ClaimStatus.DELIVERED -> deliveredCount++
                    else -> { }
                }
            }
            
            logger.setClaimsStats(joinRequestsCount, pendingCount, sentCount, deliveredCount)
        } else {
            logger.setClaimsStats(0, 0, 0, 0)
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
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val stateJson = withContext(Dispatchers.IO) {
            metaSecretCore.getAppState() // It's ok. Uses onli during login
        }
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger, logFormatter)
            val vaultState = currentState.getVaultFullInfo()
            logger.log(LogTag.AppManager.Message.VaultInfo, "$vaultState", success = true)
            vaultState
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultInfoJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun getJoinRequestsCount(): Int? {
        if (!_isAppManagerInitialized) {
            return null
        }
        
        // Using cached state from socket polling
        // metaSecretCore.getAppState() TODO: Need to uncomment, once real socket is ready
        val cachedState = appStateCacheProvider.appState.value ?: return null
        
        return try {
            val vaultEvents = cachedState.getVaultEvents()
            val requestsCount = vaultEvents?.getJoinRequestsCount()
            logger.log(LogTag.AppManager.Message.RequestsCount, "$requestsCount", success = true)
            requestsCount
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseRequestsCountJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? {
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val currentState = if (isSocketAction) {
            appStateCacheProvider.appState.value
        } else {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            AppStateModel.fromJson(stateJson, logger, logFormatter)
        }
        
        if (currentState == null) {
            return null
        }
        
        return try {
            val vaultSummary = currentState.getVaultSummary()
            logger.log(LogTag.AppManager.Message.VaultSummary, "$vaultSummary", success = true)
            vaultSummary
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultSummaryJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? {
        val updateResult = withContext(Dispatchers.IO) {
            metaSecretCore.updateMembership(candidate, actionUpdate)
        }
        return try {
            val result = CommonResponseModel.fromJson(updateResult)
            logger.log(LogTag.AppManager.Message.UpdateCandidateResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseUpdateCandidateJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun getUserDataBy(deviceId: String): UserData? {
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val stateJson = withContext(Dispatchers.IO) {
            metaSecretCore.getAppState() // It's ok. Uses only for user joining
        }
        return try {
            val currentState = AppStateModel.fromJson(stateJson, logger, logFormatter)
            val deviceIdResult = currentState.getUserDataByDeviceId(deviceId)
            logger.log(LogTag.AppManager.Message.GetUserDataByDeviceId, "$deviceId is $deviceIdResult", success = true)
            deviceIdResult
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseGetUserDataByIdJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun checkAuth(): AuthState {
        return when (initWithSavedKey()) {
            is InitResult.Success -> {
                val stateModel = getStateModel()
                val appState = stateModel?.getCurrentAppState()
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
        val splitResult = withContext(Dispatchers.IO) {
            metaSecretCore.splitSecret(secretModel.secretName, secretModel.secret)
        }
        return try {
            val result = CommonResponseModel.fromJson(splitResult)
            logger.log(LogTag.AppManager.Message.SplitSecretResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseSplitSecretJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun findClaim(secretId: String): ClaimModel? {
        logger.log(LogTag.AppManager.Message.FindClaimStarted, success = true)
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val searchResult = withContext(Dispatchers.IO) {
            metaSecretCore.findClaim(secretId)
        }
        return try {
            val result = SearchClaimModel.fromJson(searchResult)
            if (result.claim == null) {
                null
            } else {
                result.claim
            }
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseFindClaimJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? {
        if (secretModel.secretName == null) {
            logger.log(LogTag.AppManager.Message.RecoverSecretIdNull, success = true)
            return null
        }
        val recoverRequestResult = withContext(Dispatchers.IO) {
            metaSecretCore.recover(secretModel.secretName)
        }
        return try {
            val result = CommonResponseModel.fromJson(recoverRequestResult)
            logger.log(LogTag.AppManager.Message.RecoverRequestResult, "$result", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseRecoverRequestJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun acceptRecover(claimId: String?): AppStateModel? {
        logger.log(LogTag.AppManager.Message.AcceptRecoverStarted, success = true)
        if (claimId == null) {
            return null
        }
        val acceptResult = withContext(Dispatchers.IO) {
            metaSecretCore.acceptRecover(claimId)
        }
        return try {
            val result = AppStateModel.fromJson(acceptResult, logger, logFormatter)
            logger.log(LogTag.AppManager.Message.AcceptRecoverResult, "success=${result.success}", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseAcceptRecoverJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun declineRecover(claimId: String?): AppStateModel? {
        logger.log(LogTag.AppManager.Message.DeclineRecoverStarted, success = true)
        if (claimId == null) {
            return null
        }
        val declineResult = withContext(Dispatchers.IO) {
            metaSecretCore.declineRecover(claimId)
        }
        return try {
            val result = AppStateModel.fromJson(declineResult, logger, logFormatter)
            logger.log(LogTag.AppManager.Message.DeclineRecoverResult, "success=${result.success}", success = true)
            result
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseDeclineRecoverJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun sendDeclineCompletion(claimId: String?) {
        if (claimId == null) return
        withContext(Dispatchers.IO) {
            metaSecretCore.sendDeclineCompletion(claimId)
        }
    }

    override suspend fun showRecovered(secretModel: SecretModel): String? {
        logger.log(LogTag.AppManager.Message.ShowRecovered, success = true)
        if (secretModel.secretName == null) {
            return null
        }
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val showRecoveredResult = withContext(Dispatchers.IO) {
            metaSecretCore.showRecovered(secretModel.secretName)
        }
        return try {
            val parsed = RecoveredSecretModel.fromJson(showRecoveredResult)
            logger.log(LogTag.AppManager.Message.ShowRecoveredSuccess, "success=${parsed.success} hasSecret=${parsed.message?.secret != null}", success = true)
            parsed.message?.secret
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseShowRecoveredJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<models.apiModels.SecretApiModel>? {
        if (!_isAppManagerInitialized) {
            return null
        }
        
        val currentState = if (isSocketAction) {
            appStateCacheProvider.appState.value
        } else {
            val stateJson = withContext(Dispatchers.IO) {
                metaSecretCore.getAppState()
            }
            AppStateModel.fromJson(stateJson, logger, logFormatter)
        }
        
        if (currentState == null) {
            return null
        }
        
        return try {
            val secrets = when (val vaultInfo = currentState.getVaultFullInfo()) {
                is VaultFullInfo.Member -> vaultInfo.member.member.vault.secrets
                else -> emptyList()
            }
            logger.log(LogTag.AppManager.Message.GetSecretsFromVaultResult, "$secrets", success = true)
            secrets
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseGetSecretsFromVaultJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }
}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}
