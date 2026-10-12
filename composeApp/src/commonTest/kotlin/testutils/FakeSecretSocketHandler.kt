package testutils

import core.metaSecretCore.MetaSecretSocketHandlerInterface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import models.appInternalModels.RestoreData
import models.appInternalModels.SocketActionModel
import models.appInternalModels.SocketRequestModel

class FakeSecretSocketHandler : MetaSecretSocketHandlerInterface {
        override val socketActionType = MutableStateFlow<SocketActionModel>(SocketActionModel.NONE)
        override val socketActions = MutableSharedFlow<SocketActionModel>()
        override val pendingRecoveryRequests = MutableStateFlow<List<RestoreData>>(emptyList())
        override fun actionsToFollow(add: List<SocketRequestModel>?, exclude: List<SocketRequestModel>?) = Unit
        override fun pauseRefreshes() = Unit
        override fun resumeRefreshes() = Unit
        override fun onAppLaunch() = Unit
        override fun onAppForeground() = Unit
        override fun onAppBackground() = Unit
        override fun refreshAppState() = Unit
        override fun setProcessingSecretName(secretName: String) = Unit
        override fun resetReadyToRecoverDedup(claimId: String?) = Unit
        override fun resetSocketActionType(expected: SocketActionModel?) = Unit
    }
