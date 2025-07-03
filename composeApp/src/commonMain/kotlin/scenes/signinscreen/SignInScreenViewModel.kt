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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import models.apiModels.MetaSecretCoreStateModel
import models.apiModels.OutsiderStatus
import models.apiModels.StateType
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import sharedData.metaSecretCore.MemberState
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import sharedData.metaSecretCore.OutsiderState

class SignInScreenViewModel(
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorage,
    private val appManager: MetaSecretAppManager,
    private val socketHandler: MetaSecretSocketHandlerInterface
) : ViewModel() {

    // Properties
    private val _isUIBlocked = MutableStateFlow(false)
    val isUIBlocked: StateFlow<Boolean> = _isUIBlocked // TODO: #49 Need to be able to edit entered name
    private val _signInStatus = MutableStateFlow(false)
    val signInStatus: StateFlow<Boolean> = _signInStatus
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _snackBarMessage = MutableStateFlow<String?>(null)
    val snackBarMessage: StateFlow<String?> = _snackBarMessage
    private val _showSkackBar = MutableStateFlow<Boolean>(false)
    val showSnackBar: StateFlow<Boolean> = _showSkackBar
    private val _isError = MutableStateFlow<Boolean>(true)
    val isError: StateFlow<Boolean> = _isError
    private val unexpectedLoginStringResource = "Unexpected loading state" // TODO: Find a way to get from resources
        //Res.string.unexpected_login
    private val waitForJoinMessage = "Accept the request on your other device" // TODO: Find a way to get from resources
    //Res.string.accept_request_on_other_device

    init {
        socketSubscribe()
    }

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
        _isUIBlocked.value = true

        val masterKeyModel = generateMasterKey()

        if (masterKeyModel.success && !masterKeyModel.masterKey.isNullOrEmpty()) {
            println("\uD83D\uDD10 ✅ SignInVM: Generated master key: $masterKeyModel")
            keyChainManager.saveString("master_key", masterKeyModel.masterKey)

            when (val initResult = appManager.initWithSavedKey()) {
                is InitResult.Success -> {
                    println("\uD83D\uDD10 ✅ SignInVM: Start Sign Up")
                    val signUpResult = withContext(Dispatchers.IO) {
                        metaSecretStateResolver.startFirstSignUp(vaultName)
                    }

                    _isLoading.value = false
                    _isUIBlocked.value = false

                    if (signUpResult.error != null) {
                        println("\uD83D\uDD10 ⛔SignInVM:No further actions")
                        showErrorSnackBar(signUpResult.error.value)
                        return false
                    } else {
                        when (signUpResult.appState) {
                            is MemberState -> {
                                println("\uD83D\uDD10 ✅ SignInVM: Sign up is successfull")
                                return true
                            }
                            is OutsiderState -> {
                                println("\uD83D\uDD10 ✅ SignInVM: Start listening for Join accept signal")
                                socketHandler.actionsToFollow(
                                    add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
                                    exclude = null
                                )
                                showErrorSnackBar(waitForJoinMessage, false, null)
                                return false
                            }
                            else -> {
                                println("\uD83D\uDD10 ⛔SignInVM:Unknown state for sign up")
                                return false
                            }
                        }
                    }
                }
                is InitResult.Error -> {
                    _isLoading.value = false
                    _isUIBlocked.value = false
                    showErrorSnackBar(initResult.message)
                    return false
                }
                is InitResult.Loading -> {
                    _isLoading.value = false
                    _isUIBlocked.value = false
                    showErrorSnackBar(unexpectedLoginStringResource)
                    return false
                }
            }
        } else {
            _isLoading.value = false
            _isUIBlocked.value = false
            showErrorSnackBar(masterKeyModel.error ?: unexpectedLoginStringResource)
            return false
        }
    }

    private fun generateMasterKey(): MasterKeyModel {
        val jsonResponse = metaSecretCore.generateMasterKey()
        return MasterKeyModel.fromJson(jsonResponse)
    }

    private fun socketSubscribe() {
        viewModelScope.launch {
            // Subscribe handling
            socketHandler.actionType.collectLatest { actionType ->
                println("\uD83D\uDD10 ✅ SignInVM: Subscribe SignIn screen for Join Response Signal")
                when (actionType) {
                    SocketActionModel.JOIN_REQUEST_ACCEPTED -> {
                        println("\uD83D\uDD10 ✅ SignInVM: Got Accepted signal")
                        _showSkackBar.value = false
                    }
                    SocketActionModel.JOIN_REQUEST_DECLINED -> {
                        println("\uD83D\uDD10 ⛔ SignInVM: Got Declined signal")
                        _showSkackBar.value = false
                    }
                    SocketActionModel.JOIN_REQUEST_PENDING -> {
                        println("\uD83D\uDD10 ⏳ SignInVM: Joining still in progress")
                        showErrorSnackBar(waitForJoinMessage, false, null)
                    }
                    else -> {
                        println("\uD83D\uDD10 ⛔SignInVM: Joining not following yet: $actionType")
                        _showSkackBar.value = false
                    }
                }
            }

            // Subscribe init
            val initResult = appManager.initWithSavedKey()
            when (initResult) {
                is InitResult.Success -> {
                    val stateJson = metaSecretCore.getAppState()
                    val currentState = MetaSecretCoreStateModel.fromJson(stateJson)

                    if (currentState.success &&
                        currentState.getState() == StateType.OUTSIDER &&
                        currentState.getVaultInfo()?.outsider?.status == OutsiderStatus.PENDING
                    ) {
                        println("\uD83D\uDD10 ✅ SignInVM: Already in progress")
                        socketHandler.actionsToFollow(
                            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
                            exclude = null
                        )
                        showErrorSnackBar(waitForJoinMessage,  false, null)
                    } else {
                        println("\uD83D\uDD10 ✅ SignInVM: It's not a pending")
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun showErrorSnackBar(message: String, isError: Boolean = true, duration: Long? = 3000) {
        _snackBarMessage.value = message
        _isError.value = isError
        _showSkackBar.value = true
        _isUIBlocked.value = !isError
        
        delay(duration ?: Long.MAX_VALUE)
        _showSkackBar.value = false
        
        if (!isError && duration != null) {
            _isUIBlocked.value = false
        }
    }
}