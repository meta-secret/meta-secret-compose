package ui.dialogs.removesecret

import androidx.lifecycle.ViewModel
import core.KeyValueStorageInterface
import core.Secret

class RemoveSecretViewModel(
    private val keyValueStorage: KeyValueStorageInterface
) : ViewModel() {

    fun removeSecret(secret: Secret) {
        keyValueStorage.removeSecret(secret)
    }
}