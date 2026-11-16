package models.apiModels.liveboard

import core.metaSecretCore.liveboard.LiveboardKind
import models.apiModels.DeviceUiCategory

data class LiveboardSnapshotIdentity(val vaultName: String, val currentDeviceId: String)

data class LiveboardVaultPermissions(
    val storageMode: String,
    val canCreateSecret: Boolean,
    val removableDeviceIds: List<String>,
)

data class LiveboardDevice(
    val deviceId: String,
    val name: String,
    val category: DeviceUiCategory,
    val current: Boolean,
    val canRemove: Boolean,
)

data class LiveboardSecretIdentity(
    val passId: String,
    val name: String,
    val type: String?,
    val wordCount: Int?,
)

data class LiveboardSecretPrimary(val action: String, val readiness: String, val claimId: String?)

data class LiveboardSecret(
    val identity: LiveboardSecretIdentity,
    val primary: LiveboardSecretPrimary,
    val canDelete: Boolean,
    val deletionRequestId: String?,
)

data class LiveboardOperationIdentity(
    val kind: LiveboardKind,
    val requestId: String,
    val requesterId: String,
    val targetDeviceId: String?,
    val secretId: String?,
)

data class LiveboardOperationPermissions(
    val eligiblePeerIds: List<String>,
    val canApprove: Boolean,
    val canDecline: Boolean,
)

data class LiveboardOperationStatus(
    val clientStatus: String,
    val outcome: String?,
    val responderId: String?,
    val resolutionSource: String,
    val readiness: String,
)

data class LiveboardOperation(
    val identity: LiveboardOperationIdentity,
    val permissions: LiveboardOperationPermissions,
    val status: LiveboardOperationStatus,
    val revealContext: LiveboardRevealContext? = null,
)

/** Labels, metadata and permissions all belong to one decoded native Member. */
data class LiveboardSnapshot(
    val identity: LiveboardSnapshotIdentity,
    val permissions: LiveboardVaultPermissions,
    val devices: List<LiveboardDevice>,
    val secrets: List<LiveboardSecret>,
    val operations: List<LiveboardOperation>,
)
