package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject

object StateSerializer : KSerializer<State> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("State")

    override fun serialize(encoder: Encoder, value: State) {
        when (value) {
            is State.Local -> encoder.encodeSerializableValue(State.Local.serializer(), value)
            is State.Vault -> encoder.encodeSerializableValue(State.Vault.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): State {
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        val jsonObject = jsonElement.jsonObject

        return when {
            jsonObject.containsKey("local") -> {
                JsonConfig.json.decodeFromJsonElement(State.Local.serializer(), jsonElement)
            }
            jsonObject.containsKey("vault") -> {
                JsonConfig.json.decodeFromJsonElement(State.Vault.serializer(), jsonElement)
            }
            else -> throw IllegalArgumentException("Unknown state type in JSON: ${jsonObject.keys}")
        }
    }
}

object VaultFullInfoSerializer : KSerializer<VaultFullInfo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VaultFullInfo")

    override fun serialize(encoder: Encoder, value: VaultFullInfo) {
        when (value) {
            is VaultFullInfo.NotExists -> encoder.encodeSerializableValue(VaultFullInfo.NotExists.serializer(), value)
            is VaultFullInfo.Outsider -> encoder.encodeSerializableValue(VaultFullInfo.Outsider.serializer(), value)
            is VaultFullInfo.Member -> encoder.encodeSerializableValue(VaultFullInfo.Member.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): VaultFullInfo {
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        val jsonObject = jsonElement.jsonObject

        return when {
            jsonObject.containsKey("notExists") -> {
                JsonConfig.json.decodeFromJsonElement(VaultFullInfo.NotExists.serializer(), jsonElement)
            }
            jsonObject.containsKey("outsider") -> {
                JsonConfig.json.decodeFromJsonElement(VaultFullInfo.Outsider.serializer(), jsonElement)
            }
            jsonObject.containsKey("member") -> {
                JsonConfig.json.decodeFromJsonElement(VaultFullInfo.Member.serializer(), jsonElement)
            }
            else -> throw IllegalArgumentException("Unknown vault full info type in JSON: ${jsonObject.keys}")
        }
    }
}

object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        classDiscriminator = "type"
    }
}

@Serializable
enum class UserDataOutsiderStatus {
    @SerialName("nonMember")
    NON_MEMBER,
    @SerialName("pending")
    PENDING,
    @SerialName("declined")
    DECLINED
}

@Serializable
data class VaultData(
    val vaultName: String,
//    val users: HashMap<DeviceId, UserMembership>,
//    val secrets: HashSet<MetaPasswordId>,
)

@Serializable
data class UserDataMember(
    val userData: UserData
)

@Serializable
data class VaultMember(
    val member: UserDataMember,
    val vault: VaultData,
)

@Serializable
data class UserMemberFullInfo(
    val member: VaultMember,
//    val ss_claims: SsLogData,
//    val vault_events: VaultActionEvents,
)

@Serializable
data class UserDataOutsider(
    val userData: UserData,
    val status: UserDataOutsiderStatus,
)

@Serializable
data class OpenBox(
    val dsaPk: String,
    val transportPk: String
)

@Serializable
data class DeviceData(
    val deviceId: String,
    val deviceName: String,
    val keys: OpenBox
)

@Serializable
data class UserData(
    val device: DeviceData,
    val vaultName: String
)

@Serializable(with = VaultFullInfoSerializer::class)
sealed class VaultFullInfo {
    @Serializable
    data class NotExists(val notExists: UserData) : VaultFullInfo()

    @Serializable
    data class Outsider(val outsider: UserDataOutsider) : VaultFullInfo()

    @Serializable
    data class Member(val member: UserMemberFullInfo) : VaultFullInfo()
}

@Serializable(with = StateSerializer::class)
sealed class State {
    @Serializable
    data class Local(val local: DeviceData) : State()

    @Serializable
    data class Vault(
        val vault: VaultFullInfo
    ) : State()
}

@Serializable
data class Message(
    val state: State? = null
)

@Serializable
data class AppStateModel(
    val message: Message? = null,
    val success: Boolean = false
) {
    fun getAppState(): State? {
        return message?.state
    }

    fun getVaultState(): VaultFullInfo? {
        return when (val state = getAppState()) {
            is State.Vault -> state.vault
            else -> null
        }
    }

    fun getOutsiderStatus(): UserDataOutsiderStatus? {
        return when (val vaultInfo = getVaultState()) {
            is VaultFullInfo.Outsider -> vaultInfo.outsider.status
            else -> null
        }
    }

    companion object {
        fun fromJson(jsonResponse: String): AppStateModel {
            return try {
                JsonConfig.json.decodeFromString<AppStateModel>(jsonResponse)
            } catch (e: Exception) {
                println("⛔ Failed to parse JSON: $jsonResponse, error: ${e.message}")
                e.printStackTrace()
                AppStateModel(message = null, success = false)
            }
        }
    }
}