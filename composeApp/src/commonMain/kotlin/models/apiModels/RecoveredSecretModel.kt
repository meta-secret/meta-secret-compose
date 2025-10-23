package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RecoveredSecretMessage(
    val secret: String
)

@Serializable
data class RecoveredSecretModel (
    val success: Boolean,
    val message: RecoveredSecretMessage? = null,
    val error: String? = null
) {
    companion object {
        fun fromJson(jsonResponse: String): RecoveredSecretModel {
            return Json.decodeFromString<RecoveredSecretModel>(jsonResponse)
        }
    }
}