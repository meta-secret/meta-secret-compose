package models.apiModels.liveboard

import core.metaSecretCore.liveboard.LiveboardJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import models.apiModels.AppStateModel
import models.apiModels.State
import models.apiModels.VaultFullInfo

/** Raw join inputs retain their canonical types before legacy normalization. */
object LiveboardMetadataBoundary {
    fun isUnambiguous(raw: String): Boolean = try {
        val outer = LiveboardJson.record(LiveboardJson.parse(raw))
        LiveboardJson.boolean(outer["success"])
        val message = child(outer, "message")
        val memberInfo = child(child(child(message, "state"), "vault"), "member")
        val vaultMember = child(memberInfo, "member")
        userData(child(child(vaultMember, "member"), "userData"))
        val vault = child(vaultMember, "vault")
        string(vault["vaultName"])
        vault["users"]?.let { users ->
            LiveboardJson.record(users).values.forEach { value ->
                val membership = LiveboardJson.record(value)
                for (kind in listOf("member", "outsider")) {
                    membership[kind]?.takeUnless { it == JsonNull }?.let {
                        userData(child(LiveboardJson.record(it), "userData"))
                    }
                }
            }
        }
        vault["secrets"]?.let { secrets -> LiveboardJson.array(secrets).forEach(::secret) }
        true
    } catch (_: Exception) {
        false
    }

    fun retainMetadata(state: AppStateModel, unambiguous: Boolean): AppStateModel {
        if (unambiguous) return state
        val vaultState = state.message?.state as? State.Vault ?: return state
        val member = vaultState.vault as? VaultFullInfo.Member ?: return state
        val safeMember = member.copy(member = member.member.copy(liveboardState = null))
        return state.copy(message = state.message.copy(state = vaultState.copy(vault = safeMember)))
    }

    private fun userData(value: JsonObject) {
        string(value["vaultName"])
        val device = child(value, "device")
        string(device["deviceId"])
        string(device["deviceName"])
        string(device["deviceType"])
        optionalString(device["uiCategory"])
    }

    private fun secret(value: JsonElement) {
        val secret = LiveboardJson.record(value)
        string(secret["id"])
        string(secret["name"])
        optionalString(secret["type"])
        secret["wordCount"]?.takeUnless { it == JsonNull }?.let {
            val count = it as? JsonPrimitive
            require(count != null && !count.isString && count.intOrNull != null) { "InvalidResponse" }
        }
    }

    private fun string(value: JsonElement?) {
        require((value as? JsonPrimitive)?.isString == true) { "InvalidResponse" }
    }

    private fun optionalString(value: JsonElement?) {
        if (value != null && value != JsonNull) string(value)
    }

    private fun child(value: JsonObject, key: String): JsonObject = LiveboardJson.record(value[key])
}
