package models.appInternalModels

data class SecretModel(
    val secretName: String?,
    val secret: String?,
    val claimId: String? = null,
)
