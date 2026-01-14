package models.apiModels

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchClaimMessage(
    val claim: String? = null
)

@Serializable
data class SearchClaimModel (
    val success: Boolean,
    val message: SearchClaimMessage? = null,
    val error: String? = null
) {
    val claimId: String?
        get() = message?.claim
    
    companion object {
        fun fromJson(jsonResponse: String): SearchClaimModel {
            return Json.decodeFromString<SearchClaimModel>(jsonResponse)
        }
    }
}