package sharedData.metaSecretCore

import models.apiModels.MetaSecretCoreStateModel
import models.appInternalModels.SocketRequestModel

interface MetaSecretSocketHandlerInterface {
    fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?)
}