package models.apiModels

import kotlinx.serialization.Serializable
import models.appInternalModels.ClaimModel

@Serializable
data class SearchClaimMessage(
    val claim: ClaimObject? = null
)

@Serializable
data class SearchClaimModel(
    val success: Boolean,
    val message: SearchClaimMessage? = null,
    val error: String? = null
) {
    val claim: ClaimModel?
        get() = message?.claim?.toClaimModel()

    companion object {
        fun fromJson(jsonResponse: String): SearchClaimModel {
            return JsonConfig.json.decodeFromString<SearchClaimModel>(jsonResponse)
        }
    }
}

private fun ClaimObject.toClaimModel(): ClaimModel {
    val derivedStatus = aggregateClaimStatus(status.statuses.values)
    val senderStatus = sender?.let { status.statuses[it] }
    return ClaimModel(
        claimId = id,
        sender = sender,
        distributionType = distributionType,
        receivers = receivers.ifEmpty { null },
        status = derivedStatus,
        senderStatus = senderStatus
    )
}

private fun aggregateClaimStatus(statuses: Collection<ClaimStatus>): ClaimStatus {
    if (statuses.isEmpty()) return ClaimStatus.PENDING
    // TODO: k-of-N — replace with statuses.count { it == SENT || it == DELIVERED } >= threshold
    if (statuses.any { it == ClaimStatus.SENT }) return ClaimStatus.SENT
    if (statuses.any { it == ClaimStatus.DELIVERED }) return ClaimStatus.DELIVERED
    if (statuses.any { it == ClaimStatus.PENDING }) return ClaimStatus.PENDING
    if (statuses.any { it == ClaimStatus.DECLINED }) return ClaimStatus.DECLINED
    return ClaimStatus.DELIVERED
}