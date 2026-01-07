package ui.dialogs.addsecret

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import core.LogTag
import core.KeyValueStorageInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.BiometricAuthenticatorInterface
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.appInternalModels.SecretModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import kotlin.properties.Delegates

class AddSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
) : CommonViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _state = MutableStateFlow<AddSecretState?>(null)
    val state: StateFlow<AddSecretState?> = _state

    private val _biometricError = MutableSharedFlow<String>(replay = 0)
    val biometricError: SharedFlow<String> = _biometricError

    private var currentState: AddSecretState? by Delegates.observable(null) { _, _, _ ->
        _state.value = currentState
        viewModelScope.launch {
            addSecretStateResolver()
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is AddSecretEvents) {
            when (event) {
                is AddSecretEvents.AddSecret -> {
                    biometricAuthenticator.authenticate(
                        onSuccess = {
                            logger.log(LogTag.AddSecretVM.Message.BiometricAuthSuccess, success = true)
                            addSecret(secretName = event.secretName, secret = event.secret)
                        },
                        onError = { error ->
                            logger.log(LogTag.AddSecretVM.Message.BiometricAuthFailed, error, success = false)
                            viewModelScope.launch {
                                _biometricError.emit(error)
                                _isLoading.value = false
                            }
                        },
                        onFallback = {
                            logger.log(LogTag.AddSecretVM.Message.BiometricAuthFallback, success = false)
                            viewModelScope.launch {
                                _biometricError.emit("")
                                _isLoading.value = false
                            }
                        }
                    )
                }
                AddSecretEvents.ResetState -> {
                    _isLoading.value = false
                    currentState = AddSecretState.IDLE
                }
            }
        }
    }

    private fun addSecret(secretName: String, secret: String) {
        logger.log(LogTag.AddSecretVM.Message.StartingAddSecret, success = true)
        viewModelScope.launch {
            _isLoading.value = true
            currentState = AddSecretState.IN_PROGRESS
            try {
                val secretObject = SecretModel(secretName, secret)
                val splitResult = withContext(Dispatchers.IO) {
                    metaSecretAppManager.splitSecret(secretObject)
                }
                currentState = if (splitResult != null && splitResult.success) {
                    val secretsFromVault = withContext(Dispatchers.IO) {
                        metaSecretAppManager.getSecretsFromVault()
                    }
                    if (secretsFromVault != null) {
                        keyValueStorage.syncSecretsFromVault(secretsFromVault)
                    }
                    AddSecretState.ADDED_SUCCESSFULLY
                } else {
                    logger.log(LogTag.AddSecretVM.Message.AddingFailed, "splitResult=$splitResult", success = false)
                    AddSecretState.ADDING_FAILURE
                }
            } catch (e: Exception) {
                logger.log(LogTag.AddSecretVM.Message.AddingFailed, "${e.message}", success = false)
                currentState = AddSecretState.ADDING_FAILURE
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun addSecretStateResolver() {
        when (currentState) {
            AddSecretState.IDLE -> logger.log(LogTag.AddSecretVM.Message.WaitingForAddSecret, success = true)
            AddSecretState.IN_PROGRESS -> logger.log(LogTag.AddSecretVM.Message.InProgress, success = true)
            AddSecretState.ADDED_SUCCESSFULLY -> logger.log(LogTag.AddSecretVM.Message.AddedSuccessfully, success = true)
            AddSecretState.ADDING_FAILURE -> logger.log(LogTag.AddSecretVM.Message.AddingFailed, success = false)
            null -> logger.log(LogTag.AddSecretVM.Message.UnknownState, success = false)
        }
    }
}

sealed class AddSecretEvents : CommonViewModelEventsInterface {
    data class AddSecret(val secretName: String, val secret: String) : AddSecretEvents()
    data object ResetState : AddSecretEvents()
}

enum class AddSecretState {
    IDLE,
    IN_PROGRESS,
    ADDED_SUCCESSFULLY,
    ADDING_FAILURE
}