package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CommonResponseModel(
    val success: Boolean,
    @SerialName("message") val masterKey: String? = null,
    val error: String? = null
) {
    companion object {
        fun fromJson(jsonResponse: String): MasterKeyModel {
            return Json.decodeFromString<MasterKeyModel>(jsonResponse)
        }
    }
}