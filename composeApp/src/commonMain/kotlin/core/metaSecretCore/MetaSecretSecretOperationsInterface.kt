package core.metaSecretCore

interface MetaSecretSecretOperationsInterface {
    fun splitSecret(secretName: String, secret: String): String
    fun findClaim(secretId: String): String
    fun recover(secretId: String): String
    fun acceptRecover(claimId: String): String
    fun declineRecover(claimId: String): String
    fun sendDeclineCompletion(claimId: String): String
    fun showRecovered(secretId: String): String
}
