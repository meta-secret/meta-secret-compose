package models.appInternalModels

import kotlinx.serialization.Serializable
import models.apiModels.ClientStatus
import models.apiModels.DistributionType

@Serializable
data class ClaimModel(
    val claimId: String?,
    val sender: String?,
    val distributionType: DistributionType,
    val receivers: List<String>?,
    val clientStatus: ClientStatus? = null,
)