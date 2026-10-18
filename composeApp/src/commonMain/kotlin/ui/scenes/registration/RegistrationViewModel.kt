package ui.scenes.registration

import kotlinx.coroutines.cancel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.AppString
import core.AppleEmailAuthResult
import core.GoogleEmailAuthResult
import core.metaSecretCore.VaultAvailability
import core.registration.RegistrationSessionInterface
import core.registration.RegistrationSnapshot
import core.registration.registrationQrName
import core.registration.RegistrationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.appInternalModels.EmailProvider
import models.appInternalModels.isValidEmail

class RegistrationViewModel(
    private val session: RegistrationSessionInterface,
    private val providers: RegistrationProviders,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegistrationUiState())
    val state = mutableState.asStateFlow()
    private val mutableOpenMain = MutableStateFlow(false)
    val openMain = mutableOpenMain.asStateFlow()
    private var operation = false
    private var epoch = 0
    private var observationGeneration = 0
    private var observation: Job? = null

    init {
        operation = true
        viewModelScope.launch {
            try { applySnapshot(session.resume()) }
            finally { operation = false }
        }
        observeSession()
    }

    private fun observeSession() {
        val generation = ++observationGeneration
        observation = viewModelScope.launch {
            session.changes.collect { snapshot ->
                if (generation == observationGeneration && state.value.phase == RegistrationPhase.Pending &&
                    snapshot.email == state.value.identity.email
                ) applySnapshot(snapshot)
            }
        }
    }

    fun handle(intent: RegistrationIntent) {
        if (operation || state.value.busy) return
        when (intent) {
            is RegistrationIntent.Email -> mutableState.value = state.value.copy(
                identity = state.value.identity.copy(email = intent.email), error = null,
            )
            is RegistrationIntent.Provider -> perform { provider(intent.provider) }
            RegistrationIntent.Manual -> mutableState.value = state.value.copy(
                scene = RegistrationScene.Manual, phase = RegistrationPhase.Idle, error = null, direction = 1,
            )
            RegistrationIntent.ConfirmEmail -> perform { checkName() }
            RegistrationIntent.Create -> if (state.value.identity.availability == VaultAvailability.AVAILABLE) {
                perform { submit(false) }
            }
            RegistrationIntent.Join -> if (state.value.scene == RegistrationScene.Name &&
                state.value.phase == RegistrationPhase.Idle && state.value.identity.availability == VaultAvailability.EXISTS
            ) {
                mutableState.value = state.value.copy(scene = RegistrationScene.Join, error = null, direction = 1)
            }
            RegistrationIntent.Request -> if (state.value.phase == RegistrationPhase.Idle) perform { submit(true) }
            is RegistrationIntent.QrDecoded -> perform { scanned(intent.email) }
            RegistrationIntent.Back -> perform { back() }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (operation) return
        operation = true
        val generation = ++epoch
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == epoch) mutableState.value = state.value.copy(
                    phase = RegistrationPhase.Error, error = AppString.orbitNetworkError,
                )
            } finally { operation = false }
        }
    }

    private suspend fun provider(provider: EmailProvider) {
        mutableState.value = state.value.copy(phase = RegistrationPhase.Provider, error = null)
        val email = when (provider) {
            EmailProvider.APPLE -> when (val result = providers.apple.requestAppleEmail()) {
                is AppleEmailAuthResult.Success -> result.email
                AppleEmailAuthResult.Cancelled -> null
                is AppleEmailAuthResult.Error -> {
                    mutableState.value = state.value.copy(error = AppString.orbitProviderUnavailable)
                    null
                }
            }
            EmailProvider.GOOGLE -> when (val result = providers.google.requestGoogleEmail()) {
                is GoogleEmailAuthResult.Success -> result.email
                GoogleEmailAuthResult.Cancelled -> null
                is GoogleEmailAuthResult.Error -> {
                    mutableState.value = state.value.copy(error = AppString.orbitProviderUnavailable)
                    null
                }
            }
            EmailProvider.MANUAL -> null
        }
        mutableState.value = state.value.copy(phase = RegistrationPhase.Idle)
        if (email != null) {
            mutableState.value = state.value.copy(identity = RegistrationIdentity(email.trim(), provider))
            checkName()
        }
    }

    private suspend fun checkName() {
        val email = state.value.identity.email.trim()
        if (!isValidEmail(email)) {
            mutableState.value = state.value.copy(error = AppString.orbitInvalidEmail)
            return
        }
        mutableState.value = state.value.copy(
            scene = RegistrationScene.Name, identity = state.value.identity.copy(email = email),
            phase = RegistrationPhase.Checking, error = null, direction = 1,
        )
        applySnapshot(session.prepare(email))
    }

    private suspend fun scanned(raw: String) {
        val email = registrationQrName(raw)
        if (email == null) {
            mutableState.value = state.value.copy(error = AppString.orbitInvalidQr)
            return
        }
        mutableState.value = state.value.copy(identity = RegistrationIdentity(email))
        val prepared = session.prepare(email)
        applySnapshot(prepared)
        if (prepared.status == RegistrationStatus.Prepared && prepared.availability == VaultAvailability.EXISTS) {
            mutableState.value = state.value.copy(scene = RegistrationScene.Join)
            submit(true)
        } else if (prepared.status == RegistrationStatus.Prepared) {
            mutableState.value = state.value.copy(scene = RegistrationScene.Join, phase = RegistrationPhase.Error, error = AppString.orbitVaultNotFound)
        }
    }

    private suspend fun submit(join: Boolean) {
        mutableState.value = state.value.copy(
            phase = if (join) RegistrationPhase.Requesting else RegistrationPhase.Creating,
            error = null,
        )
        val result = session.submit(join)
        applySnapshot(result)
        if (!join && result.status == RegistrationStatus.Failed && result.reconciledFailure) {
            mutableState.value = state.value.copy(phase = RegistrationPhase.CreateError)
            delay(3000) // Approved visible error duration; never a Core readiness timeout.
            mutableState.value = state.value.copy(scene = RegistrationScene.Name, phase = RegistrationPhase.Idle, error = null)
        }
    }

    private suspend fun back() {
        val previous = state.value
        if (previous.scene == RegistrationScene.Manual) {
            mutableState.value = previous.copy(
                scene = if (previous.identity.availability == null) RegistrationScene.Providers else RegistrationScene.Name,
                error = null, direction = -1,
            )
            return
        }
        if (previous.phase in setOf(RegistrationPhase.Pending, RegistrationPhase.Declined)) {
            cleanupIncomplete(previous)
            return
        }
        if (previous.scene == RegistrationScene.Join || previous.phase == RegistrationPhase.Error) {
            if (!session.reset()) {
                val refreshed = session.resume()
                if (refreshed.status == RegistrationStatus.Ready) applySnapshot(refreshed)
                else mutableState.value = previous.copy(phase = RegistrationPhase.Error, error = AppString.orbitResetFailed)
                return
            }
            mutableState.value = previous.copy(scene = RegistrationScene.Name, phase = RegistrationPhase.Checking, error = null, direction = -1)
            applySnapshot(session.prepare(previous.identity.email))
        }
    }

    private suspend fun cleanupIncomplete(previous: RegistrationUiState) {
        ++observationGeneration
        observation?.cancelAndJoin()
        try {
            val result = session.cleanupIncomplete()
            when (result.status) {
                RegistrationStatus.Empty -> mutableState.value = RegistrationUiState(direction = -1)
                RegistrationStatus.Ready -> applySnapshot(result)
                RegistrationStatus.Prepared -> {
                    mutableState.value = previous.copy(scene = RegistrationScene.Name, phase = RegistrationPhase.Checking,
                        error = null, direction = -1)
                    applySnapshot(session.prepare(result.email))
                }
                else -> mutableState.value = previous.copy(error = result.error ?: AppString.orbitResetFailed)
            }
        } finally { observeSession() }
    }

    private fun applySnapshot(snapshot: RegistrationSnapshot) {
        if (snapshot.status == RegistrationStatus.Empty) return
        val previous = state.value
        mutableState.value = previous.copy(
            identity = previous.identity.copy(email = snapshot.email, availability = snapshot.availability),
            scene = if (snapshot.status == RegistrationStatus.Pending || snapshot.status == RegistrationStatus.Declined) {
                RegistrationScene.Join
            } else previous.scene,
            phase = when (snapshot.status) {
                RegistrationStatus.Empty, RegistrationStatus.Prepared -> RegistrationPhase.Idle
                RegistrationStatus.Pending -> RegistrationPhase.Pending
                RegistrationStatus.Declined -> RegistrationPhase.Declined
                RegistrationStatus.Ready -> RegistrationPhase.Syncing
                RegistrationStatus.Failed -> RegistrationPhase.Error
            },
            error = if (snapshot.status == RegistrationStatus.Declined) AppString.orbitDeclined else snapshot.error,
        )
        if (snapshot.status == RegistrationStatus.Ready) mutableOpenMain.value = true
    }

    fun stopSession() {
        session.stop()
        viewModelScope.cancel()
    }

    override fun onCleared() {
        session.stop()
        super.onCleared()
    }
}
