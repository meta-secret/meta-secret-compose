package core.metaSecretCore

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

interface MetaSecretSocketHandlerInterface {
    val socketActionType: StateFlow<SocketActionModel>
    val socketActions: SharedFlow<SocketActionModel>
    fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?)
    fun pauseRefreshes()
    fun resumeRefreshes()
    fun onAppLaunch()
    fun onAppForeground()
    fun onAppBackground()
    fun refreshAppState()
    fun setProcessingSecretName(secretName: String)
    fun resetReadyToRecoverDedup(claimId: String?)
    fun resetSocketActionType(expected: SocketActionModel? = null)
}
