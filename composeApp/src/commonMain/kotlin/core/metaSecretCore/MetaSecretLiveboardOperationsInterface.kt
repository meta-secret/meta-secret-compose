package core.metaSecretCore

/** String-only boundary for the additive native Liveboard operations. */
interface MetaSecretLiveboardOperationsInterface {
    fun clusterDistributionChecked(name: String, value: String): String
    fun recoverChecked(secretId: String): String
    fun decideJoinRequest(requestJson: String): String
    fun decideRemoveMember(requestJson: String): String
    fun deleteSecretChecked(secretId: String): String
    fun removeMemberChecked(targetDeviceId: String): String
    fun reconcileProducerReceipt(reference: String): String
    fun continueLocalRemoveChecked(reference: String): String
}
