package ui.scenes.signinscreen

import androidx.lifecycle.viewModelScope
import core.BiometricAuthenticatorInterface
import core.KeyChainInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.email.EmailProvider
import core.email.EmailSelectionCoordinatorInterface
import core.email.EmailSelectionPlatformConfig
import core.email.EmailSelectionResult
import core.email.isApplePrivateRelayEmail
import core.email.isValidEmail
import core.email.normalizeEmailInput
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
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface
import kotlin.properties.Delegates

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
    private val emailSelectionCoordinator: EmailSelectionCoordinatorInterface,
    private val emailSelectionPlatformConfig: EmailSelectionPlatformConfig,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent

    private val _emailSelectionStep = MutableStateFlow(EmailSelectionStep.PROVIDER_OPTIONS)
    val emailSelectionStep: StateFlow<EmailSelectionStep> = _emailSelectionStep

    private val _selectedEmail = MutableStateFlow("")
    val selectedEmail: StateFlow<String> = _selectedEmail

    private val _manualEmail = MutableStateFlow("")
    val manualEmail: StateFlow<String> = _manualEmail

    private val _manualEmailError = MutableStateFlow<String?>(null)
    val manualEmailError: StateFlow<String?> = _manualEmailError

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _selectedProvider = MutableStateFlow<EmailProvider?>(null)
    val selectedProvider: StateFlow<EmailProvider?> = _selectedProvider

    val providerOrder: List<EmailProvider> = emailSelectionPlatformConfig.providerOrder

    private val _showJoinDecision = MutableStateFlow(false)
    val showJoinDecision: StateFlow<Boolean> = _showJoinDecision

    private val _showJoinPending = MutableStateFlow(false)
    val showJoinPending: StateFlow<Boolean> = _showJoinPending

    private val _isEmailInputLocked = MutableStateFlow(false)
    val isEmailInputLocked: StateFlow<Boolean> = _isEmailInputLocked

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
            when (event) {
                is SignInViewEvents.SelectProvider -> {
                    selectProvider(event.provider)
                }
                SignInViewEvents.StartManualEntry -> {
                    openManualEntry()
                }
                is SignInViewEvents.UpdateManualEmail -> {
                    _manualEmail.value = event.email
                    _manualEmailError.value = null
                }
                SignInViewEvents.SubmitManualEmail -> {
                    submitManualEmail()
                }
                SignInViewEvents.ConfirmEmail -> {
                    confirmSelectedEmail()
                }
                SignInViewEvents.RetryCurrent -> {
                    retryCurrentSelection()
                }
                SignInViewEvents.BackToProviderOptions -> {
                    backToProviderOptions()
                }
                SignInViewEvents.JoinExistingVault -> {
                    currentState = SignInFlowState.VAULT_AVAILABLE
                }
                SignInViewEvents.CancelJoin -> {
                    currentState = SignInFlowState.DECLINED
                }
            }
        }
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
                _isEmailInputLocked.value = false
                _selectedEmail.value = ""
                _manualEmail.value = ""
                _manualEmailError.value = null
                _statusMessage.value = null
                _selectedProvider.value = null
                _emailSelectionStep.value = EmailSelectionStep.PROVIDER_OPTIONS
                logger.log(LogTag.SignInVM.Message.WaitingForSignUp, success = true)
            }
            SignInFlowState.CHECKING_VAULT -> checkVaultState()
            SignInFlowState.VAULT_AVAILABLE -> submitJoinRequest()
            SignInFlowState.VAULT_EXISTS -> {
                _isLoading.value = false
                _showJoinDecision.value = true
                _showJoinPending.value = false
                _isEmailInputLocked.value = true
                _emailSelectionStep.value = EmailSelectionStep.CONFIRMATION
                _statusMessage.value = null
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
                _isEmailInputLocked.value = false
                _emailSelectionStep.value = EmailSelectionStep.ERROR
                _statusMessage.value = stringProvider.errorInternal()
                showNotification(message = stringProvider.errorInternal(), isError = true)
            }
            SignInFlowState.NONE, null -> {}
        }
    }

    private fun selectProvider(provider: EmailProvider) {
        viewModelScope.launch {
            _selectedProvider.value = provider
            _selectedEmail.value = ""
            _manualEmail.value = ""
            _manualEmailError.value = null
            _statusMessage.value = null
            _emailSelectionStep.value = EmailSelectionStep.PROVIDER_OPTIONS
            _isLoading.value = true

            val result = runCatching {
                emailSelectionCoordinator.selectEmail(provider)
            }.getOrElse { throwable ->
                EmailSelectionResult.Error(
                    message = throwable.message ?: stringProvider.emailSelectionCouldNotGetEmail(),
                    provider = provider,
                    cause = throwable::class.simpleName,
                )
            }

            _isLoading.value = false

            when (result) {
                is EmailSelectionResult.Success -> {
                    val normalizedEmail = normalizeEmailInput(result.email)
                    when {
                        normalizedEmail.isBlank() || !isValidEmail(normalizedEmail) -> {
                            setSelectionError(
                                message = stringProvider.emailSelectionCouldNotGetEmail(),
                                provider = provider,
                            )
                        }
                        isApplePrivateRelayEmail(normalizedEmail) -> {
                            setPrivateRelayState(normalizedEmail, provider)
                        }
                        else -> {
                            _selectedProvider.value = result.provider
                            _selectedEmail.value = normalizedEmail
                            _statusMessage.value = null
                            _manualEmailError.value = null
                            _emailSelectionStep.value = EmailSelectionStep.CONFIRMATION
                        }
                    }
                }
                is EmailSelectionResult.PrivateRelay -> {
                    val normalizedEmail = normalizeEmailInput(result.email)
                    setPrivateRelayState(normalizedEmail, result.provider)
                }
                EmailSelectionResult.Cancelled -> {
                    _statusMessage.value = null
                    _emailSelectionStep.value = EmailSelectionStep.PROVIDER_OPTIONS
                }
                is EmailSelectionResult.Error -> {
                    setSelectionError(
                        message = result.message.ifBlank { stringProvider.emailSelectionCouldNotGetEmail() },
                        provider = result.provider ?: provider,
                    )
                }
            }
        }
    }

    private fun openManualEntry() {
        _manualEmailError.value = null
        _statusMessage.value = null
        _manualEmail.value = if (_emailSelectionStep.value == EmailSelectionStep.CONFIRMATION) {
            _selectedEmail.value
        } else {
            ""
        }
        _selectedProvider.value = EmailProvider.MANUAL
        _emailSelectionStep.value = EmailSelectionStep.MANUAL_ENTRY
    }

    private fun submitManualEmail() {
        val normalizedEmail = normalizeEmailInput(_manualEmail.value)
        when {
            normalizedEmail.isBlank() || !isValidEmail(normalizedEmail) -> {
                _manualEmailError.value = stringProvider.emailSelectionInvalidEmail()
            }
            isApplePrivateRelayEmail(normalizedEmail) -> {
                setPrivateRelayState(normalizedEmail, EmailProvider.MANUAL)
            }
            else -> {
                _selectedProvider.value = EmailProvider.MANUAL
                _selectedEmail.value = normalizedEmail
                _manualEmailError.value = null
                _statusMessage.value = null
                _emailSelectionStep.value = EmailSelectionStep.CONFIRMATION
            }
        }
    }

    private fun confirmSelectedEmail() {
        val normalizedEmail = normalizeEmailInput(_selectedEmail.value)
        when {
            normalizedEmail.isBlank() || !isValidEmail(normalizedEmail) -> {
                setSelectionError(
                    message = stringProvider.emailSelectionCouldNotGetEmail(),
                    provider = _selectedProvider.value,
                )
            }
            isApplePrivateRelayEmail(normalizedEmail) -> {
                setPrivateRelayState(normalizedEmail, _selectedProvider.value ?: EmailProvider.APPLE)
            }
            else -> {
                _selectedEmail.value = normalizedEmail
                _statusMessage.value = null
                _manualEmailError.value = null
                _emailSelectionStep.value = EmailSelectionStep.CONFIRMATION
                currentState = SignInFlowState.CHECKING_VAULT
            }
        }
    }

    private fun retryCurrentSelection() {
        val normalizedEmail = normalizeEmailInput(_selectedEmail.value)
        if (normalizedEmail.isNotBlank() && isValidEmail(normalizedEmail) && !isApplePrivateRelayEmail(normalizedEmail)) {
            confirmSelectedEmail()
            return
        }

        val provider = _selectedProvider.value
        if (provider != null && provider != EmailProvider.MANUAL) {
            selectProvider(provider)
        } else {
            backToProviderOptions()
        }
    }

    private fun backToProviderOptions() {
        _isLoading.value = false
        _showJoinDecision.value = false
        _showJoinPending.value = false
        _isEmailInputLocked.value = false
        _manualEmailError.value = null
        _statusMessage.value = null
        _manualEmail.value = ""
        _emailSelectionStep.value = EmailSelectionStep.PROVIDER_OPTIONS
        if (_selectedProvider.value != EmailProvider.MANUAL) {
            _selectedProvider.value = null
        }
    }

    private fun setSelectionError(message: String, provider: EmailProvider?) {
        _selectedProvider.value = provider
        _statusMessage.value = message
        _manualEmailError.value = null
        _emailSelectionStep.value = EmailSelectionStep.ERROR
    }

    private fun setPrivateRelayState(email: String, provider: EmailProvider) {
        _selectedProvider.value = provider
        _selectedEmail.value = normalizeEmailInput(email)
        _manualEmailError.value = null
        _statusMessage.value = stringProvider.emailSelectionPrivateRelayMessage()
        _emailSelectionStep.value = EmailSelectionStep.PRIVATE_RELAY
    }

    private suspend fun checkVaultState() {
        val vaultName = normalizeEmailInput(_selectedEmail.value)
        if (vaultName.isBlank() || !isValidEmail(vaultName)) {
            _manualEmailError.value = stringProvider.emailSelectionInvalidEmail()
            _statusMessage.value = stringProvider.emailSelectionCouldNotGetEmail()
            _emailSelectionStep.value = EmailSelectionStep.ERROR
            currentState = SignInFlowState.IDLE
            return
        }

        _isLoading.value = true
        _showJoinDecision.value = false
        _showJoinPending.value = false
        _isEmailInputLocked.value = true

        if (!generateMasterKey()) {
            currentState = SignInFlowState.FAILED
            return
        }

        if (!initAppManagerResult()) {
            currentState = SignInFlowState.FAILED
            return
        }

        val prepareResult = metaSecretStateResolver.prepareSignUp(vaultName)
        currentState = when {
            prepareResult.error != null -> SignInFlowState.FAILED
            prepareResult.availability == VaultAvailability.AVAILABLE -> SignInFlowState.VAULT_AVAILABLE
            prepareResult.availability == VaultAvailability.EXISTS -> SignInFlowState.VAULT_EXISTS
            else -> SignInFlowState.FAILED
        }
    }

    private suspend fun submitJoinRequest() {
        _isLoading.value = true
        _showJoinDecision.value = false
        _showJoinPending.value = false
        _isEmailInputLocked.value = true

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
        _isEmailInputLocked.value = true
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
        _isEmailInputLocked.value = false
        _manualEmailError.value = null
        _statusMessage.value = null
        _selectedEmail.value = ""
        _manualEmail.value = ""
        _selectedProvider.value = null
        _emailSelectionStep.value = EmailSelectionStep.PROVIDER_OPTIONS
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
    data class SelectProvider(val provider: EmailProvider) : SignInViewEvents()
    data object StartManualEntry : SignInViewEvents()
    data class UpdateManualEmail(val email: String) : SignInViewEvents()
    data object SubmitManualEmail : SignInViewEvents()
    data object ConfirmEmail : SignInViewEvents()
    data object RetryCurrent : SignInViewEvents()
    data object BackToProviderOptions : SignInViewEvents()
    data object JoinExistingVault : SignInViewEvents()
    data object CancelJoin : SignInViewEvents()
}

enum class EmailSelectionStep {
    PROVIDER_OPTIONS,
    MANUAL_ENTRY,
    CONFIRMATION,
    PRIVATE_RELAY,
    ERROR,
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
