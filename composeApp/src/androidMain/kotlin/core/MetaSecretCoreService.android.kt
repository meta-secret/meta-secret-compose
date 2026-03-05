package core

import com.metasecret.core.MetaSecretNative
import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.UserData
import org.koin.java.KoinJavaComponent.inject
import core.metaSecretCore.MetaSecretCoreInterface
import core.LogFormatterInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {

    private val context: Context by inject(Context::class.java)
    private val databasePathProvider: DatabasePathProviderInterface by inject(DatabasePathProviderInterface::class.java)
    private val logger: DebugLoggerInterface by inject(DebugLoggerInterface::class.java)
    private val logFormatter: LogFormatterInterface by inject(LogFormatterInterface::class.java)
    
    companion object {
        private var loggerInstance: DebugLoggerInterface? = null
        private var logFormatterInstance: LogFormatterInterface? = null
        
        fun setLogger(logger: DebugLoggerInterface) {
            loggerInstance = logger
        }
        
        fun setLogFormatter(logFormatter: LogFormatterInterface) {
            logFormatterInstance = logFormatter
        }
        
        init {
            try {
                System.loadLibrary("metasecret_mobile")
                loggerInstance?.log(LogTag.MetaSecretCoreService.Message.LibraryLoaded, success = true)
                    ?: println(logFormatterInstance?.formatLogMessage("Metasecret_mobile library has been loaded successfully") 
                        ?: "[${System.currentTimeMillis()}] Metasecret_mobile library has been loaded successfully")
            } catch (e: Exception) {
                loggerInstance?.log(LogTag.MetaSecretCoreService.Message.LibraryLoadError, "${e.message}", success = false)
                    ?: println(logFormatterInstance?.formatLogMessage("Error during loading of the Metasecret_mobile library: ${e.message}")
                        ?: "[${System.currentTimeMillis()}] Error during loading of the Metasecret_mobile library: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    init {
        setLogger(logger)
        setLogFormatter(logFormatter)
    }

    override fun generateMasterKey(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateMasterKey, success = true)
            val masterKey = MetaSecretNative.generateMasterKey()
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerated, masterKey, success = true)
            return masterKey
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerationError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun initAppManager(masterKey: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingInitAppManager, "with: $masterKey", success = true)
            val result = MetaSecretNative.init(masterKey)
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun getAppState(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGetState, success = true)
            val result = MetaSecretNative.getState()
            
            if (result.isEmpty()) {
                logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "Empty response from FFI", success = false)
                throw IllegalStateException("Empty response from FFI getState")
            }
            
            if (!result.contains("\"message\"") && !result.contains("\"success\"")) {
                logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "Invalid JSON response from FFI", success = false)
                throw IllegalStateException("Invalid JSON response from FFI getState: $result")
            }
            
            logger.log(LogTag.MetaSecretCoreService.Message.AppStateResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun generateUserCreds(vaultName: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateUserCreds, success = true)
            val result = MetaSecretNative.generate_user_creds(vaultName)
            logger.log(LogTag.MetaSecretCoreService.Message.GenerateUserCredsResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.GenerateUserCredsError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun signUp(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSignUp, success = true)
            val result = MetaSecretNative.signUp()
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
            
            val result = MetaSecretNative.update_membership(userDataJson, jsonActionUpdate)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSplitSecret, success = true)
            val result = MetaSecretNative.split(secretName, secret)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingFindClaim, success = true)
            val result = MetaSecretNative.find_claim_by_(secretId)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingRecover, success = true)
            val result = MetaSecretNative.recover(secretId)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingAcceptRecover, success = true)
            val result = MetaSecretNative.acceptRecover(claimId)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingDeclineRecover, success = true)
            val result = MetaSecretNative.declineRecover(claimId)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingSendDeclineCompletion, success = true)
            val result = MetaSecretNative.sendDeclineCompletion(claimId)
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
            logger.log(LogTag.MetaSecretCoreService.Message.CallingShowRecovered, success = true)
            val result = MetaSecretNative.showRecovered(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredResult, result, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    private fun cleanDB() {
        CoroutineScope(Dispatchers.IO).launch {
            logger.log(LogTag.MetaSecretCoreService.Message.CleanDb, success = true)
            try {
                val dbFileName = databasePathProvider.getDatabaseFileName() ?: return@launch
                val dbFile = File(context.getDatabasePath(dbFileName).path)
                if (dbFile.exists()) {
                    val deleted = dbFile.delete()
                    MetaSecretNative.clean_up_database()
                    logger.log(
                        LogTag.MetaSecretCoreService.Message.DbFileDeleted,
                        "$deleted",
                        success = deleted
                    )
                } else {
                    logger.log(LogTag.MetaSecretCoreService.Message.DbFileNotExist, success = true)
                }
            } catch (e: Exception) {
                logger.log(
                    LogTag.MetaSecretCoreService.Message.ErrorCleaningDb,
                    "${e.message}",
                    success = false
                )
                e.printStackTrace()
            }
        }
    }
}