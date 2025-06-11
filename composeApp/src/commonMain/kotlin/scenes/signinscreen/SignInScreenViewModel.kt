package scenes.signinscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.MasterKeyModel
import sharedData.KeyChainInterface
import sharedData.MetaSecretAppManager
import sharedData.MetaSecretCoreInterface
import storage.KeyValueStorage

class SignInScreenViewModel(
    private val metaSecretCoreInterface: MetaSecretCoreInterface,
    private val keyChainInterface: KeyChainInterface,
    private val keyValueStorage: KeyValueStorage,
    private val appManager: MetaSecretAppManager,
) : ViewModel() {

    // Properties
    private val _signInStatus = MutableStateFlow(false)
    val signInStatus: StateFlow<Boolean> = _signInStatus
    private val _masterKeyGenerationError = MutableStateFlow<String?>(null)
    val masterKeyGenerationError: StateFlow<String?> = _masterKeyGenerationError

    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    fun completeSignIn(name: String) {
        viewModelScope.launch {
            _signInStatus.value = true
        }
    }

    suspend fun generateAndSaveMasterKey(): Boolean {
        val masterKeyModel = generateMasterKey()

        if (masterKeyModel.success && !masterKeyModel.masterKey.isNullOrEmpty()) {
            println("✅ Got master key: $masterKeyModel")
            keyChainInterface.saveString("master_key", masterKeyModel.masterKey)

            if (appManager.initWithSavedKey()) {
                val state = appManager.getState()
                println("State: $state")
            }

            return true
        } else {
            _masterKeyGenerationError.value = masterKeyModel.error
            return false
        }
    }

    private fun generateMasterKey(): MasterKeyModel {
        val jsonResponse = metaSecretCoreInterface.generateMasterKey()
        return MasterKeyModel.fromJson(jsonResponse)
    }

}