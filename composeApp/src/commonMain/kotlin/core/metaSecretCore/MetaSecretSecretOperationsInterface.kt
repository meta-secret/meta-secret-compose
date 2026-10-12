package core.metaSecretCore

interface MetaSecretSecretOperationsInterface {
    fun deleteSecret(secretId: String): String
    fun decideDeleteSecret(requestJson: String): String
    fun splitSecret(secretName: String, secret: String): String
    fun findClaim(secretId: String): String
    fun recover(secretId: String): String
    fun acceptRecover(claimId: String): String
    fun declineRecover(claimId: String): String
    fun sendDeclineCompletion(claimId: String): String
    fun showRecovered(claimId: String): String
    fun showLocalSecret(secretId: String): String
}
