package models.apiModels.liveboard

import core.metaSecretCore.liveboard.LiveboardJson
import core.metaSecretCore.liveboard.LiveboardKind
import core.metaSecretCore.liveboard.LiveboardRevealDecoder
import kotlinx.serialization.json.JsonElement
import models.apiModels.DeviceUiCategory
import models.apiModels.AppStateModel
import models.apiModels.SecretApiModel
import models.apiModels.UserMemberFullInfo
import models.apiModels.VaultFullInfo

/** Unsupported or unavailable metadata never enables Liveboard actions. */
object LiveboardStateDecoder {
    fun decode(appState: AppStateModel): LiveboardSnapshot? = try {
        require(appState.success) { "InvalidResponse" }
        val member = (appState.getVaultFullInfo() as? VaultFullInfo.Member)?.member
            ?: throw IllegalArgumentException("InvalidResponse")
        val metadata = LiveboardJson.record(member.liveboardState)
        LiveboardJson.fields(metadata, "schemaVersion", "vaultName", "currentDeviceId", "storageMode",
            "canCreateSecret", "removableDeviceIds", "secrets", "operations")
        LiveboardJson.version(metadata["schemaVersion"])
        val identity = LiveboardSnapshotIdentity(LiveboardJson.text(metadata["vaultName"]),
            LiveboardJson.text(metadata["currentDeviceId"]))
        require(identity.vaultName == member.member.vault.vaultName &&
            identity.vaultName == member.member.member.userData.vaultName &&
            identity.currentDeviceId == member.member.member.userData.device.deviceId) { "InvalidResponse" }
        val removable = texts(metadata["removableDeviceIds"])
        LiveboardJson.unique(removable)
        val vaultSecrets = member.member.vault.secrets
        LiveboardJson.unique(vaultSecrets.map { it.id })
        val byId = vaultSecrets.associateBy { it.id }
        val secrets = LiveboardJson.array(metadata["secrets"]).map { secret(it, byId) }
        LiveboardJson.unique(secrets.map { it.identity.passId })
        val operations = LiveboardJson.array(metadata["operations"]).map { operation(it) }
        require(operations.map { it.identity.kind to it.identity.requestId }.toSet().size == operations.size) {
            "InvalidResponse"
        }
        operations.forEach { correlate(it, identity, secrets) }
        LiveboardSnapshot(identity, LiveboardVaultPermissions(
            LiveboardJson.choice(metadata["storageMode"], "whole", "copies", "shares"),
            LiveboardJson.boolean(metadata["canCreateSecret"]), removable),
            devices(member, identity, removable), secrets, operations)
    } catch (_: Exception) {
        null
    }

    fun decode(raw: String): LiveboardSnapshot? = decode(AppStateModel.fromJson(raw))

    private fun devices(
        member: UserMemberFullInfo,
        identity: LiveboardSnapshotIdentity,
        removable: List<String>,
    ): List<LiveboardDevice> {
        val devices = member.member.vault.users.mapNotNull { (id, membership) ->
            require((membership.member == null) != (membership.outsider == null)) { "InvalidResponse" }
            val user = membership.member?.userData ?: membership.outsider!!.userData
            require(id.isNotEmpty() && id == user.device.deviceId && user.vaultName == identity.vaultName) {
                "InvalidResponse"
            }
            membership.member ?: return@mapNotNull null
            LiveboardDevice(id, user.device.deviceName, user.device.uiCategory ?: DeviceUiCategory.Other,
                id == identity.currentDeviceId, id != identity.currentDeviceId && id in removable)
        }
        require(devices.any { it.current }) { "InvalidResponse" }
        return devices
    }

    private fun secret(value: JsonElement, byId: Map<String, SecretApiModel>): LiveboardSecret {
        val metadata = LiveboardJson.record(value)
        LiveboardJson.fields(metadata, "secretId", "primaryAction", "recoveryClaimId", "deletionRequestId",
            "canDelete", "readiness")
        val id = LiveboardJson.text(metadata["secretId"])
        val secret = byId[id] ?: throw IllegalArgumentException("InvalidResponse")
        val action = LiveboardJson.choice(metadata["primaryAction"], "showLocal", "showRecovered", "recover",
            "waiting", "unavailable")
        val claimId = LiveboardJson.optionalText(metadata["recoveryClaimId"])
        require(action !in listOf("showRecovered", "waiting") || claimId != null) { "InvalidResponse" }
        return LiveboardSecret(LiveboardSecretIdentity(id, secret.name, secret.type, secret.wordCount),
            LiveboardSecretPrimary(action, LiveboardJson.choice(metadata["readiness"], "ready", "waiting",
                "synchronizing"), claimId),
            LiveboardJson.boolean(metadata["canDelete"]), LiveboardJson.optionalText(metadata["deletionRequestId"]))
    }

    private fun operation(value: JsonElement): LiveboardOperation {
        val metadata = LiveboardJson.record(value)
        LiveboardJson.fields(metadata, "kind", "requestId", "requesterId", "targetDeviceId", "secretId", "clientStatus",
            "eligiblePeerIds", "canApprove", "canDecline", "outcome", "responderId", "resolutionSource", "readiness",
            "revealContext")
        val peers = texts(metadata["eligiblePeerIds"])
        LiveboardJson.unique(peers)
        val outcome = LiveboardJson.optionalText(metadata["outcome"])
        require(outcome == null || outcome in listOf("accepted", "declined")) { "InvalidResponse" }
        return LiveboardOperation(LiveboardOperationIdentity(LiveboardJson.kind(metadata["kind"]),
            LiveboardJson.text(metadata["requestId"]), LiveboardJson.text(metadata["requesterId"]),
            LiveboardJson.optionalText(metadata["targetDeviceId"]), LiveboardJson.optionalText(metadata["secretId"])),
            LiveboardOperationPermissions(peers, LiveboardJson.boolean(metadata["canApprove"]),
                LiveboardJson.boolean(metadata["canDecline"])),
            LiveboardOperationStatus(LiveboardJson.choice(metadata["clientStatus"], "pending", "needApprove",
                "accepted",
                "declined", "done", "unknown", "synchronizing"), outcome, LiveboardJson.optionalText(metadata[
                    "responderId"]),
                LiveboardJson.choice(metadata["resolutionSource"], "unknown", "canonical", "verified"),
                LiveboardJson.choice(metadata["readiness"], "waiting", "ready", "synchronizing", "done", "settled")),
            if ("revealContext" in metadata) LiveboardRevealDecoder.context(metadata["revealContext"]) else null)
    }

    private fun correlate(operation: LiveboardOperation, identity: LiveboardSnapshotIdentity,
        secrets: List<LiveboardSecret>) {
        val context = operation.revealContext ?: return
        val secret = secrets.singleOrNull { it.identity.passId == operation.identity.secretId }
            ?: throw IllegalArgumentException("InvalidResponse")
        require(context.identity == identity && operation.identity.kind == LiveboardKind.Recover &&
            operation.identity.requesterId == identity.currentDeviceId && operation.identity.targetDeviceId == null &&
            operation.status.outcome == "accepted" && secret.primary.readiness == "ready") { "InvalidResponse" }
        require(when (operation.status.clientStatus) {
            "accepted" -> secret.primary.action == "showRecovered" && secret.primary.claimId == operation.identity
                .requestId
            "done" -> secret.primary.action == "recover" && secret.primary.claimId == null
            else -> false
        }) { "InvalidResponse" }
    }

    private fun texts(value: JsonElement?): List<String> = LiveboardJson.array(value).map { LiveboardJson.text(it) }
}
