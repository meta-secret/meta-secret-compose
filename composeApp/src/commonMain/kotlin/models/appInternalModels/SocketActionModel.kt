package models.appInternalModels

enum class SocketActionModel {
    ASK_TO_JOIN,
    JOIN_REQUEST_ACCEPTED,
    JOIN_REQUEST_DECLINED,
    JOIN_REQUEST_PENDING,
    UPDATE_STATE,
    NONE,
}