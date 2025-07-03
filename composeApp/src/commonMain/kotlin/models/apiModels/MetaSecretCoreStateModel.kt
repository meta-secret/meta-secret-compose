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
    
    @SerialName("vaultNotExists")
    VAULT_NOT_EXISTS,
    
    @SerialName("member")
    MEMBER,
    
    @SerialName("outsider")
    OUTSIDER
}

@Serializable
enum class OutsiderStatus {
    @SerialName("nonMember")
    NON_MEMBER,
    
    @SerialName("pending")
    PENDING,
    
    @SerialName("declined")
    DECLINED
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
data class UserData(
    val device: DeviceInfo,
    val vaultName: String
)

@Serializable
data class OutsiderInfo(
    val status: OutsiderStatus,
    val userData: UserData
)

@Serializable
data class VaultInfo(
    val notExists: NotExistsInfo? = null,
    val outsider: OutsiderInfo? = null
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
    val message: JsonElement? = null,
    val success: Boolean = false
) {
    fun getState(): StateType? {
        val messageObj = message as? JsonObject ?: return null
        val stateElement = messageObj["state"] ?: return null
        
        if (stateElement is JsonPrimitive) {
            return when (stateElement.content) {
                "local" -> StateType.LOCAL
                "member" -> StateType.MEMBER
                "outsider" -> StateType.OUTSIDER
                "vaultNotExists" -> StateType.VAULT_NOT_EXISTS
                else -> null
            }
        } else {
            val stateObj = stateElement as JsonObject
            if (stateObj.containsKey("vault")) {
                val vaultObj = stateObj["vault"] as? JsonObject
                if (vaultObj != null && vaultObj.containsKey("outsider")) {
                    return StateType.OUTSIDER
                }
                return StateType.VAULT
            }
            return null
        }
    }
    
    fun getVaultInfo(): VaultInfo? {
        val messageObj = message as? JsonObject ?: return null
        val stateElement = messageObj["state"] ?: return null
        
        if (stateElement is JsonObject && stateElement.containsKey("vault")) {
            val stateObj = Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(StateMessageComplex.serializer(), messageObj)
            return stateObj.state.vault
        }
        return null
    }
    
    fun getOutsiderStatus(): OutsiderStatus? {
        val vaultInfo = getVaultInfo() ?: return null
        return vaultInfo.outsider?.status
    }
    
    companion object {
        fun fromJson(jsonResponse: String): MetaSecretCoreStateModel {
            val json = Json { 
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            return try {
                json.decodeFromString<MetaSecretCoreStateModel>(jsonResponse)
            } catch (e: Exception) {
                println("⛔ Failed to parse JSON: $jsonResponse, error: ${e.message}")
                MetaSecretCoreStateModel(message = null, success = false)
            }
        }
    }
}