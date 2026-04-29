package ui.scenes.devicesscreen

import androidx.lifecycle.viewModelScope
import core.AlertCoordinatorInterface
import core.BiometricAuthenticatorInterface
import core.KeyValueStorageInterface
import core.LogTag
import core.NotificationCoordinatorInterface
import core.ScreenMetricsProviderInterface
import core.StringProviderInterface
import core.VaultStatsProviderInterface
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.apiModels.UserStatus
import models.appInternalModels.DeviceCellModel
import models.appInternalModels.DeviceStatus
import models.appInternalModels.SocketActionModel
import models.appInternalModels.UpdateMemberActionModel
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class DevicesScreenViewModel(
    val screenMetricsProvider: ScreenMetricsProviderInterface,
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val appManager: MetaSecretAppManagerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val vaultStatsProvider: VaultStatsProviderInterface,
    private val alertCoordinator: AlertCoordinatorInterface,
    private val biometricAuthenticator: BiometricAuthenticatorInterface,
    private val notificationCoordinator: NotificationCoordinatorInterface,
    private val stringProvider: StringProviderInterface,
) : CommonViewModel() {

    private val _devicesList = MutableStateFlow<List<DeviceCellModel>>(emptyList())
    val devicesList = _devicesList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentDeviceId = MutableStateFlow<String?>(null)
    val currentDeviceId: String?
        get() = keyValueStorage.cachedDeviceId

    val vaultName = vaultStatsProvider.vaultName
    
    init {
        alertCoordinator.setJoinRequestHandler { isAccepted ->
            if (isAccepted) {
                handle(DeviceViewEvents.Accept)
            } else {
                handle(DeviceViewEvents.Decline)
            }
        }
        
        viewModelScope.launch {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    logger.log(LogTag.DevicesVM.Message.JoinRequestStateReceived, success = true)
                    loadDevicesList(true)
                }
            }
        }

        viewModelScope.launch {
            vaultStatsProvider.secretsCount.collect { count ->
                _devicesList.value = _devicesList.value.map { it.copy(secretsCount = count) }
            }
        }
        viewModelScope.launch {
            vaultStatsProvider.devicesCount.collect { count ->
                _devicesList.value = _devicesList.value.map { it.copy(devicesCount = count) }
            }
        }
        viewModelScope.launch {
            vaultStatsProvider.vaultName.collect { name ->
                _devicesList.value = _devicesList.value.map { it.copy(vaultName = name ?: it.vaultName) }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is DeviceViewEvents) {
            when (event) {
                DeviceViewEvents.OnAppear -> loadDevicesList(false)
                DeviceViewEvents.Accept -> authenticateAndUpdateMembership(true)
                DeviceViewEvents.Decline -> authenticateAndUpdateMembership(false)
                is DeviceViewEvents.SelectDevice -> {
                    selectCurrentDevice(event.deviceId)
                    event.deviceId?.let { deviceId ->
                        alertCoordinator.showJoinRequest(deviceId)
                    }
                }
            }
        }
    }

    private fun authenticateAndUpdateMembership(isJoin: Boolean) {
        biometricAuthenticator.authenticate(
            onSuccess = { updateMembership(isJoin) },
            onError = { error ->
                logger.log(LogTag.DevicesVM.Message.BiometricError, error, success = false)
                notificationCoordinator.showError(error.ifEmpty { stringProvider.errorBiometricAuthFailed() })
                resetJoinRequestState()
            },
            onFallback = {
                logger.log(LogTag.DevicesVM.Message.BiometricError, success = false)
                notificationCoordinator.showError(stringProvider.errorBiometricAuthFailed())
                resetJoinRequestState()
            }
        )
    }

    private fun loadDevicesList(isSocketAction: Boolean) {
        viewModelScope.launch {
            logger.log(LogTag.DevicesVM.Message.LoadDevicesList, success = true)
            try {
                _isLoading.value = true
                _devicesList.value = fetchDevicesList(isSocketAction)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun resetJoinRequestState() {
        _currentDeviceId.value?.let { deviceId ->
            alertCoordinator.showJoinRequest(deviceId)
        } ?: alertCoordinator.dismissJoinRequest()
    }

    private fun updateMembership(isJoin: Boolean) {
        logger.log(LogTag.DevicesVM.Message.UpdateCandidateStart, success = true)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val action = if (isJoin) { UpdateMemberActionModel.Accept } else { UpdateMemberActionModel.Decline }
                val updateResult = withContext(Dispatchers.IO) {
                    val candidate = _currentDeviceId.value?.let { appManager.getUserDataBy(it) }
                    logger.log(LogTag.DevicesVM.Message.UpdateCandidateSuccess, "$candidate", success = true)
                    if (candidate != null) {
                        appManager.updateMember(candidate, action.name)
                    } else {
                        null
                    }
                }
                if (updateResult?.success == false) {
                    logger.log(LogTag.DevicesVM.Message.UpdateCandidateFailed, "${updateResult.error}", success = false)
                }
                _currentDeviceId.value = null
                alertCoordinator.dismissJoinRequest()
                _devicesList.value = fetchDevicesList(isSocketAction = false)
            } catch (e: Exception) {
                logger.log(LogTag.DevicesVM.Message.UpdateError, "${e.message}", success = false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchDevicesList(isSocketAction: Boolean): List<DeviceCellModel> {
        val vaultSummary = withContext(Dispatchers.IO) {
            appManager.getVaultSummary(isSocketAction)
        }
        val currentId = keyValueStorage.cachedDeviceId

        return vaultSummary?.users?.map { (_, userInfo) ->
            val baseStatus = when (userInfo.status) {
                UserStatus.MEMBER -> DeviceStatus.Member
                UserStatus.PENDING -> DeviceStatus.Pending
                UserStatus.DECLINED -> DeviceStatus.Declined
                UserStatus.NON_MEMBER -> DeviceStatus.Member
            }
            DeviceCellModel(
                id = userInfo.deviceId,
                status = if (userInfo.deviceId == currentId) DeviceStatus.Current else baseStatus,
                secretsCount = vaultStatsProvider.secretsCount.value,
                devicesCount = vaultStatsProvider.devicesCount.value,
                vaultName = vaultStatsProvider.vaultName.value ?: vaultSummary.vaultName,
                deviceName = userInfo.deviceName,
                deviceType = userInfo.deviceType,
                deviceUiCategory = userInfo.deviceUiCategory
            )
        } ?: emptyList()
    }

    private fun selectCurrentDevice(deviceId: String?) {
        logger.log(LogTag.DevicesVM.Message.SelectDevice, "$deviceId", success = true)
        _currentDeviceId.value = deviceId
    }
}

sealed class DeviceViewEvents : CommonViewModelEventsInterface {
    data object OnAppear : DeviceViewEvents()
    data object Accept : DeviceViewEvents()
    data object Decline : DeviceViewEvents()
    data class SelectDevice(val deviceId: String?) : DeviceViewEvents()
}
