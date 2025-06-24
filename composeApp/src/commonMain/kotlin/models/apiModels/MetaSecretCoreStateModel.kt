package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StateType {
    @SerialName("local")
    LOCAL,
    
    @SerialName("vault")
    VAULT
}

@Serializable
data class StateMessage(
    val state: StateType
)

@Serializable
data class MetaSecretCoreStateModel(
    val message: StateMessage,
    val success: Boolean
) {
    companion object {
        fun fromJson(jsonResponse: String): MetaSecretCoreStateModel {
            return Json.decodeFromString<MetaSecretCoreStateModel>(jsonResponse)
        }
    }
}