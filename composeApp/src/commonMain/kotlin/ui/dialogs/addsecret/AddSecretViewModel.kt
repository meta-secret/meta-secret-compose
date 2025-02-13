package ui.dialogs.addsecret

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import sharedData.Repository

class AddSecretViewModel (
    private val repository: Repository
) : ViewModel() {

    private val _secretsCount = mutableStateOf(repository.secrets.size)

    fun addSecret(secretName: String, password: String) {
        val newSecret = Repository.Secret(secretName, password)
        repository.addSecret(newSecret)
        _secretsCount.value = repository.secrets.size
    }

}