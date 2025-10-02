package ui.dialogs.addsecret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import core.LogTags
import core.KeyValueStorageInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import kotlinx.coroutines.launch
import models.appInternalModels.SecretModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import kotlin.properties.Delegates

class AddSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
) : ViewModel(), CommonViewModel {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _state = MutableStateFlow<AddSecretState?>(null)
    val state: StateFlow<AddSecretState?> = _state

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
                    addSecret(secretName = event.secretId, secret = event.secret)
                }
            }
        }
    }

    private fun addSecret(secretName: String, secret: String) {
        viewModelScope.launch {
            _isLoading.value = true
            currentState = AddSecretState.IN_PROGRESS
            val secretObject = SecretModel(secretName, secret)
            val splitResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
                metaSecretAppManager.splitSecret(secretObject)
            }
            currentState = if (splitResult != null && splitResult.success) {
                val secretsFromVault = metaSecretAppManager.getSecretsFromVault()
                if (secretsFromVault != null) {
                    keyValueStorage.syncSecretsFromVault(secretsFromVault)
                }
                AddSecretState.ADDED_SUCCESSFULLY
            } else {
                AddSecretState.ADDING_FAILURE
            }
            _isLoading.value = false
        }
    }

    private fun addSecretStateResolver() {
        when (currentState) {
            AddSecretState.IDLE -> println("✅" + LogTags.ADD_SECRET_VM + ": Waiting for AddSecret")
            AddSecretState.IN_PROGRESS -> println("✅" + LogTags.ADD_SECRET_VM + ": In progress")
            AddSecretState.ADDED_SUCCESSFULLY -> println("✅" + LogTags.ADD_SECRET_VM + ": Added successfully")
            AddSecretState.ADDING_FAILURE -> println("❌" + LogTags.ADD_SECRET_VM + ": Adding failed")
            null -> println("❌" + LogTags.ADD_SECRET_VM + ": Unknown state")
        }
    }
}

sealed class AddSecretEvents : CommonViewModelEventsInterface {
    data class AddSecret(val secretId: String, val secret: String) : AddSecretEvents()
}

enum class AddSecretState {
    IDLE,
    IN_PROGRESS,
    ADDED_SUCCESSFULLY,
    ADDING_FAILURE
}