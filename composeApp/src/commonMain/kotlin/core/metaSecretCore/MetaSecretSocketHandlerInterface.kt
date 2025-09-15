package core.metaSecretCore

import kotlinx.coroutines.flow.StateFlow
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

interface MetaSecretSocketHandlerInterface {
    val socketActionType: StateFlow<SocketActionModel>
    fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?)
}