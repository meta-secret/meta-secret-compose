package ui.dialogs.removesecret

import androidx.lifecycle.ViewModel
import core.KeyValueStorage
import core.Secret

class RemoveSecretViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun removeSecret(secret: Secret) {
        keyValueStorage.removeSecret(secret)
    }
}