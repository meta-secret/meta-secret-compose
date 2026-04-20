package testutils

import core.AppStateCacheProviderInterface
import core.BackupCoordinatorInterface
import core.BiometricAuthenticatorInterface
import core.DebugLoggerInterface
import core.Device
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogFormatterInterface
import core.LogTag
import core.LoginInfo
import core.NotificationCoordinatorInterface
import core.NotificationState
import core.ScreenMetricsProviderInterface
import core.Secret
import core.StringProviderInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretCoreInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import models.apiModels.AppStateModel
import models.apiModels.SecretApiModel
import models.apiModels.UserData

class FakeDebugLogger : DebugLoggerInterface {
    var backupDbExists: Boolean? = null
    var appManagerCreated: Boolean? = null
    var masterKeyGenerated: Boolean? = null
    var vaultState: String? = null

    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) = Unit
    override fun setLoggerVisibility() = Unit
    override fun testInfo() = Unit
    override fun setBackupDbExists(exists: Boolean) {
        backupDbExists = exists
    }
    override fun setAppManagerCreated(created: Boolean) {
        appManagerCreated = created
    }
    override fun setMasterKeyGenerated(generated: Boolean) {
        masterKeyGenerated = generated
    }
    override fun setVaultState(state: String?) {
        vaultState = state
    }
    override fun setDeviceId(deviceId: String?) = Unit
    override fun setClaimsStats(
        joinRequestsCount: Int,
        pendingClaimsCount: Int,
        sentClaimsCount: Int,
        deliveredClaimsCount: Int,
    ) = Unit
    override fun setOuterLoggerVisibility(isVisible: Boolean) = Unit
}

class FakeKeyChain(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : KeyChainInterface {
    var clearAllCalls = 0
    var clearAllIsCleanDb: Boolean? = null

    override suspend fun saveString(key: String, value: String): Boolean {
        values[key] = value
        return true
    }

    override suspend fun getString(key: String): String? = values[key]
    override suspend fun removeKey(key: String): Boolean = values.remove(key) != null
    override suspend fun containsKey(key: String): Boolean = values.containsKey(key)

    override suspend fun clearAll(isCleanDB: Boolean): Boolean {
        clearAllCalls += 1
        clearAllIsCleanDb = isCleanDB
        values.clear()
        return true
    }
}

class FakeKeyValueStorage : KeyValueStorageInterface {
    override var isOnboardingCompleted: Boolean = false
    override var signInInfo: LoginInfo? = null
    override var isWarningVisible: Boolean = false
    override var secretData: StateFlow<List<Secret>> = MutableStateFlow(emptyList())
    override var deviceData: StateFlow<List<Device>> = MutableStateFlow(emptyList())
    override var isBiometricEnabled: Boolean = false
    override var cachedDeviceId: String? = null
    override var cachedVaultName: String? = null

    override fun cleanStorage() = Unit
    override fun resetKeyValueStorage() = Unit
    override fun addSecret(secret: Secret) = Unit
    override fun removeSecret(secret: Secret) = Unit

    override fun syncSecretsFromVault(apiSecrets: List<SecretApiModel>) {
        (secretData as MutableStateFlow).value = apiSecrets.map { Secret(secretId = it.id, secretName = it.name) }
    }

    override fun addDevice(device: Device) = Unit
    override fun removeDevice(index: Int) = Unit
}

class FakeNotificationCoordinator : NotificationCoordinatorInterface {
    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Hidden)
    override val notificationState: StateFlow<NotificationState> = _notificationState
    val errorMessages = mutableListOf<String>()
    val successMessages = mutableListOf<String>()

    override fun showError(message: String) {
        errorMessages += message
        _notificationState.value = NotificationState.Visible(message, isError = true)
    }

    override fun showSuccess(message: String) {
        successMessages += message
        _notificationState.value = NotificationState.Visible(message, isError = false)
    }

    override fun dismiss() {
        _notificationState.value = NotificationState.Hidden
    }
}

class FakeLogFormatter : LogFormatterInterface {
    override fun formatLogMessage(message: String): String = message
}

