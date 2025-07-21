package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CommonResponseModel(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
) {
    companion object {
        fun fromJson(jsonResponse: String): CommonResponseModel {
            return Json.decodeFromString<CommonResponseModel>(jsonResponse)
        }
    }
}