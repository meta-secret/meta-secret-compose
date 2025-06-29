package scenes.mainscreen

import androidx.lifecycle.ViewModel
import models.appInternalModels.SocketRequestModel
import sharedData.metaSecretCore.MetaSecretAppManager
import sharedData.metaSecretCore.MetaSecretSocketHandlerInterface
import storage.KeyValueStorage
import ui.TabStateHolder

class MainScreenViewModel(
    private val socketHandler: MetaSecretSocketHandlerInterface
) : ViewModel() {


    init {
        socketHandler.actionsToFollow(
            listOf(SocketRequestModel.RESPONSIBLE_TO_ACCEPT_JOIN),
            exclude = null
        )
    }

    fun setTabIndex(index: Int) {
        TabStateHolder.setTabIndex(index)
    }
}
