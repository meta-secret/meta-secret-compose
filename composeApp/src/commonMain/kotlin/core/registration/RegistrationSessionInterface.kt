package core.registration

import core.AppString
import core.metaSecretCore.VaultAvailability
import kotlinx.coroutines.flow.Flow

interface RegistrationSessionInterface {
    val changes: Flow<RegistrationSnapshot>
    suspend fun resume(): RegistrationSnapshot
    suspend fun prepare(email: String): RegistrationSnapshot
    suspend fun submit(join: Boolean): RegistrationSnapshot
    suspend fun cleanupIncomplete(): RegistrationSnapshot
    suspend fun reset(): Boolean
    fun stop()
}

/** Presentation results of existing Core operations, never a membership authority. */
enum class RegistrationStatus { Empty, Prepared, Pending, Declined, Ready, Failed }

data class RegistrationSnapshot(
    val email: String = "",
    val availability: VaultAvailability? = null,
    val status: RegistrationStatus = RegistrationStatus.Empty,
    val error: AppString? = null,
    val reconciledFailure: Boolean = false,
)
