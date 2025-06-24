package scenes.signinscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.CommonResponseModel
import models.apiModels.MasterKeyModel
import models.appInternalModels.HandleStateModel
import sharedData.KeyChainInterface
import storage.KeyValueStorage
import sharedData.metaSecretCore.InitResult
import sharedData.metaSecretCore.MetaSecretAppManager
import sharedData.metaSecretCore.MetaSecretCoreInterface
import sharedData.metaSecretCore.MetaSecretStateResolverInterface

class SignInScreenViewModel(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorage,
    private val appManager: MetaSecretAppManager,
) : ViewModel() {

    // Properties
    private val _signInStatus = MutableStateFlow(false)
    val signInStatus: StateFlow<Boolean> = _signInStatus
    private val _masterKeyGenerationError = MutableStateFlow<String?>(null)
    val masterKeyGenerationError: StateFlow<String?> = _masterKeyGenerationError
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    fun completeSignIn(name: String) {
        viewModelScope.launch {
            _signInStatus.value = true
        }
    }

    suspend fun generateAndSaveMasterKey(vaultName: String): Boolean {
        _isLoading.value = true
        val masterKeyModel = generateMasterKey()

        if (masterKeyModel.success && !masterKeyModel.masterKey.isNullOrEmpty()) {
            println("✅ Generated master key: $masterKeyModel")
            keyChainManager.saveString("master_key", masterKeyModel.masterKey)

            when (val initResult = appManager.initWithSavedKey()) {
                is InitResult.Success -> {
                    val appManagerInitResult = CommonResponseModel.fromJson(initResult.result)
                    if (appManagerInitResult.success) {
                        val state = appManager.getState()
                        metaSecretStateResolver.handleState(HandleStateModel(state, vaultName))

                        _isLoading.value = false
                        return false // TODO: Return true or false according to state result
                    } else {
                        _isLoading.value = false
                        return false
                    }
                }
                is InitResult.Error -> {
                    _masterKeyGenerationError.value = initResult.message
                    _isLoading.value = false
                    return false
                }
                is InitResult.Loading -> {
                    _masterKeyGenerationError.value = "Unexpected loading state"
                    _isLoading.value = false
                    return false
                }
            }
        } else {
            _isLoading.value = false
            _masterKeyGenerationError.value = masterKeyModel.error
            return false
        }
    }

    private fun generateMasterKey(): MasterKeyModel {
        val jsonResponse = metaSecretCore.generateMasterKey()
        return MasterKeyModel.fromJson(jsonResponse)
    }

}