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
import sharedData.metaSecretCore.MetaSecretCoreInterface
import sharedData.metaSecretCore.MetaSecretStateResolverInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import scenes.common.CommonViewModel
import scenes.common.CommonViewModelEventsInterface
import sharedData.metaSecretCore.MemberState
import sharedData.metaSecretCore.MetaSecretAppManagerInterface
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import sharedData.metaSecretCore.OutsiderState
import kotlin.properties.Delegates

class SignInScreenViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorage,
    private val socketHandler: MetaSecretSocketHandlerInterface
) : ViewModel(), CommonViewModel {

    // Properties
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading // TODO: #49 Need to be able to edit entered name
    private val _snackBarMessage = MutableStateFlow<SignInSnackMessages?>(null)
    val snackBarMessage: StateFlow<SignInSnackMessages?> = _snackBarMessage
    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent

    private var currentName: String? = null
    private var currentState: SignInStates? by Delegates.observable(null) { _, _, _ ->
        viewModelScope.launch {
            signInStateResolver()
        }
    }

    init {
        viewModelScope.launch {
            initialState()
        }
        socketSubscribe()
        addSubscriptionToSocket()
    }

    // Public API for View
    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SignInViewEvents) {
            when (event) {
                is SignInViewEvents.StartSignInProcess -> {
                    currentName = event.name
                    currentState = SignInStates.START_SIGN_IN
                }
            }
        }
    }

    // Check Initial state
    private suspend fun initialState() {
        if (initAppManagerResult()) {
            metaSecretAppManager.getStateModel()?.getAppState()?.let { state ->
                when (state) {
                    is State.Vault -> {
                        currentState = when (metaSecretAppManager.getVaultFullInfoModel()) {
                            is VaultFullInfo.Outsider -> {
                                SignInStates.SIGN_IN_PENDING
                            }
                            null -> {
                                SignInStates.IDLE
                            }
                            else -> error("Critical error! Impossible state")
                        }
                    }
                    else -> {}
                }
            }
        } else {
            currentState = SignInStates.IDLE
        }
    }

    // Resolve all states of the screen
    private suspend fun signInStateResolver() {
            when (currentState) {
                SignInStates.IDLE -> println("\uD83D\uDD10 ✅ SignInVM: Waiting for SignUp")
                SignInStates.START_SIGN_IN -> {
                    currentName?.let { name ->
                        isNameError(name)
                    } ?: run {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.NAME_INCORRECT -> showErrorSnackBar(SignInSnackMessages.INCORRECT_NAME)
                SignInStates.NAME_SUCCEEDED -> generateMasterKey()
                SignInStates.MASTER_KEY_GENERATED -> {
                    currentName?.let { name ->
                        firstSignUp(name)
                    } ?: run {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.MASTER_KEY_FAILED -> showErrorSnackBar(SignInSnackMessages.SIGN_IN_ERROR)
                SignInStates.SIGN_IN_PENDING -> viewModelScope.launch { showPendingState() }
                SignInStates.SIGN_IN_REJECTED -> showErrorSnackBar(SignInSnackMessages.REJECT)
                SignInStates.SIGN_IN_COMPLETED -> _navigationEvent.value = true
                SignInStates.SIGN_IN_FAILED -> showErrorSnackBar(SignInSnackMessages.SIGN_IN_ERROR)
                null -> showErrorSnackBar(SignInSnackMessages.SIGN_IN_ERROR)

            }
    }

    private suspend fun showPendingState() {
        _isLoading.value = true
        println("\uD83D\uDD10 ✅ SignInVM: Start listening for Join accept signal")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
            exclude = null
        )
        showErrorSnackBar(SignInSnackMessages.WAIT_JOIN, true, null)
    }

    private fun isNameError(string: String) {
        val regex = "^[A-Za-z0-9_]{2,10}$"
        val isNameError = !(string.matches(regex.toRegex()) && string != keyValueStorage.signInInfo?.username)
        currentState = if (isNameError) {
            SignInStates.NAME_INCORRECT
        } else {
            SignInStates.NAME_SUCCEEDED
        }
    }

    private suspend fun firstSignUp(vaultName: String) {
        _isLoading.value = true

        if (initAppManagerResult()) {
            println("\uD83D\uDD10 ✅ SignInVM: Start Sign Up")
            val signUpResult = withContext(Dispatchers.IO) {
                metaSecretStateResolver.startFirstSignUp(vaultName)
            }

            if (signUpResult.error != null) {
                println("\uD83D\uDD10 ⛔SignInVM:No further actions")
                currentState = SignInStates.SIGN_IN_FAILED
            } else {
                when (signUpResult.appState) {
                    is MemberState -> {
                        println("\uD83D\uDD10 ✅ SignInVM: Sign up is successfull")
                        currentState = SignInStates.SIGN_IN_COMPLETED
                    }
                    is OutsiderState -> {
                        println("\uD83D\uDD10 ✅ SignInVM: Start listening for Join accept signal")
                        currentState = SignInStates.SIGN_IN_PENDING
                    }
                    else -> {
                        println("\uD83D\uDD10 ⛔SignInVM:Unknown state for sign up")
                        currentState = SignInStates.SIGN_IN_FAILED
                    }
                }
            }
        } else {
            showErrorSnackBar(SignInSnackMessages.UNEXPECTED_LOGIN_STATE)
        }

        _isLoading.value = false
    }

    private suspend fun generateMasterKey() {
        _isLoading.value = true
        val jsonResponse = metaSecretCore.generateMasterKey()
        val model = MasterKeyModel.fromJson(jsonResponse)

        if (model.success && !model.masterKey.isNullOrEmpty()) {
            println("\uD83D\uDD10 ✅ SignInVM: Generated master key: $model")
            keyChainManager.saveString("master_key", model.masterKey)
            currentState = SignInStates.MASTER_KEY_GENERATED
        } else {
            currentState = SignInStates.MASTER_KEY_FAILED
        }
        _isLoading.value = false
    }

    // Socket routine
    private fun socketSubscribe() {
        viewModelScope.launch {
            socketHandler.actionType.collect { actionType ->
                println("\uD83D\uDD10 ✅ SignInVM: Subscribe SignIn screen for Join Response Signal")
                when (actionType) {
                    SocketActionModel.JOIN_REQUEST_ACCEPTED -> {
                        println("\uD83D\uDD10 ✅ SignInVM: Got Accepted signal")
                        currentState = SignInStates.SIGN_IN_COMPLETED
                    }
                    SocketActionModel.JOIN_REQUEST_DECLINED -> {
                        println("\uD83D\uDD10 ⛔ SignInVM: Got Declined signal")
                        currentState = SignInStates.SIGN_IN_REJECTED
                    }
                    SocketActionModel.JOIN_REQUEST_PENDING -> {
                        println("\uD83D\uDD10 ⏳ SignInVM: Joining still in progress")
                        currentState = SignInStates.SIGN_IN_PENDING
                    }
                    else -> {
                        println("\uD83D\uDD10 ⛔SignInVM: Joining not following yet: $actionType")
                        currentState = SignInStates.SIGN_IN_FAILED
                    }
                }
            }
        }
    }

    private fun addSubscriptionToSocket() {
        // Subscribe init
        viewModelScope.launch {
            when (metaSecretAppManager.initWithSavedKey()) {
                is InitResult.Success -> {
                    val appState = metaSecretAppManager.getStateModel()
                    val vaultState = appState?.getVaultFullInfo()
                    val outsiderState = appState?.getOutsiderStatus()

                    if ( vaultState is VaultFullInfo.Outsider && outsiderState == UserDataOutsiderStatus.PENDING) {
                        currentState = SignInStates.SIGN_IN_PENDING
                    } else {
                        println("\uD83D\uDD10 ✅ SignInVM: It's not a pending")
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun initAppManagerResult(): Boolean {
        return metaSecretAppManager.initWithSavedKey() is InitResult.Success
    }

    private suspend fun showErrorSnackBar(message: SignInSnackMessages, blockUI: Boolean = false, duration: Long? = 3000) {
        withContext(Dispatchers.Main.immediate) {
            _snackBarMessage.value = message
            _isLoading.value = blockUI
        }
        delay(duration ?: Long.MAX_VALUE) // TODO: Long.MAX_VALUE is freezing the main UI flow
        withContext(Dispatchers.Main.immediate) {
            _isLoading.value = false
            _snackBarMessage.value = null
        }
    }
}

enum class SignInSnackMessages {
    UNEXPECTED_LOGIN_STATE,
    WAIT_JOIN,
    INCORRECT_NAME,
    SIGN_IN_ERROR,
    REJECT,
}

sealed class SignInViewEvents : CommonViewModelEventsInterface {
    data class StartSignInProcess(val name: String) : SignInViewEvents()
}

private enum class SignInStates {
    IDLE,
    START_SIGN_IN,
    NAME_SUCCEEDED,
    NAME_INCORRECT,
    MASTER_KEY_GENERATED,
    MASTER_KEY_FAILED,
    SIGN_IN_COMPLETED,
    SIGN_IN_PENDING,
    SIGN_IN_REJECTED,
    SIGN_IN_FAILED,
}