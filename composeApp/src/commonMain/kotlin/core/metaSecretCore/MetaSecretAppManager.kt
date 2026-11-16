package core.metaSecretCore

import kotlinx.coroutines.CancellationException
import models.apiModels.AppStateModel
import models.apiModels.CommonResponseModel
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.VaultFullInfo
import models.apiModels.VaultSummary
import core.LogTag
import models.apiModels.RecoveredSecretModel
import models.apiModels.SearchClaimModel
import models.appInternalModels.ClaimModel
import models.appInternalModels.SecretModel
import core.metaSecretCore.liveboard.NativeManagerStateReader
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeManagerRuntime
import core.metaSecretCore.liveboard.NativeReadScope
import core.metaSecretCore.liveboard.NativeManagerPorts
import core.metaSecretCore.liveboard.NativeManagerPresentation

sealed class InitResult {
    data class Success(val result: String) : InitResult()
    data class Error(val message: String) : InitResult()
}

class MetaSecretAppManager(
    ports: NativeManagerPorts,
    presentation: NativeManagerPresentation,
    private val readOwner: NativeCanonicalReadOwner,
    runtime: NativeManagerRuntime,
): MetaSecretAppManagerInterface {
    private val metaSecretCore = ports.core
    private val keyLifecycle = runtime.keyLifecycle
    private val operations = runtime.operations
    private val logger = ports.logger
    private val notificationCoordinator = presentation.notification
    private val errorMapper = presentation.errorMapper
    private val logFormatter = presentation.logFormatter
    private val appStateCacheProvider = presentation.cache
    
    private val stateReader = NativeManagerStateReader(ports, presentation, readOwner, operations)

    private val _isAppManagerInitialized: Boolean get() = keyLifecycle.isInitialized

    override suspend fun initWithSavedKey(): InitResult {
        val result = keyLifecycle.initializeSavedKey(keyLifecycle.capture())
            ?: return InitResult.Error("Native owner retired")
        if (!keyLifecycle.isCurrent(result.owner)) return InitResult.Error("Native owner retired")
        val initialized = result.value is InitResult.Success
        logger.setMasterKeyGenerated(initialized || (result.value as? InitResult.Error)?.message !=
            "No master key found")
        logger.setAppManagerCreated(initialized)
        return when (val value = result.value) {
            is InitResult.Success -> {
                logger.log(LogTag.AppManager.Message.IsInitiated, value.result, success = true)
                value
            }
            is InitResult.Error -> {
                logger.log(LogTag.AppManager.Message.InitError, value.message, success = false)
                result.failure?.takeIf { keyLifecycle.isCurrent(result.owner) }?.let { failure ->
                    val appError = errorMapper.mapExceptionToAppError(failure)
                    notificationCoordinator.showError(errorMapper.getUserFriendlyMessage(appError))
                }
                value
            }
        }
    }

    override suspend fun getStateModel(): AppStateModel? = stateReader.getStateModel()

    override suspend fun getVaultFullInfoModel(): VaultFullInfo? = stateReader.getVaultFullInfoModel()

    override suspend fun getJoinRequestsCount(): Int? {
        if (!_isAppManagerInitialized) {
            return null
        }
        
        // Uses AppState refreshed by launch, lifecycle, explicit action, or socket invalidation.
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

    override suspend fun getVaultSummary(isSocketAction: Boolean): VaultSummary? =
        stateReader.getVaultSummary(isSocketAction)

    private fun notifyOperationError(scope: NativeReadScope, error: Exception) {
        if (error is CancellationException) throw error
        if (!operations.isCurrent(scope)) return
        val appError = errorMapper.mapExceptionToAppError(error)
        val userMessage = errorMapper.getUserFriendlyMessage(appError)
        if (operations.isCurrent(scope)) notificationCoordinator.showError(userMessage)
    }

    override suspend fun updateMember(candidate: UserData, actionUpdate: String): CommonResponseModel? {
        return operations.execute(native = {
            metaSecretCore.updateMembership(candidate, actionUpdate)
        }) { expected, updateResult ->
            try {
                val result = CommonResponseModel.fromJson(updateResult)
                logger.log(LogTag.AppManager.Message.UpdateCandidateResult, "success=${result.success}", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseUpdateCandidateJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun deleteSecret(secretId: String): CommonResponseModel? =
        deletionOperation { metaSecretCore.deleteSecret(secretId) }

    override suspend fun decideDeleteSecret(requestId: String, accepted: Boolean): CommonResponseModel? =
        deletionOperation {
            metaSecretCore.decideDeleteSecret(kotlinx.serialization.json.Json.encodeToString(
                models.apiModels.SecretDeletionDecision.serializer(),
                models.apiModels.SecretDeletionDecision(requestId, accepted)))
        }

    private suspend fun deletionOperation(operation: () -> String): CommonResponseModel? {
        val expected = operations.capture() ?: return null
        val turn = readOwner.beginRead(expected) ?: return null
        var begun = false
        var synchronized = false
        var completed = false
        return try {
            begun = true
            appStateCacheProvider.beginSynchronization()
            operations.execute(expected, operation) { _, response ->
                val result = CommonResponseModel.fromJson(response)
                // Even an error can follow an applied request: reconcile only its current owner.
                if (!operations.isCurrent(expected)) return@execute null
                val state = stateReader.getStateModel(expected, turn)
                if (!operations.isCurrent(expected)) return@execute null
                synchronized = state?.success == true
                result
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (operations.isCurrent(expected)) CommonResponseModel(success = false) else null
        } finally {
            try {
                if (begun) appStateCacheProvider.finishSynchronization(synchronized)
                completed = synchronized
            } finally { readOwner.finishRead(turn, completed) }
        }
    }

    override suspend fun removeMember(targetDeviceId: String): CommonResponseModel? =
        operations.execute(native = { metaSecretCore.removeMember(targetDeviceId) }) { _, response ->
            parseDeviceRemovalResponse(response)
        }

    override suspend fun approveRemoveMember(targetDeviceId: String): CommonResponseModel? =
        operations.execute(native = { metaSecretCore.approveRemoveMember(targetDeviceId) }) { _, response ->
            parseDeviceRemovalResponse(response)
        }

    override suspend fun declineRemoveMember(targetDeviceId: String): CommonResponseModel? =
        operations.execute(native = { metaSecretCore.declineRemoveMember(targetDeviceId) }) { _, response ->
            parseDeviceRemovalResponse(response)
        }

    private fun parseDeviceRemovalResponse(response: String): CommonResponseModel? {
        return try {
            CommonResponseModel.fromJson(response)
        } catch (e: Exception) {
            logger.log(LogTag.AppManager.Message.FailedToParseUpdateCandidateJson, "${e.message}", success = false)
            null
        }
    }

    override suspend fun getUserDataBy(deviceId: String): UserData? = stateReader.getUserDataBy(deviceId)

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
        return operations.execute(native = {
            metaSecretCore.splitSecret(secretModel.secretName, secretModel.secret)
        }) { expected, splitResult ->
            try {
                val result = CommonResponseModel.fromJson(splitResult)
                logger.log(LogTag.AppManager.Message.SplitSecretResult, "success=${result.success}", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseSplitSecretJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun findClaim(secretId: String): ClaimModel? {
        logger.log(LogTag.AppManager.Message.FindClaimStarted, success = true)
        if (!_isAppManagerInitialized) {
            return null
        }
        
        return operations.execute(native = {
            metaSecretCore.findClaim(secretId)
        }) { expected, searchResult ->
            try {
                val result = SearchClaimModel.fromJson(searchResult)
                if (result.claim == null) {
                    null
                } else {
                    result.claim
                }
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseFindClaimJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun recover(secretModel: SecretModel): CommonResponseModel? {
        if (secretModel.secretName == null) {
            logger.log(LogTag.AppManager.Message.RecoverSecretIdNull, success = true)
            return null
        }
        return operations.execute(native = {
            metaSecretCore.recover(secretModel.secretName)
        }) { expected, recoverRequestResult ->
            try {
                val result = CommonResponseModel.fromJson(recoverRequestResult)
                logger.log(LogTag.AppManager.Message.RecoverRequestResult, "success=${result.success}", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseRecoverRequestJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun acceptRecover(claimId: String?): AppStateModel? {
        logger.log(LogTag.AppManager.Message.AcceptRecoverStarted, success = true)
        if (claimId == null) {
            return null
        }
        return operations.execute(native = {
            metaSecretCore.acceptRecover(claimId)
        }) { expected, acceptResult ->
            try {
                val result = AppStateModel.fromJson(acceptResult, logger, logFormatter)
                logger.log(LogTag.AppManager.Message.AcceptRecoverResult, "success=${result.success}", success = true)
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseAcceptRecoverJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun declineRecover(claimId: String?): AppStateModel? {
        logger.log(LogTag.AppManager.Message.DeclineRecoverStarted, success = true)
        if (claimId == null) {
            return null
        }
        return operations.execute(native = {
            metaSecretCore.declineRecover(claimId)
        }) { expected, declineResult ->
            try {
                val result = AppStateModel.fromJson(declineResult, logger, logFormatter)
                logger.log(
                    LogTag.AppManager.Message.DeclineRecoverResult,
                    "success=${result.success}",
                    success = result.success
                )
                if (!result.success) {
                    throw IllegalStateException("Decline recovery request was not accepted by core")
                }
                result
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseDeclineRecoverJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun sendDeclineCompletion(claimId: String?) {
        if (claimId == null) return
        operations.execute(native = { metaSecretCore.sendDeclineCompletion(claimId) }) { _, _ -> Unit }
    }

    override suspend fun showRecovered(secretModel: SecretModel): String? {
        logger.log(LogTag.AppManager.Message.ShowRecovered, success = true)
        if (secretModel.secretName == null) {
            return null
        }
        if (!_isAppManagerInitialized) {
            return null
        }
        
        return operations.execute(native = {
            if (secretModel.claimId != null) {
                metaSecretCore.showRecovered(secretModel.claimId)
            } else {
                metaSecretCore.showLocalSecret(secretModel.secretName)
            }
        }) { expected, showRecoveredResult ->
            try {
                val parsed = RecoveredSecretModel.fromJson(showRecoveredResult)
                logger.log(LogTag.AppManager.Message.ShowRecoveredSuccess,
                    "success=${parsed.success} hasSecret=${parsed.message?.secret != null}", success = true)
                parsed.message?.secret
            } catch (e: Exception) {
                logger.log(LogTag.AppManager.Message.FailedToParseShowRecoveredJson, "${e.message}", success = false)
                notifyOperationError(expected, e)
                null
            }
        }
    }

    override suspend fun getSecretsFromVault(isSocketAction: Boolean): List<models.apiModels.SecretApiModel>? =
        stateReader.getSecretsFromVault(isSocketAction)
}

enum class AuthState {
    COMPLETED,
    NOT_YET_COMPLETED
}
