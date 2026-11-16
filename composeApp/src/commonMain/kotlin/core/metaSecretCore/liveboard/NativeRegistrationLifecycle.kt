package core.metaSecretCore.liveboard

import core.metaSecretCore.VaultAvailability
import core.metaSecretCore.mapVaultAvailability
import kotlinx.coroutines.CancellationException
import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo

data class NativeRegistrationPreparation(
    val snapshot: AppStateModel?,
    val availability: VaultAvailability?,
    val cleared: Boolean = false,
    val memberPreserved: Boolean = false,
)
data class NativeRegistrationSubmission(val snapshot: AppStateModel?, val errorMessage: String?)
data class NativeRegistrationCleanup(val snapshot: AppStateModel?, val cleared: Boolean)

/** Ordered local registration effects use the same drained native/key reservation. */
class NativeRegistrationLifecycle(private val keyLifecycle: NativeKeyLifecycle) {
    fun capture(): NativeOwnerToken = keyLifecycle.capture()
    fun isCurrent(owner: NativeOwnerToken): Boolean = keyLifecycle.isCurrent(owner)

    suspend fun resume(
        expectedOwner: NativeOwnerToken,
        allowStartupHandoff: Boolean,
    ): NativeKeyResult<AppStateModel?>? = keyLifecycle.registrationResume(expectedOwner, allowStartupHandoff) {
            if (!contains("master_key")) null
            else {
                initializeSaved()
                snapshot()
            }
        }

    /** Back observes local identity without initializing, clearing or revoking JOIN. */
    suspend fun observe(expectedOwner: NativeOwnerToken): NativeKeyResult<AppStateModel?>? =
        keyLifecycle.registration(expectedOwner) { if (contains("master_key")) snapshot() else null }

    suspend fun prepare(
        expectedOwner: NativeOwnerToken,
        vaultName: String,
    ): NativeKeyResult<NativeRegistrationPreparation?>? = keyLifecycle.registration(expectedOwner) {
        if (contains("master_key")) {
            initializeSaved()
            val existing = snapshot()
            if (!existing.success) return@registration NativeRegistrationPreparation(null, null)
            if (existing.getVaultFullInfo() is VaultFullInfo.Member) {
                return@registration NativeRegistrationPreparation(existing, null, memberPreserved = true)
            }
        }
        if (!clear()) return@registration NativeRegistrationPreparation(null, null)
        try {
            val master = generateMasterKey()
            val key = master.masterKey
            if (!master.success || key.isNullOrBlank() || !save("master_key", key)) {
                return@registration NativeRegistrationPreparation(null, null, true)
            }
            initializeKey(key)
            val local = snapshot()
            if (!local.success || local.getCurrentAppState() !is State.Local) {
                return@registration NativeRegistrationPreparation(null, null, true)
            }
            val prepared = generateCredentials(vaultName)
            val availability = if (prepared.success && prepared.getCurrentAppState() is State.Vault) {
                mapVaultAvailability(prepared.getVaultFullInfo())
            } else null
            NativeRegistrationPreparation(prepared, availability, true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NativeRegistrationPreparation(null, null, true)
        }
    }

    suspend fun submit(
        expectedOwner: NativeOwnerToken,
        vaultName: String,
    ): NativeKeyResult<NativeRegistrationSubmission?>? = keyLifecycle.registration(expectedOwner) {
        if (!save("pending_vault_email", vaultName)) return@registration NativeRegistrationSubmission(null, null)
        var message: String? = null
        try {
            message = signUp().error
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A lost response does not authorize a second submission; observe once under this permit.
        }
        NativeRegistrationSubmission(snapshot(), message)
    }

    suspend fun cleanupIncomplete(
        expectedOwner: NativeOwnerToken,
        vaultName: String,
        deviceId: String?,
    ): NativeKeyResult<NativeRegistrationCleanup?>? = keyLifecycle.registration(expectedOwner) {
        if (vaultName.isBlank() || deviceId.isNullOrBlank() || savedKey().isNullOrBlank()) {
            return@registration NativeRegistrationCleanup(null, false)
        }
        val existing = snapshot()
        if (!existing.success || existing.getCurrentVaultName() != vaultName || existing.getCurrentDeviceId() !=
            deviceId) {
            return@registration NativeRegistrationCleanup(null, false)
        }
        val incomplete = existing.getOutsiderStatus() in setOf(
            UserDataOutsiderStatus.PENDING, UserDataOutsiderStatus.DECLINED,
        )
        if (!incomplete) return@registration NativeRegistrationCleanup(existing, false)
        val cleared = clear() && listOf("master_key", "pending_vault_email", "pending_email_provider").none {
            contains(it) }
        NativeRegistrationCleanup(existing, cleared)
    }
}
