package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.metaSecretCore.InitResult
import core.metaSecretCore.MemberState
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretCoreInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.MetaSecretStateResolverInterface
import core.metaSecretCore.OutsiderState
import core.metaSecretCore.VaultAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.apiModels.MasterKeyModel
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.EmailProvider
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class EmailConfirmationScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val stringProvider: StringProviderInterface,
    private val vaultName: String,
    private val provider: EmailProvider,
) : CommonViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigationEvent = MutableStateFlow<EmailConfirmationNavigationEvent?>(EmailConfirmationNavigationEvent.Idle)
    val navigationEvent: StateFlow<EmailConfirmationNavigationEvent?> = _navigationEvent

    private val _screenState = MutableStateFlow<EmailConfirmationScreenState>(EmailConfirmationScreenState.Default)
    val screenState: StateFlow<EmailConfirmationScreenState> = _screenState

    init {
        viewModelScope.launch {
            resumePendingJoinIfNeeded()
        }
        socketSubscribe()
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        when (event) {
            EmailConfirmationViewEvents.ContinueClicked -> {
                logger.log(LogTag.SignInVM.Message.EmailConfirmationContinueClicked, success = true)
                viewModelScope.launch {
                    startSignUpFlow()
                }
            }

            EmailConfirmationViewEvents.JoinExistingVault -> {
                logger.log(
                    LogTag.SignInVM.Message.EmailConfirmationContinueClicked,
                    "join existing vault",
                    success = true
                )
                viewModelScope.launch {
                    continueSignUpFlow()
                }
            }

            EmailConfirmationViewEvents.CancelJoin -> {
                logger.log(LogTag.SignInVM.Message.EmailConfirmationChangeClicked, "cancel join", success = false)
                viewModelScope.launch {
                    clearJoinAttemptAndReturn()
                }
            }

            EmailConfirmationViewEvents.StartOver -> {
                logger.log(LogTag.SignInVM.Message.EmailConfirmationChangeClicked, "start over", success = false)
                viewModelScope.launch {
                    clearJoinAttemptAndReturn()
                }
            }
        }
    }

    fun consumeNavigationEvent() {
        _navigationEvent.value = EmailConfirmationNavigationEvent.Idle
    }

    private suspend fun resumePendingJoinIfNeeded() {
        if (initAppManagerResult()) {
            val stateModel = metaSecretAppManager.getStateModel()
            val appState = stateModel?.getCurrentAppState()
            logger.setVaultState(appState?.description())

            val vaultState = stateModel?.getVaultFullInfo()
            val outsiderState = stateModel?.getOutsiderStatus()

            when {
                vaultState is VaultFullInfo.Outsider && outsiderState == UserDataOutsiderStatus.PENDING -> {
                    logger.log(LogTag.SignInVM.Message.UserIsOutsider, success = true)
                    showJoinPendingState()
                }

                vaultState is VaultFullInfo.Member -> {
                    logger.log(LogTag.SignInVM.Message.UserIsMember, success = true)
                    _navigationEvent.value = EmailConfirmationNavigationEvent.MainScreen
                }

                else -> {
                    logger.log(LogTag.SignInVM.Message.NotPending, success = true)
                }
            }
        } else {
            logger.setVaultState(null)
            resetScreenState()
        }
    }

    private suspend fun startSignUpFlow() {
        _isLoading.value = true
        resetScreenState()

        if (!generateMasterKey()) {
            failWithInternalError()
            return
        }

        if (!initAppManagerResult()) {
            failWithInternalError()
            return
        }

        when (val prepareResult = metaSecretStateResolver.prepareSignUp(vaultName)) {
            else -> {
                if (prepareResult.error != null || prepareResult.availability == null) {
                    failWithInternalError()
                    return
                }

                when (prepareResult.availability) {
                    VaultAvailability.AVAILABLE -> continueSignUpFlow()
                    VaultAvailability.EXISTS -> showJoinDecisionState()
                }
            }
        }
    }

    private suspend fun continueSignUpFlow() {
        _isLoading.value = true
        resetScreenState()

        val signUpResult = metaSecretStateResolver.continueSignUp()
        if (signUpResult.error != null) {
            failWithInternalError()
            return
        }

        when (signUpResult.appState) {
            is MemberState -> {
                _isLoading.value = false
                _navigationEvent.value = EmailConfirmationNavigationEvent.MainScreen
            }

            is OutsiderState -> {
                showJoinPendingState()
            }

            else -> failWithInternalError()
        }
    }

    private fun showJoinDecisionState() {
        _isLoading.value = false
        _screenState.value = EmailConfirmationScreenState.VaultExists
        showNotification(stringProvider.nameOccupiedJoinPrompt(), isError = false)
        socketHandler.actionsToFollow(
            add = null,
            exclude = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
        )
    }

    private suspend fun showJoinPendingState() {
        _isLoading.value = false
        _screenState.value = EmailConfirmationScreenState.JoiningPending
        keyChainManager.saveString("pending_vault_email", vaultName)
        keyChainManager.saveString("pending_email_provider", provider.name)
        showNotification(stringProvider.acceptRequestOnOtherDevice(), isError = false)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
            exclude = null
        )
    }

    private suspend fun clearJoinAttemptAndReturn() {
        _isLoading.value = false
        metaSecretStateResolver.clearPreparedSignUp()
        keyValueStorage.cachedDeviceId = null
        keyValueStorage.cachedVaultName = null
        keyChainManager.clearAll(isCleanDB = true)
        socketHandler.actionsToFollow(
            add = null,
            exclude = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
        )
        resetScreenState()
        _navigationEvent.value = EmailConfirmationNavigationEvent.BackToSignIn
    }

    private suspend fun failWithInternalError() {
        _isLoading.value = false
        resetScreenState()
        showNotification(stringProvider.errorInternal(), isError = true)
    }

    private suspend fun generateMasterKey(): Boolean {
        val jsonResponse = metaSecretCore.generateMasterKey()
        val model = MasterKeyModel.fromJson(jsonResponse)

        if (model.success && !model.masterKey.isNullOrEmpty()) {
            logger.log(LogTag.SignInVM.Message.GeneratedMasterKey, "$model", success = true)
            val saved = keyChainManager.saveString("master_key", model.masterKey)
            logger.setMasterKeyGenerated(saved)
            return saved
        }

        logger.setMasterKeyGenerated(false)
        return false
    }

    private fun socketSubscribe() {
        viewModelScope.launch {
            socketHandler.socketActionType.collect { actionType ->
                logger.log(LogTag.SignInVM.Message.SubscribeJoinResponse, success = true)
                when (actionType) {
                    SocketActionModel.JOIN_REQUEST_ACCEPTED -> {
                        if (_screenState.value is EmailConfirmationScreenState.JoiningPending) {
                            handleJoinRequestAccepted()
                        } else {
                            logger.log(
                                LogTag.SignInVM.Message.JoiningNotFollowing,
                                "screenState=${_screenState.value}, ignoring JOIN_REQUEST_ACCEPTED",
                                success = true
                            )
                        }
                    }

                    SocketActionModel.JOIN_REQUEST_DECLINED -> {
                        logger.log(LogTag.SignInVM.Message.GotDeclinedSignal, success = false)
                        viewModelScope.launch {
                            clearJoinAttemptAndReturn()
                        }
                    }

                    SocketActionModel.JOIN_REQUEST_PENDING -> {
                        logger.log(LogTag.SignInVM.Message.JoiningInProgress, success = true)
                        if (_screenState.value !is EmailConfirmationScreenState.JoiningPending) {
                            showJoinPendingState()
                        }
                    }

                    else -> {
                        logger.log(LogTag.SignInVM.Message.JoiningNotFollowing, "$actionType", success = true)
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
                    _navigationEvent.value = EmailConfirmationNavigationEvent.MainScreen
                }
            },
            onError = { error ->
                logger.log(LogTag.SignInVM.Message.BiometricAuthFailed, error, success = false)
                showNotification(error.ifEmpty { stringProvider.errorBiometricAuthFailed() }, isError = true)
                viewModelScope.launch {
                    resetScreenState()
                }
            },
            onFallback = {
                logger.log(LogTag.SignInVM.Message.BiometricAuthFallback, success = false)
                showNotification(stringProvider.errorBiometricAuthFailed(), isError = true)
                viewModelScope.launch {
                    resetScreenState()
                }
            }
        )
    }

    private suspend fun initAppManagerResult(): Boolean {
        return metaSecretAppManager.initWithSavedKey() is InitResult.Success
    }

    private fun resetScreenState() {
        _screenState.value = EmailConfirmationScreenState.Default
    }
}

sealed class EmailConfirmationViewEvents : CommonViewModelEventsInterface {
    data object ContinueClicked : EmailConfirmationViewEvents()
    data object JoinExistingVault : EmailConfirmationViewEvents()
    data object CancelJoin : EmailConfirmationViewEvents()
    data object StartOver : EmailConfirmationViewEvents()
}

sealed class EmailConfirmationScreenState {
    data object Default : EmailConfirmationScreenState()
    data object VaultExists : EmailConfirmationScreenState()
    data object JoiningPending : EmailConfirmationScreenState()
}

sealed class EmailConfirmationNavigationEvent {
    data object Idle : EmailConfirmationNavigationEvent()
    data object MainScreen : EmailConfirmationNavigationEvent()
    data object BackToSignIn : EmailConfirmationNavigationEvent()
}
