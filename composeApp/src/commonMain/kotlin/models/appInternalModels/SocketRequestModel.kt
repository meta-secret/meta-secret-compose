package models.appInternalModels

enum class SocketRequestModel {
    WAIT_FOR_JOIN_APPROVE,
    RESPONSIBLE_TO_ACCEPT_JOIN,
    GET_STATE,
    WAIT_FOR_RECOVER_REQUEST,
    NONE,
}