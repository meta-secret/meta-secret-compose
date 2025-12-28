package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.MasterKeyModel
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.metaSecretCore.InitResult
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import core.metaSecretCore.MemberState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.OutsiderState
import kotlin.properties.Delegates
import core.LogTag
import core.ScreenMetricsProviderInterface
import core.BiometricAuthenticatorInterface

class SignInScreenViewModel(
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
) : CommonViewModel() {

    // Properties
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _snackBarMessage = MutableStateFlow<SignInSnackMessages?>(null)
    val snackBarMessage: StateFlow<SignInSnackMessages?> = _snackBarMessage
    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent
    private val _nameText = MutableStateFlow("")
    val nameText: StateFlow<String> = _nameText
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
                    _nameText.value = event.name
                    currentState = SignInStates.START_SIGN_IN
                }
                is SignInViewEvents.UpdateName -> {
                    _nameText.value = event.name
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
                        val vaultInfo = metaSecretAppManager.getVaultFullInfoModel()
                        logger.log(LogTag.SignInVM.Message.VaultStateDetected, "$vaultInfo", success = true)
                        currentState = when (vaultInfo) {
                            is VaultFullInfo.Outsider -> {
                                logger.log(LogTag.SignInVM.Message.UserIsOutsider, success = true)
                                SignInStates.SIGN_IN_PENDING
                            }
                            is VaultFullInfo.Member -> {
                                logger.log(LogTag.SignInVM.Message.UserIsMember, success = true)
                                SignInStates.SIGN_IN_COMPLETED
                            }
                            is VaultFullInfo.NotExists -> {
                                logger.log(LogTag.SignInVM.Message.NoVaultInfoIdle, success = true)
                                SignInStates.IDLE
                            }
                            null -> {
                                logger.log(LogTag.SignInVM.Message.NoVaultInfoIdle, success = true)
                                SignInStates.IDLE
                            }
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
                SignInStates.IDLE -> logger.log(LogTag.SignInVM.Message.WaitingForSignUp, success = true)
                SignInStates.START_SIGN_IN -> {
                    val name = _nameText.value
                    if (name.isNotEmpty()) {
                        isNameError(name)
                    } else {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.NAME_INCORRECT -> showErrorSnackBar(SignInSnackMessages.INCORRECT_NAME)
                SignInStates.NAME_SUCCEEDED -> generateMasterKey()
                SignInStates.MASTER_KEY_GENERATED -> {
                    val name = _nameText.value
                    if (name.isNotEmpty()) {
                        firstSignUp(name)
                    } else {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.MASTER_KEY_FAILED -> showErrorSnackBar(SignInSnackMessages.SIGN_IN_ERROR)
                SignInStates.SIGN_IN_PENDING -> viewModelScope.launch { showPendingState() }
                SignInStates.SIGN_IN_REJECTED -> {
                    _isLoading.value = false
                    _nameText.value = ""
                    showErrorSnackBar(SignInSnackMessages.REJECT)
                }
                SignInStates.SIGN_IN_COMPLETED -> _navigationEvent.value = true
                SignInStates.SIGN_IN_FAILED -> showErrorSnackBar(SignInSnackMessages.SIGN_IN_ERROR)
                SignInStates.NONE -> showErrorSnackBar(SignInSnackMessages.NONE)
                null -> showErrorSnackBar(SignInSnackMessages.NONE)
            }
    }

    private suspend fun showPendingState() {
        _isLoading.value = true
        logger.log(LogTag.SignInVM.Message.StartListeningJoinAccept, success = true)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
            exclude = null
        )
        showErrorSnackBar(SignInSnackMessages.WAIT_JOIN, true, null)
    }

    private fun isNameError(string: String) {
        val regex = "^[A-Za-z0-9_]{2,20}$"
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
            logger.log(LogTag.SignInVM.Message.StartSignUp, success = true)
            val signUpResult = withContext(Dispatchers.IO) {
                metaSecretStateResolver.startFirstSignUp(vaultName)
            }

            if (signUpResult.error != null) {
                logger.log(LogTag.SignInVM.Message.SignUpUnknownState, success = false)
                currentState = SignInStates.SIGN_IN_FAILED
            } else {
                when (signUpResult.appState) {
                    is MemberState -> {
                        logger.log(LogTag.SignInVM.Message.SignUpSuccess, success = true)
                        currentState = SignInStates.SIGN_IN_COMPLETED
                    }
                    is OutsiderState -> {
                        logger.log(LogTag.SignInVM.Message.StartListeningJoinAccept, success = true)
                        currentState = SignInStates.SIGN_IN_PENDING
                    }
                    else -> {
                        logger.log(LogTag.SignInVM.Message.SignUpUnknownState, success = false)
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
            logger.log(LogTag.SignInVM.Message.GeneratedMasterKey, "$model", success = true)
            keyChainManager.saveString("master_key", model.masterKey)
            logger.setMasterKeyGenerated(true)
            currentState = SignInStates.MASTER_KEY_GENERATED
        } else {
            logger.setMasterKeyGenerated(false)
            currentState = SignInStates.MASTER_KEY_FAILED
        }
        _isLoading.value = false
    }

    // Socket routine
    private fun socketSubscribe() {
        viewModelScope.launch {
            socketHandler.socketActionType.collect { actionType ->
                logger.log(LogTag.SignInVM.Message.SubscribeJoinResponse, success = true)
                when (actionType) {
                    SocketActionModel.JOIN_REQUEST_ACCEPTED -> {
                        handleJoinRequestAccepted()
                    }
                    SocketActionModel.JOIN_REQUEST_DECLINED -> {
                        logger.log(LogTag.SignInVM.Message.GotDeclinedSignal, success = false)
                        currentState = SignInStates.SIGN_IN_REJECTED
                    }
                    SocketActionModel.JOIN_REQUEST_PENDING -> {
                        logger.log(LogTag.SignInVM.Message.JoiningInProgress, success = true)
                        currentState = SignInStates.SIGN_IN_PENDING
                    }
                    else -> {
                        logger.log(LogTag.SignInVM.Message.JoiningNotFollowing, "$actionType", success = false)
                        currentState = SignInStates.NONE
                    }
                }
            }
        }
    }

    private fun handleJoinRequestAccepted() {
        biometricAuthenticator.authenticate(
            onSuccess = {
                logger.log(LogTag.SignInVM.Message.BiometricAuthSuccess, success = true)
                logger.log(LogTag.SignInVM.Message.GotAcceptedSignal, success = true)
                viewModelScope.launch {
                    currentState = SignInStates.SIGN_IN_COMPLETED
                }
            },
            onError = { error ->
                logger.log(LogTag.SignInVM.Message.BiometricAuthFailed, error, success = false)
                viewModelScope.launch {
                    showErrorSnackBar(SignInSnackMessages.BIOMETRIC_ERROR)
                    currentState = SignInStates.IDLE
                }
            },
            onFallback = {
                logger.log(LogTag.SignInVM.Message.BiometricAuthFallback, success = false)
                viewModelScope.launch {
                    showErrorSnackBar(SignInSnackMessages.BIOMETRIC_ERROR)
                    currentState = SignInStates.IDLE
                }
            }
        )
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
                        logger.log(LogTag.SignInVM.Message.NotPending, success = true)
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
    BIOMETRIC_ERROR,
    NONE,
}

sealed class SignInViewEvents : CommonViewModelEventsInterface {
    data class StartSignInProcess(val name: String) : SignInViewEvents()
    data class UpdateName(val name: String) : SignInViewEvents()
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
    NONE,
}