package core.metaSecretCore.liveboard

import core.LogTag
import models.apiModels.AppStateModel
import models.apiModels.ClientStatus
import models.apiModels.UserData
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary

/** The original Manager's state consumption, using its same grouped ports and reader. */
internal class NativeManagerStateReader(
    ports: NativeManagerPorts,
    presentation: NativeManagerPresentation,
    private val readOwner: NativeCanonicalReadOwner,
    private val operations: NativeOperationPort,
) {
    private val logger = ports.logger
    private val keyValueStorage = ports.storage
    private val notificationCoordinator = presentation.notification
    private val errorMapper = presentation.errorMapper
    private val appStateCacheProvider = presentation.cache

    private suspend fun readCurrentState(expected: NativeReadScope?, turn: NativeReadTurn? = null): AppStateModel? {
        val call = readOwner.readAttempt(expected, turn)
        if (!readOwner.mayConsumeActualRead(call)) return null
        return when (call) {
            is NativeReadCall.Value -> call.frame.state
            is NativeReadCall.Failure -> throw call.cause
        }
    }

    suspend fun getStateModel(): AppStateModel? = getStateModel(operations.capture(), null)

    internal suspend fun getStateModel(expected: NativeReadScope?, inherited: NativeReadTurn?): AppStateModel? {
        val turn = inherited ?: readOwner.beginRead(expected)
        if (turn == null || turn.scope != expected || !readOwner.ownsRead(turn)) {
            if (turn != null && inherited == null) readOwner.finishRead(turn, false)
            return null
        }
        var begun = false
        var successful = false
        var completed = false
        return try {
            begun = true
            appStateCacheProvider.beginSynchronization()
            getStateModelInternal(expected, turn).also { successful = it?.success == true }
        } finally {
            try {
                if (begun) appStateCacheProvider.finishSynchronization(successful)
                completed = successful
            } finally {
                if (inherited == null) readOwner.finishRead(turn, completed)
            }
        }
    }

    private suspend fun getStateModelInternal(expected: NativeReadScope?, turn: NativeReadTurn): AppStateModel? {
        if (expected == null) return null
        val currentState = readCurrentState(expected, turn)
        return if (currentState == null) null else try {
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
                when (claim.clientStatus) {
                    ClientStatus.NEED_APPROVE -> pendingCount++
                    ClientStatus.ACCEPTED -> sentCount++
                    ClientStatus.DONE -> deliveredCount++
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

    suspend fun getVaultFullInfoModel(): VaultFullInfo? {
        val expected = operations.capture() ?: return null
        val currentState = readCurrentState(expected)
        return if (currentState == null) null else try {
            val vaultState = currentState.getVaultFullInfo()
            logger.log(LogTag.AppManager.Message.VaultInfo, "stateLoaded=true", success = true)
            vaultState
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultInfoJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? {
        val expected = operations.capture() ?: return null
        
        val currentState = if (isSocketAction) {
            appStateCacheProvider.appState.value
        } else {
            readCurrentState(expected)
        }
        
        return if (currentState == null || !currentState.success) null else try {
            val vaultSummary = currentState.getVaultSummary()
            logger.log(LogTag.AppManager.Message.VaultSummary, "loaded=true", success = true)
            vaultSummary
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseVaultSummaryJson, "${e.message}", success = false)
            val appError = errorMapper.mapExceptionToAppError(e)
            val userMessage = errorMapper.getUserFriendlyMessage(appError)
            notificationCoordinator.showError(userMessage)
            null
        }
    }

    suspend fun getUserDataBy(deviceId: String): UserData? {
        val expected = operations.capture() ?: return null
        val currentState = readCurrentState(expected)
        return if (currentState == null) null else try {
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

    suspend fun getSecretsFromVault(isSocketAction: Boolean): List<models.apiModels.SecretApiModel>? {
        val expected = operations.capture() ?: return null
        
        val currentState = if (isSocketAction) {
            appStateCacheProvider.appState.value
        } else {
            readCurrentState(expected)
        }
        
        return if (currentState == null || !currentState.success) null else try {
            val secrets = when (val vaultInfo = currentState.getVaultFullInfo()) {
                is VaultFullInfo.Member -> vaultInfo.member.member.vault.secrets
                else -> emptyList()
            }
            logger.log(LogTag.AppManager.Message.GetSecretsFromVaultResult, "count=${secrets.size}", success = true)
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
