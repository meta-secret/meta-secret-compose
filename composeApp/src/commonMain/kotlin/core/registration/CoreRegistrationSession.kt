package core.registration

import core.AppStateCacheProviderInterface
import core.AppString
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.metaSecretCore.liveboard.NativeKeyResult
import core.metaSecretCore.liveboard.NativeRegistrationLifecycle
import core.metaSecretCore.VaultAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import models.apiModels.AppStateModel
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
    private val registration: NativeRegistrationLifecycle,
) : RegistrationSessionInterface {
    override val changes = MutableSharedFlow<RegistrationSnapshot>(extraBufferCapacity = 1)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current = RegistrationSnapshot()
    private var deviceId: String? = null
    private var observing = false
    private var expectedOwner = registration.capture()
    private var startupResumeEligible = true

    init {
        scope.launch {
            ports.socket.socketActions.collect { signal ->
                if (signal == SocketActionModel.JOIN_REQUEST_ACCEPTED ||
                    signal == SocketActionModel.JOIN_REQUEST_DECLINED
                ) mutex.withLock {
                    if (observing && ownsAttempt()) {
                        val refreshed = canonical()
                        if (refreshed != null && ownsAttempt()) {
                            current = refreshed
                            observing = refreshed.status == RegistrationStatus.Pending
                            changes.emit(current)
                        }
                    }
                }
            }
        }
    }

    // Startup lineage is resolved and claimed by the shared gate before any key read.
    override suspend fun resume(): RegistrationSnapshot = mutex.withLock {
        val result = registration.resume(expectedOwner, startupResumeEligible) ?: return@withLock failure()
        if (!adopt(result, bindsAttempt = result.value != null || result.failure != null)) return@withLock failure()
        result.failure?.let { if (it is CancellationException) throw it else return@withLock failure() }
        val snapshot = result.value ?: return@withLock current
        if (!snapshot.success) return@withLock failure()
        val restored = mapSnapshot(snapshot) ?: return@withLock failure()
        if (!ownsAttempt()) return@withLock failure()
        current = restored
        deviceId = snapshot.getCurrentDeviceId()
        if (current.status == RegistrationStatus.Prepared || current.status == RegistrationStatus.Declined) {
            if (!ports.resolver.restorePreparedSignUp() || !ownsAttempt()) return@withLock failure()
        }
        if (current.status == RegistrationStatus.Pending) observe()
        if (ownsAttempt()) current else failure()
    }

    override suspend fun prepare(email: String): RegistrationSnapshot = mutex.withLock {
        if (!ownsAttempt()) return@withLock failure()
        if (current.email == email) {
            if (current.status == RegistrationStatus.Prepared) return@withLock current
            if (current.status == RegistrationStatus.Pending) return@withLock current.copy(status =
                RegistrationStatus.Prepared)
            if (current.status == RegistrationStatus.Declined && ports.resolver.restorePreparedSignUp() &&
                ownsAttempt()) {
                current = current.copy(status = RegistrationStatus.Prepared)
                return@withLock current
            }
        }
        if (current.status == RegistrationStatus.Ready) return@withLock current
        stopObserving()
        try {
            if (!ownsAttempt()) return@withLock failure()
            ports.socket.pauseRefreshesAndAwait()
            if (!ownsAttempt()) return@withLock failure()
            val result = registration.prepare(expectedOwner, email) ?: return@withLock failure()
            if (!adopt(result, bindsAttempt = true)) return@withLock failure()
            val prepared = result.value
            val snapshot = prepared?.snapshot
            if (prepared?.memberPreserved == true && snapshot != null) {
                current = mapSnapshot(snapshot) ?: return@withLock failure()
                return@withLock current
            }
            if (prepared?.cleared == true) {
                if (!forgetAttempt()) return@withLock failure()
                current = RegistrationSnapshot(email)
            }
            result.failure?.let { if (it is CancellationException) throw it }
            if (prepared?.availability == null || snapshot?.success != true || snapshot.getCurrentVaultName() !=
                email) {
                return@withLock failure()
            }
            if (!ownsAttempt()) return@withLock failure()
            deviceId = snapshot.getCurrentDeviceId()
            current = RegistrationSnapshot(email, prepared.availability, RegistrationStatus.Prepared)
            if (!ports.resolver.restorePreparedSignUp() || !ownsAttempt()) return@withLock failure()
            current
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure() }
        finally { if (ownsAttempt()) ports.socket.resumeRefreshes() }
    }

    override suspend fun submit(join: Boolean): RegistrationSnapshot = mutex.withLock {
        if (!ownsAttempt()) return@withLock failure()
        if (current.status == RegistrationStatus.Pending) {
            observe()
            return@withLock if (ownsAttempt()) current else failure()
        }
        if (current.status == RegistrationStatus.Ready) return@withLock current
        if (current.status != RegistrationStatus.Prepared) return@withLock failure()
        if (join && current.availability != VaultAvailability.EXISTS) return@withLock failure(AppString
            .orbitVaultNotFound, true)
        if (!join && current.availability != VaultAvailability.AVAILABLE) return@withLock failure()
        try {
            val result = registration.submit(expectedOwner, current.email) ?: return@withLock failure()
            if (!adopt(result, bindsAttempt = true)) return@withLock failure()
            result.failure?.let { if (it is CancellationException) throw it }
            val submitted = result.value
            val snapshot = submitted?.snapshot
            val refreshed = if (snapshot?.success == true && snapshot.getCurrentVaultName() == current.email &&
                (deviceId == null || snapshot.getCurrentDeviceId() == deviceId)) mapSnapshot(snapshot) else null
            if (!ownsAttempt()) return@withLock failure()
            if (refreshed != null && refreshed.status in setOf(RegistrationStatus.Ready, RegistrationStatus.Pending,
                RegistrationStatus.Declined)) {
                current = refreshed
                if (current.status == RegistrationStatus.Ready) ports.resolver.clearPreparedSignUp()
                if (!ownsAttempt()) return@withLock failure()
                if (current.status == RegistrationStatus.Pending) observe()
                return@withLock if (ownsAttempt()) current else failure()
            }
            val error = if (submitted?.errorMessage?.contains("at most 3 devices", true) == true) AppString
                .vaultDeviceLimitReached
                else AppString.orbitNetworkError
            failure(error, refreshed?.status == RegistrationStatus.Prepared)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure() }
    }

    override suspend fun cleanupIncomplete(): RegistrationSnapshot = mutex.withLock {
        if (!ownsAttempt()) return@withLock failure(AppString.orbitResetFailed)
        if (current.status !in setOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
            return@withLock if (current.status == RegistrationStatus.Empty) failure(AppString.orbitResetFailed
                ) else current
        }
        val name = current.email
        val device = deviceId
        stopObserving()
        try {
            if (!ownsAttempt()) return@withLock failure(AppString.orbitResetFailed)
            ports.socket.pauseRefreshesAndAwait()
            if (!ownsAttempt()) return@withLock failure(AppString.orbitResetFailed)
            val result = registration.cleanupIncomplete(expectedOwner, name, device) ?: return@withLock failure(
                AppString.orbitResetFailed)
            if (!adopt(result, bindsAttempt = true)) return@withLock failure(AppString.orbitResetFailed)
            result.failure?.let { if (it is CancellationException) throw it }
            val cleaned = result.value ?: return@withLock failure(AppString.orbitResetFailed)
            val snapshot = cleaned.snapshot ?: return@withLock failure(AppString.orbitResetFailed)
            val refreshed = mapSnapshot(snapshot) ?: return@withLock failure(AppString.orbitResetFailed)
            if (!ownsAttempt()) return@withLock failure(AppString.orbitResetFailed)
            current = refreshed
            if (cleaned.cleared) {
                if (!forgetAttempt()) return@withLock failure(AppString.orbitResetFailed)
            } else if (current.status in setOf(RegistrationStatus.Pending, RegistrationStatus.Declined)) {
                return@withLock failure(AppString.orbitResetFailed)
            }
            current
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure(AppString.orbitResetFailed) }
        finally { if (ownsAttempt()) ports.socket.resumeRefreshes() }
    }

    override suspend fun reset(): Boolean = mutex.withLock {
        if (!ownsAttempt()) return@withLock false
        stopObserving()
        if (!ownsAttempt()) return@withLock false
        ports.socket.pauseRefreshesAndAwait()
        try {
            if (!ownsAttempt()) return@withLock false
            val result = registration.observe(expectedOwner) ?: return@withLock false
            if (!adopt(result, bindsAttempt = false) || result.failure != null) return@withLock false
            val snapshot = result.value
            if (snapshot == null) {
                current = RegistrationSnapshot(current.email)
                return@withLock true
            }
            if (!snapshot.success) return@withLock false
            val refreshed = if (snapshot.getCurrentAppState() is State.Local) RegistrationSnapshot(current.email) else {
                if (snapshot.getCurrentVaultName() != current.email) return@withLock false
                if (deviceId != null && snapshot.getCurrentDeviceId() != deviceId) return@withLock false
                mapSnapshot(snapshot) ?: return@withLock false
            }
            if (!ownsAttempt()) return@withLock false
            current = refreshed
            // Back stops local observation; it does not revoke JOIN or erase its identity.
            current.status != RegistrationStatus.Ready
        } finally { if (ownsAttempt()) ports.socket.resumeRefreshes() }
    }

    private fun ownsAttempt(): Boolean = registration.isCurrent(expectedOwner)
    private fun adopt(result: NativeKeyResult<*>, bindsAttempt: Boolean): Boolean {
        if (!registration.isCurrent(result.owner)) return false
        expectedOwner = result.owner
        if (bindsAttempt) startupResumeEligible = false
        return true
    }
    private fun stopObserving() {
        observing = false
        if (ownsAttempt()) ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
    }
    private fun forgetAttempt(): Boolean {
        if (!ownsAttempt()) return false
        ports.resolver.clearPreparedSignUp()
        if (!ownsAttempt()) return false
        // Preserve a new current-owner frame published by an inline retirement callback.
        if (local.cache.canonicalFrames.value?.scope?.owner !== expectedOwner) {
            local.storage.cachedDeviceId = null
            if (!ownsAttempt()) return false
            local.storage.cachedVaultName = null
            if (!ownsAttempt()) return false
            local.cache.clearCache()
        }
        if (!ownsAttempt()) return false
        deviceId = null
        current = RegistrationSnapshot()
        return true
    }

    private suspend fun canonical(): RegistrationSnapshot? {
        if (!ownsAttempt()) return null
        val snapshot = ports.manager.getStateModel() ?: return null
        if (!ownsAttempt()) return null
        if (!snapshot.success || snapshot.getCurrentVaultName() != current.email) return null
        if (deviceId != null && snapshot.getCurrentDeviceId() != deviceId) return null
        return mapSnapshot(snapshot)
    }

    private fun mapSnapshot(snapshot: AppStateModel): RegistrationSnapshot? {
        if (!ownsAttempt()) return null
        val email = snapshot.getCurrentVaultName() ?: return null
        return when (snapshot.getVaultFullInfo()) {
            is VaultFullInfo.Member -> {
                local.storage.isOnboardingCompleted = true
                if (!ownsAttempt()) return null
                RegistrationSnapshot(email, status = RegistrationStatus.Ready)
            }
            is VaultFullInfo.NotExists -> RegistrationSnapshot(email, VaultAvailability.AVAILABLE,
                RegistrationStatus.Prepared)
            is VaultFullInfo.Outsider -> RegistrationSnapshot(email, VaultAvailability.EXISTS, when (snapshot
                .getOutsiderStatus()) {
                UserDataOutsiderStatus.PENDING -> RegistrationStatus.Pending
                UserDataOutsiderStatus.DECLINED -> RegistrationStatus.Declined
                else -> RegistrationStatus.Prepared
            })
            null -> null
        }
    }

    private fun observe() {
        if (!ownsAttempt()) return
        observing = true
        ports.socket.actionsToFollow(listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), null)
        if (ownsAttempt()) ports.socket.onAppForeground()
    }

    private fun failure(error: AppString = AppString.orbitNetworkError, reconciled: Boolean = false) =
        current.copy(status = RegistrationStatus.Failed, error = error, reconciledFailure = reconciled)

    override fun stop() {
        observing = false
        if (ownsAttempt()) ports.socket.actionsToFollow(null, listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE))
        scope.cancel()
    }
}
