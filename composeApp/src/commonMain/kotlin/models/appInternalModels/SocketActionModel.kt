package models.appInternalModels

sealed class SocketActionModel {
    data object NONE : SocketActionModel()
    data object ASK_TO_JOIN : SocketActionModel()
    data object JOIN_REQUEST_ACCEPTED : SocketActionModel()
    data object JOIN_REQUEST_DECLINED : SocketActionModel()
    data object JOIN_REQUEST_PENDING : SocketActionModel()
    data object UPDATE_STATE : SocketActionModel()
    data class READY_TO_RECOVER(val restoreData: List<RestoreData>) : SocketActionModel()
    data class RECOVER_SENT(val secretId: String) : SocketActionModel()
}

data class RestoreData(val claimId: String, val secretId: String)