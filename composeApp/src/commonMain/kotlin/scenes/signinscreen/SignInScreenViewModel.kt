package scenes.signinscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.MasterKeyModel
import sharedData.KeyChainInterface
import storage.KeyValueStorage
import sharedData.metaSecretCore.InitResult
import sharedData.metaSecretCore.MetaSecretAppManager
import sharedData.metaSecretCore.MetaSecretCoreInterface
import sharedData.metaSecretCore.MetaSecretStateResolverInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

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
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _errorNotification = MutableStateFlow<String?>(null)
    val errorNotification: StateFlow<String?> = _errorNotification
    private val _showErrorNotification = MutableStateFlow<Boolean>(false)
    val showErrorNotification: StateFlow<Boolean> = _showErrorNotification

    fun isNameError(string: String): Boolean {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        return !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
    }

    fun completeSignIn() {
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
                    println("✅ Start Sign Up")
                    val signUpResult = withContext(Dispatchers.IO) {
                        metaSecretStateResolver.startFirstSignUp(vaultName)
                    }

                    _isLoading.value = false

                    if (signUpResult.error != null) {
                        println("⛔No further actions")
                        _errorNotification.value = signUpResult.error.value
                        _showErrorNotification.value = true
                        delay(3000)
                        _showErrorNotification.value = false
                        return false
                    }

                    println("✅Sign up is successfull")
                    return true
                }
                is InitResult.Error -> {
                    _errorNotification.value = initResult.message
                    _isLoading.value = false
                    _showErrorNotification.value = true
                    delay(3000)
                    _showErrorNotification.value = false
                    return false
                }
                is InitResult.Loading -> {
                    _errorNotification.value = "Unexpected loading state"
                    _isLoading.value = false
                    _showErrorNotification.value = true
                    delay(3000)
                    _showErrorNotification.value = false
                    return false
                }
            }
        } else {
            _isLoading.value = false
            _errorNotification.value = masterKeyModel.error
            _showErrorNotification.value = true
            delay(3000)
            _showErrorNotification.value = false
            return false
        }
    }

    private fun generateMasterKey(): MasterKeyModel {
        val jsonResponse = metaSecretCore.generateMasterKey()
        return MasterKeyModel.fromJson(jsonResponse)
    }

}