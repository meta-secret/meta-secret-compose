package core

import com.metaSecret.ios.SwiftBridge
import com.metaSecret.ios.clusterDistributionChecked
import com.metaSecret.ios.continueLocalRemoveChecked
import com.metaSecret.ios.decideJoinRequest
import com.metaSecret.ios.decideRemoveMember
import com.metaSecret.ios.deleteSecretChecked
import com.metaSecret.ios.reconcileProducerReceipt
import com.metaSecret.ios.recoverChecked
import com.metaSecret.ios.removeMemberChecked
import core.metaSecretCore.MetaSecretLiveboardOperationsInterface
import kotlinx.cinterop.ExperimentalForeignApi

/** The Swift wrapper forwards to the same existing process-native Core instance. */
@OptIn(ExperimentalForeignApi::class)
internal class IosLiveboardOperations(private val bridge: SwiftBridge) : MetaSecretLiveboardOperationsInterface {
    override fun clusterDistributionChecked(name: String, value: String): String = bridge
        .clusterDistributionChecked(name, value)
    override fun recoverChecked(secretId: String): String = bridge.recoverChecked(secretId)
    override fun decideJoinRequest(requestJson: String): String = bridge.decideJoinRequest(requestJson)
    override fun decideRemoveMember(requestJson: String): String = bridge.decideRemoveMember(requestJson)
    override fun deleteSecretChecked(secretId: String): String = bridge.deleteSecretChecked(secretId)
    override fun removeMemberChecked(targetDeviceId: String): String = bridge.removeMemberChecked(targetDeviceId)
    override fun reconcileProducerReceipt(reference: String): String = bridge.reconcileProducerReceipt(reference)
    override fun continueLocalRemoveChecked(reference: String): String = bridge.continueLocalRemoveChecked(reference)
}
