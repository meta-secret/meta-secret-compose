package core

import com.metasecret.core.MetaSecretNative
import android.content.Context
import android.util.Log
import android.system.Os
import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import models.apiModels.UserData
import org.koin.java.KoinJavaComponent.inject
import core.metaSecretCore.MetaSecretCoreInterface
import core.LogFormatterInterface
import java.io.File
import metasecret.project.com.BuildConfig

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {

    private fun e2eLog(message: String) {
        Log.i("MetaSecretE2E", "E2E: $message")
    }

    private interface LibC : Library {
        fun chdir(path: String): Int
    }

    private val context: Context by inject(Context::class.java)
private val logger: DebugLoggerInterface by inject(DebugLoggerInterface::class.java)
    private val logFormatter: LogFormatterInterface by inject(LogFormatterInterface::class.java)
    private val clientDeviceInfoProvider: ClientDeviceInfoProviderInterface by inject(ClientDeviceInfoProviderInterface::class.java)
    
    companion object {
        private var loggerInstance: DebugLoggerInterface? = null
        private var logFormatterInstance: LogFormatterInterface? = null
        @Volatile
        private var workingDirectoryConfigured: Boolean = false
        private val libc: LibC by lazy { Native.load("c", LibC::class.java) as LibC }
        
        fun setLogger(logger: DebugLoggerInterface) {
            loggerInstance = logger
        }
        
        fun setLogFormatter(logFormatter: LogFormatterInterface) {
            logFormatterInstance = logFormatter
        }
        
        init {
            try {
                System.setProperty("uniffi.component.mobile_uniffi.libraryOverride", "metasecret_mobile")
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
        configureE2eServerOverride()
        ensureWritableWorkingDirectory()
    }

    private fun configureE2eServerOverride() {
        val endpoint = BuildConfig.META_SECRET_E2E_SERVER_URL.trim()
        if (endpoint.isNotEmpty()) {
            runCatching { Os.setenv("METASECRET_E2E_SERVER_URL", endpoint, true) }
                .onFailure { error ->
                    Log.w("MetaSecretE2E", "Unable to configure E2E server override: ${error.message}")
                }
            e2eLog("ANDROID_E2E_SERVER_OVERRIDE=$endpoint")
        }
    }

    private fun ensureWritableWorkingDirectory() {
        if (workingDirectoryConfigured) return

        synchronized(MetaSecretCoreServiceAndroid::class.java) {
            if (workingDirectoryConfigured) return

            val targetDir = context.noBackupFilesDir ?: context.filesDir
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw IllegalStateException("Failed to create writable directory for native DB: ${targetDir.absolutePath}")
            }
            if (!targetDir.canWrite()) {
                throw IllegalStateException("Native DB directory is not writable: ${targetDir.absolutePath}")
            }

            val nativeDatabaseDir = File(targetDir, "database")
            if (!nativeDatabaseDir.exists() && !nativeDatabaseDir.mkdirs()) {
                throw IllegalStateException("Failed to create native database directory: ${nativeDatabaseDir.absolutePath}")
            }

            val result = libc.chdir(targetDir.absolutePath)
            if (result != 0) {
                throw IllegalStateException(
                    "Failed to set native working directory to ${targetDir.absolutePath}, errno=${Native.getLastError()}"
                )
            }
            System.setProperty("user.dir", targetDir.absolutePath)
            System.setProperty("user.home", targetDir.absolutePath)
            runCatching { Os.setenv("HOME", targetDir.absolutePath, true) }
            runCatching { Os.setenv("TMPDIR", context.cacheDir.absolutePath, true) }
            logger.log(
                LogTag.MetaSecretCoreService.Message.CallingInitAppManager,
                "Configured native working directory: ${targetDir.absolutePath}",
                success = true
            )
            workingDirectoryConfigured = true
        }
    }

    override fun generateMasterKey(): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateMasterKey, success = true)
            val masterKey = MetaSecretNative.generateMasterKey()
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerated, success = true)
            return masterKey
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.MasterKeyGenerationError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun initAppManager(masterKey: String): String {
        try {
            val clientDeviceInfo = clientDeviceInfoProvider.current()
            logger.log(
                LogTag.MetaSecretCoreService.Message.CallingInitAppManager,
                "deviceName=${clientDeviceInfo.deviceName}; deviceType=${clientDeviceInfo.deviceType}",
                success = true
            )
            val result = MetaSecretNative.initWithDevice(
                masterKey = masterKey,
                deviceName = clientDeviceInfo.deviceName,
                deviceType = clientDeviceInfo.deviceType,
            )
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitResult, success = true)
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
            
            logger.log(LogTag.MetaSecretCoreService.Message.AppStateResult, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.AppManagerInitError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun stateEventsAuthToken(vaultName: String): String {
        val result = MetaSecretNative.stateEventsAuthToken(vaultName)
        if (!result.contains("\"success\":true")) {
            throw IllegalStateException("Failed to obtain state events authorization: $result")
        }
        val marker = "\"message\":\""
        val start = result.indexOf(marker)
        if (start < 0) throw IllegalStateException("State events authorization token is missing")
        val tokenStart = start + marker.length
        val tokenEnd = result.indexOf('"', tokenStart)
        if (tokenEnd < 0) throw IllegalStateException("State events authorization token is malformed")
        return result.substring(tokenStart, tokenEnd)
    }

    override fun generateUserCreds(vaultName: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingGenerateUserCreds, success = true)
            val result = MetaSecretNative.generate_user_creds(vaultName)
            logger.log(LogTag.MetaSecretCoreService.Message.GenerateUserCredsResult, success = true)
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
            logger.log(LogTag.MetaSecretCoreService.Message.SignUpResult, success = true)
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
                    put("deviceType", JsonPrimitive(candidate.device.deviceType))
                    putJsonObject("keys") {
                        put("dsaPk", JsonPrimitive(candidate.device.keys.dsaPk))
                        put("transportPk", JsonPrimitive(candidate.device.keys.transportPk))
                    }
                }
            }
            
            val userDataJson = jsonObject.toString()
            logger.log(LogTag.MetaSecretCoreService.Message.FormattedUserDataJson, success = true)

            if (actionUpdate.isBlank()) {
                throw IllegalArgumentException("actionUpdate cannot be blank")
            }
            val jsonActionUpdate = "\"" + actionUpdate.lowercase() + "\""
            logger.log(LogTag.MetaSecretCoreService.Message.FormattedActionUpdate, success = true)
            
            val result = MetaSecretNative.update_membership(userDataJson, jsonActionUpdate)
            logger.log(LogTag.MetaSecretCoreService.Message.UpdateMembershipResult, success = true)
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
            logger.log(LogTag.MetaSecretCoreService.Message.SplitSecretResult, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.SplitSecretError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun findClaim(secretId: String): String {
        try {
            e2eLog("ANDROID_NATIVE_FIND_CLAIM_START secret=$secretId")
            logger.log(LogTag.MetaSecretCoreService.Message.CallingFindClaim, success = true)
            val result = MetaSecretNative.find_claim_by_(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.FindClaimResult, success = true)
            e2eLog("ANDROID_NATIVE_FIND_CLAIM_RESULT secret=$secretId bytes=${result.length}")
            return result
        } catch (e: Exception) {
            e2eLog("ANDROID_NATIVE_FIND_CLAIM_ERROR secret=$secretId error=${e.message}")
            logger.log(LogTag.MetaSecretCoreService.Message.FindClaimError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun recover(secretId: String): String {
        try {
            e2eLog("ANDROID_NATIVE_RECOVER_START secret=$secretId")
            logger.log(LogTag.MetaSecretCoreService.Message.CallingRecover, success = true)
            val result = MetaSecretNative.recover(secretId)
            logger.log(LogTag.MetaSecretCoreService.Message.RecoverResult, success = true)
            e2eLog("ANDROID_NATIVE_RECOVER_RESULT secret=$secretId bytes=${result.length}")
            return result
        } catch (e: Exception) {
            e2eLog("ANDROID_NATIVE_RECOVER_ERROR secret=$secretId error=${e.message}")
            logger.log(LogTag.MetaSecretCoreService.Message.RecoverError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun acceptRecover(claimId: String): String {
        try {
            e2eLog("ANDROID_NATIVE_ACCEPT_RECOVER_START claim=$claimId")
            logger.log(LogTag.MetaSecretCoreService.Message.CallingAcceptRecover, success = true)
            val result = MetaSecretNative.acceptRecover(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.AcceptRecoverResult, success = true)
            e2eLog("ANDROID_NATIVE_ACCEPT_RECOVER_RESULT claim=$claimId bytes=${result.length}")
            return result
        } catch (e: Exception) {
            e2eLog("ANDROID_NATIVE_ACCEPT_RECOVER_ERROR claim=$claimId error=${e.message}")
            logger.log(LogTag.MetaSecretCoreService.Message.AcceptRecoverError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun declineRecover(claimId: String): String {
        try {
            logger.log(LogTag.MetaSecretCoreService.Message.CallingDeclineRecover, success = true)
            val result = MetaSecretNative.declineRecover(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.DeclineRecoverResult, success = true)
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
            logger.log(LogTag.MetaSecretCoreService.Message.SendDeclineCompletionResult, success = true)
            return result
        } catch (e: Exception) {
            logger.log(LogTag.MetaSecretCoreService.Message.SendDeclineCompletionError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun showRecovered(claimId: String): String {
        try {
            e2eLog("ANDROID_NATIVE_SHOW_RECOVERED_START claim=$claimId")
            logger.log(LogTag.MetaSecretCoreService.Message.CallingShowRecovered, success = true)
            val result = MetaSecretNative.showRecovered(claimId)
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredResult, success = true)
            e2eLog("ANDROID_NATIVE_SHOW_RECOVERED_RESULT claim=$claimId bytes=${result.length}")
            return result
        } catch (e: Exception) {
            e2eLog("ANDROID_NATIVE_SHOW_RECOVERED_ERROR claim=$claimId error=${e.message}")
            logger.log(LogTag.MetaSecretCoreService.Message.ShowRecoveredError, "${e.message}", success = false)
            e.printStackTrace()
            throw e
        }
    }

    override fun showLocalSecret(secretId: String): String {
        try {
            e2eLog("ANDROID_NATIVE_SHOW_LOCAL_SECRET_START secret=$secretId")
            val result = MetaSecretNative.showLocalSecret(secretId)
            e2eLog("ANDROID_NATIVE_SHOW_LOCAL_SECRET_RESULT secret=$secretId bytes=${result.length}")
            return result
        } catch (e: Exception) {
            e2eLog("ANDROID_NATIVE_SHOW_LOCAL_SECRET_ERROR secret=$secretId error=${e.message}")
            throw e
        }
    }


}
