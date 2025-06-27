package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class StateType {
    @SerialName("local")
    LOCAL,
    
    @SerialName("vault")
    VAULT,
    
    @SerialName("member")
    MEMBER,
    
    @SerialName("outsider")
    OUTSIDER
}

@Serializable
data class DeviceKeys(
    val dsaPk: String,
    val transportPk: String
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val keys: DeviceKeys
)

@Serializable
data class NotExistsInfo(
    val device: DeviceInfo,
    val vaultName: String
)

@Serializable
data class VaultInfo(
    val notExists: NotExistsInfo? = null
)

@Serializable
data class StateValue(
    val local: String? = null,
    val vault: VaultInfo? = null,
    val member: String? = null,
    val outsider: String? = null
)

@Serializable
data class StateMessageComplex(
    val state: StateValue
)

@Serializable
data class StateMessageSimple(
    val state: String
)

@Serializable
data class MetaSecretCoreStateModel(
    val message: JsonElement?,
    val success: Boolean
) {
    fun getState(): StateType? {
        val messageObj = message as JsonObject
        val stateElement = messageObj["state"] ?: return null
        
        if (stateElement is JsonPrimitive) {
            return when (stateElement.content) {
                "local" -> StateType.LOCAL
                "member" -> StateType.MEMBER
                "outsider" -> StateType.OUTSIDER
                else -> null
            }
        } else {
            val stateObj = stateElement as JsonObject
            if (stateObj.containsKey("vault")) {
                return StateType.VAULT
            }
            return null
        }
    }
    
    fun getVaultInfo(): VaultInfo? {
        val messageObj = message as JsonObject
        val stateElement = messageObj["state"] ?: return null
        
        if (stateElement is JsonObject && stateElement.containsKey("vault")) {
            val stateObj = Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(StateMessageComplex.serializer(), messageObj)
            return stateObj.state.vault
        }
        return null
    }
    
    companion object {
        fun fromJson(jsonResponse: String): MetaSecretCoreStateModel {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString<MetaSecretCoreStateModel>(jsonResponse)
        }
    }
}