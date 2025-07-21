package scenes.devicesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import models.apiModels.UserStatus
import models.appInternalModels.DeviceCellModel
import models.appInternalModels.DeviceStatus
import scenes.common.CommonViewModel
import scenes.common.CommonViewModelEventsInterface
import sharedData.metaSecretCore.MetaSecretAppManagerInterface

class DevicesScreenViewModel(
    private val appManager: MetaSecretAppManagerInterface
) : ViewModel(), CommonViewModel {

    private val _devicesList = MutableStateFlow<List<DeviceCellModel>>(emptyList())
    val devicesList = _devicesList.asStateFlow()

    private val _vaultName = MutableStateFlow<String?>(null)
    val vaultName = _vaultName.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is DeviceViewEvents) {
            when (event) {
                DeviceViewEvents.ON_APPEAR -> onAppear()
            }
        }
    }

    private fun onAppear() {
        loadDevicesList()
    }

    private fun loadDevicesList() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val vaultSummary = withContext(Dispatchers.Default) {
                    appManager.getVaultSummary()
                }

                val devices = vaultSummary?.users?.map { (_, userInfo) ->
                    DeviceCellModel(
                        status = when (userInfo.status) {
                            UserStatus.MEMBER -> DeviceStatus.Member
                            UserStatus.PENDING -> DeviceStatus.Pending
                            else -> DeviceStatus.Unknown
                        },
                        secretsCount = vaultSummary.secretsCount,
                        devicesCount = vaultSummary.users.size,
                        deviceName = vaultSummary.vaultName
                    )
                } ?: emptyList()

                _devicesList.value = devices
                _vaultName.value = vaultSummary?.vaultName
            } finally {
                _isLoading.value = false
            }
        }
    }
}

enum class DeviceViewEvents: CommonViewModelEventsInterface {
    ON_APPEAR
}