package core

import com.metasecret.core.MetaSecretNative
import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.UserData
import org.koin.java.KoinJavaComponent.inject
import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {

    private val context: Context by inject(Context::class.java)
    private val databasePathProvider: DatabasePathProviderInterface by inject(DatabasePathProviderInterface::class.java)
    private val logger: DebugLoggerInterface by inject(DebugLoggerInterface::class.java)
    
    companion object {
        private var loggerInstance: DebugLoggerInterface? = null
        
        fun setLogger(logger: DebugLoggerInterface) {
            loggerInstance = logger
        }
        
        init {
            try {
                System.loadLibrary("metasecret_mobile")
                loggerInstance?.log(LogTag.MetaSecretCoreService.Message.LibraryLoaded, success = true)
                    ?: println("Metasecret_mobile library has been loaded successfully")
            } catch (e: Exception) {
                loggerInstance?.log(LogTag.MetaSecretCoreService.Message.LibraryLoadError, "${e.message}", success = false)
                    ?: println("Error during loading of the Metasecret_mobile library: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    init {
        setLogger(logger)
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
            val result = MetaSecretNative.initAppManager(masterKey)
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
            val result = MetaSecretNative.getAppState()
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
            val result = MetaSecretNative.generateUserCreds(vaultName)
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
            
            val result = MetaSecretNative.updateMembership(userDataJson, jsonActionUpdate)
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
            val result = MetaSecretNative.splitSecret(secretName,secret)
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
            val result = MetaSecretNative.findClaim(secretId)
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
                val dbFileName = databasePathProvider.getDatabaseFileName()
                val dbFile = File(context.getDatabasePath(dbFileName).path)
                if (dbFile.exists()) {
                    val deleted = dbFile.delete()
                    MetaSecretNative.cleanUpDatabase()
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