class FakeAppStateCacheProvider : AppStateCacheProviderInterface {
    private val _appState = MutableStateFlow<AppStateModel?>(null)
    override val appState: StateFlow<AppStateModel?> = _appState

    override fun updateCache(state: AppStateModel) {
        _appState.value = state
    }

    override fun clearCache() {
        _appState.value = null
    }
}

class FakeStringProvider : StringProviderInterface {
    override fun biometricTitle() = "title"
    override fun biometricSubtitle() = "subtitle"
    override fun biometricDescription() = "description"
    override fun biometricFallback() = "fallback"
    override fun biometricNotAvailable() = "not available"
    override fun biometricErrorNoHardware() = "no hardware"
    override fun biometricErrorNoEnrolled() = "no enrolled"
    override fun biometricPermissionRequired() = "permission required"
    override fun biometricPromptReason() = "prompt reason"
    override fun biometricPermissionSettings() = "permission settings"
    override fun backupChoosePathMessage() = "backup message"
    override fun backupChoosePathWarning() = "backup warning"
    override fun ok() = "ok"
    override fun errorNetwork() = "network error"
    override fun errorInternal() = "internal error"
    override fun errorParse() = "parse error"
    override fun errorValidation() = "validation error"
    override fun errorUnknownPrefix() = "unknown: "
    override fun errorBiometricAuthFailed() = "biometric failed"
    override fun errorSecretAddFailed() = "secret add failed"
    override fun errorRecoverDeclined() = "recover declined"
    override fun acceptRequestOnOtherDevice() = "approve on other device"
    override fun nameOccupiedJoinPrompt() = "join existing vault"
    override fun recoverPendingExists() = "recover pending exists"
    override fun recoverRequestSent() = "recover request sent"
}

class FakeBiometricAuthenticator : BiometricAuthenticatorInterface {
    override fun isBiometricAvailable(): Boolean = true
    override fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit, onFallback: () -> Unit) = onSuccess()
    override fun openAppSettings() = Unit
}

class FakeScreenMetricsProvider : ScreenMetricsProviderInterface {
    override fun widthFactor(): Float = 1f
    override fun heightFactor(): Float = 1f
    override fun screenWidth(): Int = 100
    override fun screenHeight(): Int = 100
    override fun topSafeAreaInset(): Int = 0
    override fun bottomSafeAreaInset(): Int = 0
}

class FakeVaultStatsProvider : VaultStatsProviderInterface {
    override val secretsCount: StateFlow<Int> = MutableStateFlow(0)
    override val devicesCount: StateFlow<Int> = MutableStateFlow(0)
    override val vaultName: StateFlow<String?> = MutableStateFlow(null)
    override val joinRequestsCount: StateFlow<Int?> = MutableStateFlow(null)
    var refreshCalls = 0

    override suspend fun refresh() {
        refreshCalls += 1
    }
}

class FakeBackupCoordinator(
    private val hasDb: Boolean,
) : BackupCoordinatorInterface {
    var restoreCalls = 0

    override fun ensureBackupDestinationSelected() = Unit

    override suspend fun restoreIfNeeded() {
        restoreCalls += 1
    }

    override suspend fun backupIfChanged() = Unit
    override fun clearAllBackups() = Unit
    override suspend fun hasDatabaseFile(): Boolean = hasDb
}

class FakeMetaSecretCore : MetaSecretCoreInterface {
    var initAppManagerResult: Result<String> = Result.success("ok")

    override fun generateMasterKey(): String = "master-key"
    override fun initAppManager(masterKey: String): String = initAppManagerResult.getOrThrow()
    override fun getAppState(): String = "{}"
    override fun generateUserCreds(vaultName: String): String = vaultName
    override fun signUp(): String = "{}"
    override fun updateMembership(candidate: UserData, actionUpdate: String): String = "{}"
    override fun splitSecret(secretName: String, secret: String): String = "{}"
    override fun findClaim(secretId: String): String = "{}"
    override fun recover(secretId: String): String = "{}"
    override fun acceptRecover(claimId: String): String = "{}"
    override fun declineRecover(claimId: String): String = "{}"
    override fun sendDeclineCompletion(claimId: String): String = "{}"
    override fun showRecovered(secretId: String): String = "{}"
}
