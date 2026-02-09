package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
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
import core.NotificationCoordinatorInterface
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
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
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    // Properties
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent
    private val _nameText = MutableStateFlow("")
    val nameText: StateFlow<String> = _nameText
    private var _isNameError = MutableStateFlow(false)
    val isNameError: StateFlow<Boolean> = _isNameError

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
            val stateModel = metaSecretAppManager.getStateModel()
            val appState = stateModel?.getCurrentAppState()
            logger.setVaultState(appState?.description())
            
            appState?.let { state ->
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
            logger.setVaultState(null)
            currentState = SignInStates.IDLE
        }
    }

    // Resolve all states of the screen
    private suspend fun signInStateResolver() {
            when (currentState) {
                SignInStates.IDLE -> logger.log(LogTag.SignInVM.Message.WaitingForSignUp, success = true)
                SignInStates.START_SIGN_IN -> {
                    _isNameError.value = false
                    val name = _nameText.value
                    if (name.isNotEmpty()) {
                        isNameError(name)
                    } else {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.NAME_INCORRECT -> {
                    _isNameError.value = true
                }
                SignInStates.NAME_SUCCEEDED -> generateMasterKey()
                SignInStates.MASTER_KEY_GENERATED -> {
                    val name = _nameText.value
                    if (name.isNotEmpty()) {
                        firstSignUp(name)
                    } else {
                        currentState = SignInStates.NAME_INCORRECT
                    }
                }
                SignInStates.MASTER_KEY_FAILED -> {
                    showNotification(message = stringProvider.errorInternal(), isError = true)
                }
                SignInStates.SIGN_IN_PENDING -> viewModelScope.launch { showPendingState() }
                SignInStates.SIGN_IN_REJECTED -> {
                    _isLoading.value = false
                    _nameText.value = ""
                }
                SignInStates.SIGN_IN_COMPLETED -> _navigationEvent.value = true
                SignInStates.SIGN_IN_FAILED -> {
                    showNotification(message = stringProvider.errorInternal(), isError = true)
                }
                SignInStates.NONE -> {}
                null -> {}
            }
    }

    private fun showPendingState() {
        _isLoading.value = true
        logger.log(LogTag.SignInVM.Message.StartListeningJoinAccept, success = true)
        showNotification(stringProvider.acceptRequestOnOtherDevice(), isError = false)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
            exclude = null
        )
    }
    
    fun showNotification(message: String, isError: Boolean) {
        if (isError) {
            notificationCoordinator.showError(message)
        } else {
            notificationCoordinator.showSuccess(message)
        }
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

        try {
            if (initAppManagerResult()) {
                logger.log(LogTag.SignInVM.Message.StartSignUp, success = true)
                val signUpResult = withContext(Dispatchers.IO) {
                    metaSecretStateResolver.startFirstSignUp(vaultName)
                }

                if (signUpResult.error != null) {
                    logger.log(LogTag.SignInVM.Message.SignUpUnknownState, success = false)
                    showNotification( stringProvider.errorInternal(), isError = true)
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
                logger.log(LogTag.SignInVM.Message.SignUpUnknownState, success = false)
                showNotification( stringProvider.errorInternal(), isError = true)
                viewModelScope.launch {
                    currentState = SignInStates.SIGN_IN_FAILED
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun generateMasterKey() {
        _isLoading.value = true
        val jsonResponse = withContext(Dispatchers.IO) {
            metaSecretCore.generateMasterKey()
        }
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
                showNotification(error.ifEmpty { stringProvider.errorBiometricAuthFailed() }, isError = true)
                viewModelScope.launch {
                    currentState = SignInStates.IDLE
                }
            },
            onFallback = {
                logger.log(LogTag.SignInVM.Message.BiometricAuthFallback, success = false)
                showNotification(stringProvider.errorBiometricAuthFailed(), isError = true)
                viewModelScope.launch {
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
                    val state = appState?.getCurrentAppState()
                    logger.setVaultState(state?.description())
                    
                    val vaultState = appState?.getVaultFullInfo()
                    val outsiderState = appState?.getOutsiderStatus()

                    if ( vaultState is VaultFullInfo.Outsider && outsiderState == UserDataOutsiderStatus.PENDING) {
                        currentState = SignInStates.SIGN_IN_PENDING
                    } else {
                        logger.log(LogTag.SignInVM.Message.NotPending, success = true)
                    }
                }
                else -> {
                    logger.setVaultState(null)
                }
            }
        }
    }

    private suspend fun initAppManagerResult(): Boolean {
        return metaSecretAppManager.initWithSavedKey() is InitResult.Success
    }
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