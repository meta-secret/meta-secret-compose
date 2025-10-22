package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RecoveredSecretModel (
    val success: Boolean,
    @SerialName("message") val secret: String? = null,
    val error: String? = null
) {
    companion object {
        fun fromJson(jsonResponse: String): RecoveredSecretModel {
            return Json.decodeFromString<RecoveredSecretModel>(jsonResponse)
        }
    }
}