package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.NotificationCoordinatorInterface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.MasterKeyModel
import models.apiModels.State
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.EmailProvider
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import kotlin.properties.Delegates

class SignInScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val metaSecretCore: MetaSecretCoreInterface,
    private val metaSecretStateResolver: MetaSecretStateResolverInterface,
    private val keyChainManager: KeyChainInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    val providerOrder = listOf(
        EmailProvider.APPLE,
        EmailProvider.GOOGLE,
        EmailProvider.MANUAL
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent

    private val _showJoinDecision = MutableStateFlow(false)
    val showJoinDecision: StateFlow<Boolean> = _showJoinDecision

    private val _showJoinPending = MutableStateFlow(false)
    val showJoinPending: StateFlow<Boolean> = _showJoinPending

    private var currentState: SignInFlowState? by Delegates.observable(null) { _, _, _ ->
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

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is SignInViewEvents) {
            currentState = when (event) {
                SignInViewEvents.JoinExistingVault -> {
                    SignInFlowState.VAULT_AVAILABLE
                }

                SignInViewEvents.CancelJoin -> {
                    SignInFlowState.DECLINED
                }

                SignInViewEvents.ClearAllData -> {
                    viewModelScope.launch {
                        clearAllData()
                    }
                    SignInFlowState.NONE
                }
            }
        }
    }

    private suspend fun clearAllData() {
        keyChainManager.clearAll(isCleanDB = true)
    }

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
                                SignInFlowState.JOINING
                            }
                            is VaultFullInfo.Member -> {
                                logger.log(LogTag.SignInVM.Message.UserIsMember, success = true)
                                SignInFlowState.JOINED
                            }
                            is VaultFullInfo.NotExists, null -> {
                                logger.log(LogTag.SignInVM.Message.NoVaultInfoIdle, success = true)
                                SignInFlowState.IDLE
                            }
                        }
                    }
                    else -> {
                        currentState = SignInFlowState.IDLE
                    }
                }
            }
        } else {
            logger.setVaultState(null)
            currentState = SignInFlowState.IDLE
        }
    }

    private suspend fun signInStateResolver() {
        when (currentState) {
            SignInFlowState.IDLE -> {
                _isLoading.value = false
                _showJoinDecision.value = false
                _showJoinPending.value = false
                logger.log(LogTag.SignInVM.Message.WaitingForSignUp, success = true)
            }
            SignInFlowState.CHECKING_VAULT -> checkVaultState()
            SignInFlowState.VAULT_AVAILABLE -> submitJoinRequest()
            SignInFlowState.VAULT_EXISTS -> {
                _isLoading.value = false
                _showJoinDecision.value = true
                _showJoinPending.value = false
                showNotification(stringProvider.nameOccupiedJoinPrompt(), isError = false)
                socketHandler.actionsToFollow(
                    add = null,
                    exclude = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
                )
            }
            SignInFlowState.JOINING -> enterJoinPendingState()
            SignInFlowState.JOINED -> _navigationEvent.value = true
            SignInFlowState.DECLINED -> handleDeclinedOrCanceledJoin()
            SignInFlowState.FAILED -> {
                _isLoading.value = false
                _showJoinDecision.value = false
                _showJoinPending.value = false
                showNotification(message = stringProvider.errorInternal(), isError = true)
            }
            SignInFlowState.NONE, null -> {}
        }
    }

    private suspend fun checkVaultState() {
        _isLoading.value = true
        _showJoinDecision.value = false
        _showJoinPending.value = false

        if (!generateMasterKey()) {
            currentState = SignInFlowState.FAILED
            return
        }

        if (!initAppManagerResult()) {
            currentState = SignInFlowState.FAILED
            return
        }
    }

    private suspend fun submitJoinRequest() {
        _isLoading.value = true
        _showJoinDecision.value = false
        _showJoinPending.value = false

        try {
            val signUpResult = metaSecretStateResolver.continueSignUp()
            if (signUpResult.error != null) {
                currentState = SignInFlowState.FAILED
                return
            }

            currentState = when (signUpResult.appState) {
                is MemberState -> SignInFlowState.JOINED
                is OutsiderState -> SignInFlowState.JOINING
                else -> SignInFlowState.FAILED
            }
        } finally {
            if (currentState != SignInFlowState.JOINING) {
                _isLoading.value = false
            }
        }
    }

    private fun enterJoinPendingState() {
        _isLoading.value = false
        _showJoinDecision.value = false
        _showJoinPending.value = true
        showNotification(stringProvider.acceptRequestOnOtherDevice(), isError = false)
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE),
            exclude = null
        )
    }

    private suspend fun handleDeclinedOrCanceledJoin() {
        _isLoading.value = false
        metaSecretStateResolver.clearPreparedSignUp()
        keyValueStorage.cachedDeviceId = null
        keyValueStorage.cachedVaultName = null
        keyChainManager.clearAll(isCleanDB = true)
        socketHandler.actionsToFollow(
            add = null,
            exclude = listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE)
        )
        _showJoinDecision.value = false
        _showJoinPending.value = false
        currentState = SignInFlowState.IDLE
    }

    fun showNotification(message: String, isError: Boolean) {
        if (isError) {
            notificationCoordinator.showError(message)
        } else {
            notificationCoordinator.showSuccess(message)
        }
    }

    private suspend fun generateMasterKey(): Boolean {
        val jsonResponse = withContext(Dispatchers.IO) {
            metaSecretCore.generateMasterKey()
        }
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
                        if (currentState == SignInFlowState.JOINING) {
                            handleJoinRequestAccepted()
                        } else {
                            logger.log(
                                LogTag.SignInVM.Message.JoiningNotFollowing,
                                "currentState=$currentState, ignoring JOIN_REQUEST_ACCEPTED",
                                success = true
                            )
                        }
                    }
                    SocketActionModel.JOIN_REQUEST_DECLINED -> {
                        logger.log(LogTag.SignInVM.Message.GotDeclinedSignal, success = false)
                        currentState = SignInFlowState.DECLINED
                    }
                    SocketActionModel.JOIN_REQUEST_PENDING -> {
                        logger.log(LogTag.SignInVM.Message.JoiningInProgress, success = true)
                        if (currentState != SignInFlowState.JOINING) {
                            currentState = SignInFlowState.JOINING
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
                    currentState = SignInFlowState.JOINED
                }
            },
            onError = { error ->
                logger.log(LogTag.SignInVM.Message.BiometricAuthFailed, error, success = false)
                showNotification(error.ifEmpty { stringProvider.errorBiometricAuthFailed() }, isError = true)
                viewModelScope.launch {
                    currentState = SignInFlowState.IDLE
                }
            },
            onFallback = {
                logger.log(LogTag.SignInVM.Message.BiometricAuthFallback, success = false)
                showNotification(stringProvider.errorBiometricAuthFailed(), isError = true)
                viewModelScope.launch {
                    currentState = SignInFlowState.IDLE
                }
            }
        )
    }

    private fun addSubscriptionToSocket() {
        viewModelScope.launch {
            when (metaSecretAppManager.initWithSavedKey()) {
                is InitResult.Success -> {
                    val appState = metaSecretAppManager.getStateModel()
                    val state = appState?.getCurrentAppState()
                    logger.setVaultState(state?.description())

                    val vaultState = appState?.getVaultFullInfo()
                    val outsiderState = appState?.getOutsiderStatus()

                    if (vaultState is VaultFullInfo.Outsider && outsiderState == UserDataOutsiderStatus.PENDING) {
                        currentState = SignInFlowState.JOINING
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
    data object JoinExistingVault : SignInViewEvents()
    data object CancelJoin : SignInViewEvents()
    data object ClearAllData : SignInViewEvents()
}

private enum class SignInFlowState {
    IDLE,
    CHECKING_VAULT,
    VAULT_AVAILABLE,
    VAULT_EXISTS,
    JOINING,
    JOINED,
    DECLINED,
    FAILED,
    NONE,
}
