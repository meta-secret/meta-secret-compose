package ui.dialogs.removesecret

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage
import storage.Secret

class RemoveSecretViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun removeSecret(secret: Secret) {
        keyValueStorage.removeSecret(secret)
    }
}