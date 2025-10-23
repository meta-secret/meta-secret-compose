package ui.scenes.mainscreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import core.metaSecretCore.MetaSecretAppManagerInterface
import core.BackupCoordinatorInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.LogTags
import core.Device
import core.KeyValueStorageInterface
import core.ScreenMetricsProviderInterface
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import models.appInternalModels.ClaimModel
import models.appInternalModels.RestoreData
import ui.TabStateHolder
import ui.scenes.common.CommonViewModel
import ui.scenes.common.CommonViewModelEventsInterface

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface,
    private val metaSecretAppManager: MetaSecretAppManagerInterface,
    private val keyValueStorage: KeyValueStorageInterface,
    private val backupCoordinatorInterface: BackupCoordinatorInterface,
    val screenMetricsProvider: ScreenMetricsProviderInterface,
) : ViewModel(), CommonViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _joinRequestsCount = MutableStateFlow<Int?>(null)
    val joinRequestsCount: StateFlow<Int?> = _joinRequestsCount

    private val _isWarningShown = MutableStateFlow(false)
    val isWarningShown: StateFlow<Boolean> = _isWarningShown

    private val recoverQueue: ArrayDeque<RestoreData> = ArrayDeque()
    private val _recoverDialog = MutableStateFlow<RestoreData?>(null)
    val recoverDialog: StateFlow<RestoreData?> = _recoverDialog
    
    private val _secretIdToShow = MutableStateFlow<String?>(null)
    val secretIdToShow: StateFlow<String?> = _secretIdToShow

    private val devicesList: StateFlow<List<Device>> = keyValueStorage.deviceData
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val devicesCount: StateFlow<Int> = devicesList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        checkBackup()
        println("✅${LogTags.MAIN_VM}: Start to follow RESPONSIBLE_TO_ACCEPT_JOIN")
        socketHandler.actionsToFollow(
            add = listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN, SocketRequestModel.WAIT_FOR_RECOVER_REQUEST,
                SocketRequestModel.SHOW_SECRET),
            exclude = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            socketHandler.socketActionType.collect { actionType ->
                if (actionType == SocketActionModel.ASK_TO_JOIN) {
                    println("✅${LogTags.MAIN_VM}: New state for Join request has been gotten")

                    val count = metaSecretAppManager.getJoinRequestsCount()
                    withContext(Dispatchers.Main) {
                        _joinRequestsCount.value = count
                    }
                }

                when (val a = actionType) {
                    is SocketActionModel.READY_TO_RECOVER -> {
                        val restoreData = a.restoreData
                        println("✅${LogTags.MAIN_VM}: READY_TO_RECOVER signal has been caught $restoreData")

                        val secrets = metaSecretAppManager.getSecretsFromVault()
                        val existingSecretsIds = secrets?.map { it.name }?.toSet()
                        println("✅${LogTags.MAIN_VM}: READY_TO_RECOVER existingSecretsIds $existingSecretsIds")

                        val newRequests = restoreData.filter { restoreData ->
                            existingSecretsIds?.contains(restoreData.secretId) == true
                        }
                        println("✅${LogTags.MAIN_VM}: READY_TO_RECOVER newRequests $newRequests")

                        if (newRequests.isEmpty()) {
                            println("✅${LogTags.MAIN_VM}: READY_TO_RECOVER nothing to handle")
                            return@collect
                        }

                        recoverQueue.addAll(newRequests)
                        if (_recoverDialog.value == null) {
                            withContext(Dispatchers.Main) {
                                showNextRecoverPrompt()
                            }
                        }
                    }
                    is SocketActionModel.RECOVER_SENT -> {
                        _secretIdToShow.value = a.secretId
                        println("✅${LogTags.MAIN_VM}: READY_TO_SHOW secret by secretId ${a.secretId}")
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    override fun handle(event: CommonViewModelEventsInterface) {
        if (event is MainViewEvents) {
            when (event) {
                is MainViewEvents.SetTabIndex -> setTabIndex(event.index)
                is MainViewEvents.ShowWarning -> changeWarningVisibilityTo(event.isToShow)
                is MainViewEvents.RecoverDecision -> onRecoverDecision(event.accept)
                is MainViewEvents.DismissRecoverDialog -> {
                    _recoverDialog.value = null
                    showNextRecoverPrompt()
                }
            }
        }
    }

    private fun showNextRecoverPrompt() {
        println("✅${LogTags.MAIN_VM}: showNextRecoverPrompt")
        _recoverDialog.value = if (recoverQueue.isNotEmpty()) recoverQueue.removeFirst() else null
    }

    private fun onRecoverDecision(accept: Boolean) {
        println("✅${LogTags.MAIN_VM}: onRecoverDecision $accept")
        val current = _recoverDialog.value ?: run {
            showNextRecoverPrompt()
            return
        }

        if (!accept) {
            println("✅${LogTags.MAIN_VM}: Recover is declined")
            showNextRecoverPrompt()
            return
        }

        println("✅${LogTags.MAIN_VM}: Recover is accepted")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("✅${LogTags.MAIN_VM}: acceptRecover called for claimId = ${current.claimId}")
                metaSecretAppManager.acceptRecover(ClaimModel(current.claimId))
            } catch (t: Throwable) {
                println("❌${LogTags.MAIN_VM}: acceptRecover failed for claimId = ${current.claimId}: $t")
            } finally {
                withContext(Dispatchers.Main) {
                    showNextRecoverPrompt()
                }
            }
        }
    }

    private fun checkBackup() {
        backupCoordinatorInterface.ensureBackupDestinationSelected()
    }

    fun clearSecretIdToShow() {
        println("✅${LogTags.MAIN_VM}: Clearing secretIdToShow")
        _secretIdToShow.value = null
    }

    private fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }

    private fun changeWarningVisibilityTo(state: Boolean) {
        _isWarningShown.value = state
    }
}

sealed class MainViewEvents : CommonViewModelEventsInterface {
    data class SetTabIndex(val index: Int) : MainViewEvents()
    data class ShowWarning(val isToShow: Boolean) : MainViewEvents()
    data class RecoverDecision(val accept: Boolean) : MainViewEvents()
    data object DismissRecoverDialog: MainViewEvents()
}