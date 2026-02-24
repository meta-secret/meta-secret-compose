package core

import kotlinx.cinterop.ExperimentalForeignApi
import core.metaSecretCore.MetaSecretCoreInterface
import com.metaSecret.ios.SwiftBridge
import models.apiModels.UserData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalForeignApi::class)
class MetaSecretCoreServiceIos(
    private val logger: DebugLoggerInterface
): MetaSecretCoreInterface {
    private val swiftBridge = SwiftBridge()

    @OptIn(ExperimentalForeignApi::class)
    override fun generateMasterKey(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateMasterKey, success = true)
            val masterKey = swiftBridge.generateMasterKey()
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerated, masterKey, success = true)
            return masterKey
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerationError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun initAppManager(masterKey: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingInitAppManager, "with: $masterKey", success = true)
            val result = swiftBridge.initWithMasterKey(masterKey)
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getAppState(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGetState, success = true)
            val result = swiftBridge.getState()
            
            if (result.isEmpty()) {
                logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "Empty response from FFI", success = false)
                throw IllegalStateException("Empty response from FFI getState")
            }
            
            logger.log(LogTag.MetaSecretCoreService.Message.AppStateResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun generateUserCreds(vaultName: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateUserCreds, success = true)
            val result = swiftBridge.generateUserCredsWithVaultName(vaultName)
            logger.log(LogTag.MetaSecretCoreService.Message.GenerateUserCredsResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.GenerateUserCredsError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun signUp(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSignUp, success = true)
            val result = swiftBridge.signUp()
            logger.log(LogTag.MetaSecretCoreService.Message.SignUpResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.SignUpError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun updateMembership(candidate: UserData, actionUpdate: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingUpdateMembership, success = true)
            val jsonObject = buildJsonObject {
                put("vaultName", JsonPrimitive(candidate.vaultName))
                putJsonObject("device") {
                    put("deviceId", JsonPrimitive(candidate.device.deviceId))
                    put("deviceName", JsonPrimitive(candidate.device.deviceName))
                    putJsonObject("keys") {
                        put("dsaPk", JsonPrimitive(candidate.device.keys.dsaPk))
                        put("transportPk", JsonPrimitive(candidate.device.keys.transportPk))
                    }
                }
            }
            val userDataJson = jsonObject.toString()
            logger.log(LogTag.MetaSecretCoreService.Message.FormattedUserDataJson, userDataJson, success = true)

            if (actionUpdate.isBlank()) {
                throw IllegalArgumentException("actionUpdate cannot be blank")
            }
            val jsonActionUpdate = "\"" + actionUpdate.lowercase() + "\""
            logger.log(LogTag.MetaSecretCoreService.Message.FormattedActionUpdate, jsonActionUpdate, success = true)

            val result = swiftBridge.updateMembership(userDataJson, jsonActionUpdate)
            logger.log(LogTag.MetaSecretCoreService.Message.UpdateMembershipResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.UpdateMembershipError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun splitSecret(secretName: String, secret: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSplitSecret, "with: $secretName", success = true)
            val result = swiftBridge.splitSecret(secretName, secret)
            logger.log(LogTag.MetaSecretCoreService.Message.SplitSecretResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.SplitSecretError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }
    override fun findClaim(secretId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingFindClaim, "with: $secretId", success = true)
            val result = swiftBridge.findClaim(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.FindClaimResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.FindClaimError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun recover(secretId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingRecover, "with: $secretId", success = true)
            val result = swiftBridge.recover(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.RecoverResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.RecoverError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun acceptRecover(claimId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingAcceptRecover, "with: $claimId", success = true)
            val result = swiftBridge.acceptRecover(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.AcceptRecoverResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AcceptRecoverError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun declineRecover(claimId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingDeclineRecover, "with: $claimId", success = true)
            val result = swiftBridge.declineRecover(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.DeclineRecoverResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.DeclineRecoverError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun sendDeclineCompletion(claimId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSendDeclineCompletion, "with: $claimId", success = true)
            val result = swiftBridge.sendDeclineCompletion(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.SendDeclineCompletionResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.SendDeclineCompletionError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun showRecovered(secretId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingShowRecovered, "with: $secretId", success = true)
            val result = swiftBridge.showRecovered(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }
}