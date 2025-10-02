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
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("This serializer only supports JSON format")
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
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("This serializer only supports JSON format")
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
data class UserMembership(
    val member: UserDataMember? = null,
    val outsider: UserDataOutsider? = null
)

@Serializable
data class SecretApiModel(
    val id: String,
    val name: String
)

@Serializable
data class VaultData(
    val vaultName: String,
    val users: Map<String, UserMembership> = emptyMap(),
    val secrets: List<SecretApiModel> = emptyList(),
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
data class JoinClusterRequest(
    val candidate: UserData
)

@Serializable 
data class VaultRequest(
    val joinCluster: JoinClusterRequest? = null
)

@Serializable
data class VaultEvents(
    val requests: List<VaultRequest> = emptyList(),
    val updates: List<String> = emptyList() // Placeholder for updates structure
) {
    fun hasJoinRequests(): Boolean {
        return requests.any { it.joinCluster != null }
    }
    
    fun getJoinRequests(): List<JoinClusterRequest> {
        return requests.mapNotNull { it.joinCluster }
    }

    fun getJoinRequestsCount(): Int {
        return getJoinRequests().size
    }
}

@Serializable
data class SsClaims(
    val claims: Map<String, String> = emptyMap()
)

@Serializable
data class UserMemberFullInfo(
    val member: VaultMember,
    val ssClaims: SsClaims? = null,
    val vaultEvents: VaultEvents? = null,
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

    fun getVaultFullInfo(): VaultFullInfo? {
        return when (val state = getAppState()) {
            is State.Vault -> state.vault
            else -> null
        }
    }

    fun getOutsiderStatus(): UserDataOutsiderStatus? {
        return when (val vaultInfo = getVaultFullInfo()) {
            is VaultFullInfo.Outsider -> vaultInfo.outsider.status
            else -> null
        }
    }

    fun getCurrentDeviceId(): String? {
        return when (val vaultInfo = getVaultFullInfo()) {
            is VaultFullInfo.Member -> vaultInfo.member.member.member.userData.device.deviceId
            is VaultFullInfo.Outsider -> vaultInfo.outsider.userData.device.deviceId
            else -> null
        }
    }
    
    fun getCurrentVaultName(): String? {
        return when (val vaultInfo = getVaultFullInfo()) {
            is VaultFullInfo.Member -> vaultInfo.member.member.member.userData.vaultName
            is VaultFullInfo.Outsider -> vaultInfo.outsider.userData.vaultName
            is VaultFullInfo.NotExists -> vaultInfo.notExists.vaultName
            else -> null
        }
    }

    fun getVaultEvents(): VaultEvents? {
        return when (val vaultInfo = getVaultFullInfo()) {
            is VaultFullInfo.Member -> vaultInfo.member.vaultEvents
            else -> null
        }
    }

    fun getJoinRequestsCount(): Int {
        return getVaultEvents()?.getJoinRequestsCount() ?: 0
    }

    fun getUserDataByDeviceId(deviceId: String): UserData? {
        val vaultInfo = getVaultFullInfo() ?: return null
        
        return when (vaultInfo) {
            is VaultFullInfo.Member -> {
                val users = vaultInfo.member.member.vault.users
                users[deviceId]?.let { membership ->
                    when {
                        membership.member != null -> membership.member.userData
                        membership.outsider != null -> membership.outsider.userData
                        else -> null
                    }
                }
            }
            is VaultFullInfo.Outsider -> {
                if (vaultInfo.outsider.userData.device.deviceId == deviceId) {
                    vaultInfo.outsider.userData
                } else {
                    null
                }
            }
            is VaultFullInfo.NotExists -> {
                if (vaultInfo.notExists.device.deviceId == deviceId) {
                    vaultInfo.notExists
                } else {
                    null
                }
            }
        }
    }

    fun getVaultSummary(): VaultSummary? {
        val vaultInfo = getVaultFullInfo() ?: return null
        
        return when (vaultInfo) {
            is VaultFullInfo.Member -> {
                val vaultData = vaultInfo.member.member.vault
                val users = mutableMapOf<String, UserInfo>()
                
                vaultData.users.forEach { (deviceId, membership) ->
                    val status = when {
                        membership.member != null -> UserStatus.MEMBER
                        membership.outsider?.status == UserDataOutsiderStatus.PENDING -> UserStatus.PENDING
                        membership.outsider?.status == UserDataOutsiderStatus.DECLINED -> UserStatus.DECLINED
                        else -> UserStatus.NON_MEMBER
                    }
                    
                    val deviceName = when {
                        membership.member != null -> membership.member.userData.device.deviceName
                        membership.outsider != null -> membership.outsider.userData.device.deviceName
                        else -> ""
                    }
                    
                    users[deviceId] = UserInfo(deviceId, deviceName, status)
                }
                
                VaultSummary(
                    vaultName = vaultData.vaultName,
                    secretsCount = vaultData.secrets.size,
                    users = users
                )
            }
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

@Serializable
enum class UserStatus {
    @SerialName("member")
    MEMBER,
    @SerialName("pending")
    PENDING,
    @SerialName("declined")
    DECLINED,
    @SerialName("nonMember")
    NON_MEMBER
}

data class UserInfo(
    val deviceId: String,
    val deviceName: String,
    val status: UserStatus
)

data class VaultSummary(
    val vaultName: String,
    val secretsCount: Int,
    val users: Map<String, UserInfo>
)