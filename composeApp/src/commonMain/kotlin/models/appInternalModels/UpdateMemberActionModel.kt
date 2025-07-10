package models.appInternalModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UpdateMemberActionModel {
    @SerialName("Accept")
    Accept,

    @SerialName("Decline")
    Decline,
}