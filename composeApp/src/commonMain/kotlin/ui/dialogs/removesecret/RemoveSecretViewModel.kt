package ui.dialogs.removesecret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.BiometricAuthenticatorInterface
import core.Secret
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.SecretDeletionState

class RemoveSecretViewModel(
    private val appManager: MetaSecretAppManagerInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    fun removeSecret(secret: Secret, onComplete: () -> Unit) = submit(true, onComplete) {
        appManager.deleteSecret(secret.secretId)?.success == true
    }

    fun decide(request: SecretDeletionState, accepted: Boolean, onComplete: () -> Unit) {
        val requestId = request.requestId ?: return
        submit(accepted, onComplete) { appManager.decideDeleteSecret(requestId, accepted)?.success == true }
    }

    private fun submit(authenticate: Boolean, onComplete: () -> Unit, operation: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        _failed.value = false
        val run = {
            viewModelScope.launch {
                socketHandler.pauseRefreshes()
                try {
                    if (operation()) onComplete() else _failed.value = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _failed.value = true
                } finally {
                    _busy.value = false
                    socketHandler.resumeRefreshes()
                    socketHandler.refreshAppState()
                }
            }
            Unit
        }
        if (authenticate) biometricAuthenticator.authenticate(
            onSuccess = run,
            onError = { _busy.value = false; _failed.value = true },
            onFallback = { _busy.value = false; _failed.value = true },
        ) else run()
    }
}
