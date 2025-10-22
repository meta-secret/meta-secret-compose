package models.apiModels

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class SearchClaimModel (
    val success: Boolean,
    @SerialName("message") val claimId: String? = null,
    val error: String? = null
) {
    companion object {
        fun fromJson(jsonResponse: String): SearchClaimModel {
            return Json.decodeFromString<SearchClaimModel>(jsonResponse)
        }
    }
}