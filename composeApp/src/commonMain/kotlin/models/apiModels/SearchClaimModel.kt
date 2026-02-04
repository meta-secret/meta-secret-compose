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
    val derivedStatus = status.statuses.values.firstOrNull() ?: ClaimStatus.PENDING
    return ClaimModel(
        claimId = id,
        sender = sender,
        distributionType = distributionType,
        receivers = receivers.ifEmpty { null },
        status = derivedStatus
    )
}