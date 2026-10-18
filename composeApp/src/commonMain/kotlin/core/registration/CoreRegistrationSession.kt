package core.registration

import core.AppStateCacheProviderInterface
import core.AppString
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.metaSecretCore.VaultAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import models.apiModels.AppStateModel
import models.apiModels.MasterKeyModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class RegistrationCorePorts(
    val core: MetaSecretCoreInterface,
    val manager: MetaSecretAppManagerInterface,
    val resolver: MetaSecretStateResolverInterface,
    val socket: MetaSecretSocketHandlerInterface,
)

class RegistrationStoragePorts(
    val keys: KeyChainInterface,
    val storage: KeyValueStorageInterface,
    val cache: AppStateCacheProviderInterface,
)

/** Serializes a single device's unregistered attempt using existing Core ports. */
class CoreRegistrationSession(
    private val ports: RegistrationCorePorts,
    private val local: RegistrationStoragePorts,
) : RegistrationSessionInterface {
    override val changes = MutableSharedFlow<RegistrationSnapshot>(extraBufferCapacity = 1)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current = RegistrationSnapshot()
    private var deviceId: String? = null
    private var observing = false

    init {
        scope.launch {
            ports.socket.socketActions.collect { signal ->
                if (signal == SocketActionModel.JOIN_REQUEST_ACCEPTED ||
                    signal == SocketActionModel.JOIN_REQUEST_DECLINED
                ) mutex.withLock {
                    if (observing) {
                        val refreshed = canonical()
                        if (refreshed != null) {
                            current = refreshed
                            observing = refreshed.status == RegistrationStatus.Pending
                            changes.emit(current)
                        }
                    }
                }
            }
        }
    }

    override suspend fun resume(): RegistrationSnapshot = mutex.withLock {
        if (!local.keys.containsKey("master_key")) return@withLock current
        if (ports.manager.initWithSavedKey() !is InitResult.Success) return@withLock failure()
        val snapshot = ports.manager.getStateModel() ?: return@withLock failure()
        if (!snapshot.success) return@withLock failure()
        current = mapSnapshot(snapshot) ?: RegistrationSnapshot()
        deviceId = snapshot.getCurrentDeviceId()
        if (current.status == RegistrationStatus.Prepared || current.status == RegistrationStatus.Declined) {
            if (!ports.resolver.restorePreparedSignUp()) return@withLock failure()
        }
        if (current.status == RegistrationStatus.Pending) observe()
        current
    }

    override suspend fun prepare(email: String): RegistrationSnapshot = mutex.withLock {
        if (current.email == email) {
            if (current.status == RegistrationStatus.Prepared) return@withLock current
            if (current.status == RegistrationStatus.Pending) return@withLock current.copy(status = RegistrationStatus.Prepared)
            if (current.status == RegistrationStatus.Declined && ports.resolver.restorePreparedSignUp()) {
                current = current.copy(status = RegistrationStatus.Prepared)
                return@withLock current
            }
        }
        if (current.status == RegistrationStatus.Ready) return@withLock current
        if (!resetLocked()) return@withLock if (current.status == RegistrationStatus.Ready) current else failure(AppString.orbitResetFailed)
        current = RegistrationSnapshot(email)
        try {
            val master = withContext(Dispatchers.IO) {
                MasterKeyModel.fromJson(ports.core.generateMasterKey())
            }
            if (!master.success || master.masterKey.isNullOrBlank() ||
                !local.keys.saveString("master_key", master.masterKey)
            ) return@withLock failure()
            if (ports.manager.initWithSavedKey() !is InitResult.Success) return@withLock failure()
            val result = ports.resolver.prepareSignUp(email)
            if (result.error != null || result.availability == null) return@withLock failure()
            val snapshot = ports.manager.getStateModel()
            if (snapshot?.success != true || snapshot.getCurrentVaultName() != email) return@withLock failure()
            deviceId = snapshot.getCurrentDeviceId()
            current = RegistrationSnapshot(email, result.availability, RegistrationStatus.Prepared)
            current
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure() }
    }

    override suspend fun submit(join: Boolean): RegistrationSnapshot = mutex.withLock {
        if (current.status == RegistrationStatus.Pending) {
            observe()
            return@withLock current
        }
        if (current.status == RegistrationStatus.Ready) return@withLock current
        if (current.status != RegistrationStatus.Prepared) return@withLock failure()
        if (join && current.availability != VaultAvailability.EXISTS) {
            return@withLock failure(AppString.orbitVaultNotFound, true)
        }
        if (!join && current.availability != VaultAvailability.AVAILABLE) return@withLock failure()
        try {
            // Persistence precedes submission, allowing recovery after an ambiguous response/relaunch.
            if (!local.keys.saveString("pending_vault_email", current.email)) return@withLock failure()
            val result = ports.resolver.continueSignUp()
            val refreshed = canonical()
            if (refreshed != null && refreshed.status in setOf(
                    RegistrationStatus.Ready, RegistrationStatus.Pending, RegistrationStatus.Declined,
                )) {
                current = refreshed
                if (current.status == RegistrationStatus.Pending) observe()
                return@withLock current
            }
            val error = if (result.errorMessage?.contains("at most 3 devices", true) == true) {
                AppString.vaultDeviceLimitReached
            } else AppString.orbitNetworkError
            failure(error, refreshed?.status == RegistrationStatus.Prepared)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val refreshed = canonical()
            if (refreshed?.status == RegistrationStatus.Ready || refreshed?.status == RegistrationStatus.Pending) {
                current = refreshed
                if (current.status == RegistrationStatus.Pending) observe()
                current
            } else failure(reconciled = refreshed?.status == RegistrationStatus.Prepared)
        }
    }

    override suspend fun cleanupIncomplete(): RegistrationSnapshot = mutex.withLock {
        if (current.status !in setOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
            return@withLock if (current.status == RegistrationStatus.Empty) failure(AppString.orbitResetFailed) else current
        }
        val expectedName = current.email
        val expectedDevice = deviceId
        observing = false
        ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
        try {
            ports.socket.pauseRefreshesAndAwait()
            // The live initialized manager must prove the same incomplete attempt; init may itself clear keys.
            if (expectedName.isBlank() || expectedDevice.isNullOrBlank() ||
                local.keys.getString("master_key").isNullOrBlank()
            ) return@withLock failure(AppString.orbitResetFailed)
            val snapshot = ports.manager.getStateModel()
            if (snapshot?.success != true || snapshot.getCurrentVaultName() != expectedName ||
                snapshot.getCurrentDeviceId() != expectedDevice
            ) return@withLock failure(AppString.orbitResetFailed)
            current = mapSnapshot(snapshot) ?: return@withLock failure(AppString.orbitResetFailed)
            if (current.status !in setOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) return@withLock current
            if (!local.keys.clearAll(isCleanDB = true) ||
                listOf("master_key", "pending_vault_email", "pending_email_provider").any { local.keys.containsKey(it) }
            ) return@withLock failure(AppString.orbitResetFailed)
            forgetAttempt()
            current
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure(AppString.orbitResetFailed) }
        finally { ports.socket.resumeRefreshes() }
    }

    override suspend fun reset(): Boolean = mutex.withLock {
        observing = false
        ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
        ports.socket.pauseRefreshesAndAwait()
        try {
            if (!local.keys.containsKey("master_key")) {
                current = RegistrationSnapshot(current.email)
                return@withLock true
            }
            val snapshot = ports.manager.getStateModel() ?: return@withLock false
            if (!snapshot.success) return@withLock false
            val refreshed = if (snapshot.getCurrentAppState() is State.Local) {
                RegistrationSnapshot(current.email)
            } else {
                if (snapshot.getCurrentVaultName() != current.email) return@withLock false
                if (deviceId != null && snapshot.getCurrentDeviceId() != deviceId) return@withLock false
                mapSnapshot(snapshot) ?: return@withLock false
            }
            current = refreshed
            // Back stops local observation; it does not revoke JOIN or erase its identity.
            current.status != RegistrationStatus.Ready
        } finally { ports.socket.resumeRefreshes() }
    }

    private suspend fun resetLocked(): Boolean {
        observing = false
        ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
        ports.socket.pauseRefreshesAndAwait()
        return try {
            // Never clear a Member's key, including acceptance racing the Back button.
            if (local.keys.containsKey("master_key")) {
                if (ports.manager.initWithSavedKey() !is InitResult.Success) return false
                val snapshot = ports.manager.getStateModel()
                if (snapshot?.success != true) return false
                if (snapshot.getVaultFullInfo() is VaultFullInfo.Member) {
                    current = mapSnapshot(snapshot) ?: current
                    return false
                }
            }
            if (!local.keys.clearAll(isCleanDB = true)) return false
            forgetAttempt()
            true
        } finally { ports.socket.resumeRefreshes() }
    }

    private fun forgetAttempt() {
        ports.resolver.clearPreparedSignUp()
        local.storage.cachedDeviceId = null
        local.storage.cachedVaultName = null
        local.cache.clearCache()
        deviceId = null
        current = RegistrationSnapshot()
    }

    private suspend fun canonical(): RegistrationSnapshot? {
        val snapshot = ports.manager.getStateModel() ?: return null
        if (!snapshot.success || snapshot.getCurrentVaultName() != current.email) return null
        if (deviceId != null && snapshot.getCurrentDeviceId() != deviceId) return null
        return mapSnapshot(snapshot)
    }

    private fun mapSnapshot(snapshot: AppStateModel): RegistrationSnapshot? {
        val email = snapshot.getCurrentVaultName() ?: return null
        return when (snapshot.getVaultFullInfo()) {
            is VaultFullInfo.Member -> {
                local.storage.isOnboardingCompleted = true
                RegistrationSnapshot(email, status = RegistrationStatus.Ready)
            }
            is VaultFullInfo.NotExists -> RegistrationSnapshot(email, VaultAvailability.AVAILABLE, RegistrationStatus.Prepared)
            is VaultFullInfo.Outsider -> RegistrationSnapshot(email, VaultAvailability.EXISTS, when (snapshot.getOutsiderStatus()) {
                UserDataOutsiderStatus.PENDING -> RegistrationStatus.Pending
                UserDataOutsiderStatus.DECLINED -> RegistrationStatus.Declined
                else -> RegistrationStatus.Prepared
            })
            null -> null
        }
    }

    private fun observe() {
        observing = true
        ports.socket.actionsToFollow(listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), null)
        ports.socket.onAppForeground()
    }

    private fun failure(error: AppString = AppString.orbitNetworkError, reconciled: Boolean = false) =
        current.copy(status = RegistrationStatus.Failed, error = error, reconciledFailure = reconciled)

    override fun stop() {
        observing = false
        ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
        scope.cancel()
    }
}
