package ui.scenes.registration

import core.AppString
import core.AppleEmailRequesterInterface
import core.GoogleEmailRequesterInterface
import core.metaSecretCore.VaultAvailability
import models.appInternalModels.EmailProvider

enum class RegistrationScene(val step: String) { Providers("01"), Name("02"), Manual("03"), Join("04") }
enum class RegistrationPhase { Idle, Provider, Checking, Creating, CreateError, Requesting, Pending, Syncing,
    Declined, Error }

data class RegistrationIdentity(
    val email: String = "",
    val provider: EmailProvider = EmailProvider.MANUAL,
    val availability: VaultAvailability? = null,
)

data class RegistrationUiState(
    val scene: RegistrationScene = RegistrationScene.Providers,
    val identity: RegistrationIdentity = RegistrationIdentity(),
    val phase: RegistrationPhase = RegistrationPhase.Idle,
    val error: AppString? = null,
    val direction: Int = 1,
) {
    val busy: Boolean get() = phase in setOf(
        RegistrationPhase.Provider, RegistrationPhase.Checking, RegistrationPhase.Creating, RegistrationPhase
            .CreateError,
        RegistrationPhase.Requesting, RegistrationPhase.Syncing,
    )
}

class RegistrationProviders(val apple: AppleEmailRequesterInterface, val google: GoogleEmailRequesterInterface)

sealed interface RegistrationIntent {
    data class Provider(val provider: EmailProvider) : RegistrationIntent
    data class Email(val email: String) : RegistrationIntent
    data class QrDecoded(val email: String) : RegistrationIntent
    data object Manual : RegistrationIntent
    data object ConfirmEmail : RegistrationIntent
    data object Create : RegistrationIntent
    data object Join : RegistrationIntent
    data object Request : RegistrationIntent
    data object Back : RegistrationIntent
}
