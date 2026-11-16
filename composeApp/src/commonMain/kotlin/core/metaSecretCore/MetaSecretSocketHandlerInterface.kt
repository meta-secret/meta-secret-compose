package core.metaSecretCore

import core.metaSecretCore.liveboard.NativeReadRequirement
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel
import models.appInternalModels.RestoreData

interface MetaSecretSocketHandlerInterface {
    val socketActionType: StateFlow<SocketActionModel>
    val socketActions: SharedFlow<SocketActionModel>
    val pendingRecoveryRequests: StateFlow<List<RestoreData>>
    fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?)
    suspend fun pauseRefreshesAndAwait() { pauseRefreshes() }
    fun pauseRefreshes()
    fun resumeRefreshes()
    fun onAppLaunch()
    fun onAppForeground()
    fun onAppBackground()
    fun refreshAppState()
    fun refreshAppState(required: NativeReadRequirement)
    fun setProcessingSecretName(secretName: String)
    fun resetReadyToRecoverDedup(claimId: String?)
    fun resetSocketActionType(expected: SocketActionModel? = null)
}
