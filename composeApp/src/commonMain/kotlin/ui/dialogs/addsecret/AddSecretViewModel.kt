package ui.dialogs.addsecret

import androidx.lifecycle.ViewModel
import storage.KeyValueStorage
import storage.Secret

class AddSecretViewModel(
    private val keyValueStorage: KeyValueStorage
) : ViewModel() {

    fun addSecret(secretName: String, password: String) {
        keyValueStorage.addSecret(Secret(secretName, password))
    }
}