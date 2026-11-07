package core

import com.metasecret.core.MetaSecretNative
import core.metaSecretCore.MetaSecretLiveboardOperationsInterface

/** Shares the existing native library and initialization path. */
internal object AndroidLiveboardOperations : MetaSecretLiveboardOperationsInterface {
    override fun clusterDistributionChecked(name: String, value: String): String =
        MetaSecretNative.clusterDistributionChecked(name, value)

    override fun recoverChecked(secretId: String): String = MetaSecretNative.recoverChecked(secretId)
    override fun decideJoinRequest(requestJson: String): String = MetaSecretNative.decideJoinRequest(requestJson)
    override fun decideRemoveMember(requestJson: String): String = MetaSecretNative.decideRemoveMember(requestJson)
    override fun deleteSecretChecked(secretId: String): String = MetaSecretNative.deleteSecretChecked(secretId)
    override fun removeMemberChecked(targetDeviceId: String): String = MetaSecretNative.removeMemberChecked(
        targetDeviceId)
    override fun reconcileProducerReceipt(reference: String): String = MetaSecretNative.reconcileProducerReceipt(
        reference)
    override fun continueLocalRemoveChecked(reference: String): String = MetaSecretNative
        .continueLocalRemoveChecked(reference)
}
