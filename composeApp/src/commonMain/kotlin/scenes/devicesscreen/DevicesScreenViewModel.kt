package scenes.devicesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import models.apiModels.UserStatus
import models.appInternalModels.DeviceCellModel
import models.appInternalModels.DeviceStatus
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import models.appInternalModels.UpdateMemberActionModel
import scenes.common.CommonViewModel
import scenes.common.CommonViewModelEventsInterface
import sharedData.metaSecretCore.MetaSecretAppManagerInterface
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface

class DevicesScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val appManager: MetaSecretAppManagerInterface
) : ViewModel(), CommonViewModel {

    private val _devicesList = MutableStateFlow<List<DeviceCellModel>>(emptyList())
    val devicesList = _devicesList.asStateFlow()

    private val _vaultName = MutableStateFlow<String?>(null)
    val vaultName = _vaultName.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentDeviceId = MutableStateFlow<String?>(null)

    init {
        println("✅ DeviceScreenVM: Start to follow RESPONSIBLE_TO_ACCEPT_JOIN")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )

        viewModelScope.launch {
            socketHandler.actionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    println("✅ DeviceScreenVM: New state for Join request has been gotten")
                    loadDevicesList()
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is DeviceViewEvents) {
            when (event) {
                DeviceViewEvents.OnAppear -> loadDevicesList()
                DeviceViewEvents.Accept -> updateMembership(true)
                DeviceViewEvents.Decline -> updateMembership(false)
                is DeviceViewEvents.SelectDevice -> selectCurrentDevice(event.deviceId)
            }
        }
    }

    private fun loadDevicesList() {
        viewModelScope.launch {
            println("✅ DeviceScreenVM: Need to load devices list")
            try {
                _isLoading.value = true

                val vaultSummary = withContext(Dispatchers.Default) {
                    appManager.getVaultSummary()
                }

                val devices = vaultSummary?.users?.map { (_, userInfo) ->
                    DeviceCellModel(
                        id = userInfo.deviceId,
                        status = when (userInfo.status) {
                            UserStatus.MEMBER -> DeviceStatus.Member
                            UserStatus.PENDING -> DeviceStatus.Pending
                            else -> DeviceStatus.Unknown
                        },
                        secretsCount = vaultSummary.secretsCount,
                        devicesCount = vaultSummary.users.size,
                        vaultName = vaultSummary.vaultName
                    )
                } ?: emptyList()

                _devicesList.value = devices
                _vaultName.value = vaultSummary?.vaultName
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateMembership(isJoin: Boolean) {
        println("✅ DeviceScreenVM: Start Update candidate")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val action = if (isJoin) { UpdateMemberActionModel.Accept } else { UpdateMemberActionModel.Decline }
                val updateResult = withContext(Dispatchers.Default) {
                    val candidate = _currentDeviceId.value?.let { appManager.getUserDataBy(it) }
                    println("✅ DeviceScreenVM: Update candidate $candidate")
                    if (candidate != null) {
                        appManager.updateMember(candidate, action.name)
                    } else {
                        throw IllegalStateException("Candidate not found")
                    }
                }
                if (updateResult?.success == false) {
                    println("✅ DeviceScreenVM: Update failed: ${updateResult.error}")
                }
                _currentDeviceId.value = null
            } catch (e: Exception) {
                println("✅ DeviceScreenVM: Update error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun selectCurrentDevice(deviceId: String?) {
        println("✅ DeviceScreenVM: Select device with Id: $deviceId")
        _currentDeviceId.value = deviceId
    }
}

sealed class DeviceViewEvents : CommonViewModelEventsInterface {
    data object OnAppear : DeviceViewEvents()
    data object Accept : DeviceViewEvents()
    data object Decline : DeviceViewEvents()
    data class SelectDevice(val deviceId: String?) : DeviceViewEvents()
}
