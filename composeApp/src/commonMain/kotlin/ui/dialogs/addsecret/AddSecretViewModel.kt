package ui.dialogs.addsecret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.LogTags
import core.metaSecretCore.MetaSecretAppManagerInterface
import kotlinx.coroutines.launch
import models.appInternalModels.SecretModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import kotlin.properties.Delegates

class AddSecretViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
) : ViewModel(), CommonViewModel {

    private var currentState: AddSecretState? by Delegates.observable(null) { _, _, _ ->
        viewModelScope.launch {
            addSecretStateResolver()
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is AddSecretEvents) {
            when (event) {
                is AddSecretEvents.AddSecret -> {
                    addSecret(secretId = event.secretId, secret = event.secret)
                }
            }
        }
    }

    private fun addSecret(secretId: String, secret: String) {
        currentState = AddSecretState.IN_PROGRESS
        val secretObject = SecretModel(secretId, secret)
        val splitResult = metaSecretAppManager.splitSecret(secretObject)
        currentState = if (splitResult == null) {
            AddSecretState.ADDING_FAILURE
        } else {
            if (splitResult.success) {
                AddSecretState.ADDED_SUCCESSFULLY
            } else {
                AddSecretState.ADDING_FAILURE
            }
        }
    }

    private suspend fun addSecretStateResolver() {
        when (currentState) {
            AddSecretState.IDLE -> println("✅" + LogTags.ADD_SECRET_VM + ": Waiting for SignUp")
            AddSecretState.IN_PROGRESS -> TODO()
            AddSecretState.ADDED_SUCCESSFULLY -> TODO()
            AddSecretState.ADDING_FAILURE -> TODO()
            null -> TODO()
        }
    }
}

sealed class AddSecretEvents : CommonViewModelEventsInterface {
    data class AddSecret(val secretId: String, val secret: String) : AddSecretEvents()
}

private enum class AddSecretState {
    IDLE,
    IN_PROGRESS,
    ADDED_SUCCESSFULLY,
    ADDING_FAILURE
}