package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretCoreInterface

/** Stateless integration boundary. Session lifetime and admission locks belong to its caller. */
class LiveboardCoreAdapter(private val core: MetaSecretCoreInterface) {
    fun clusterDistributionChecked(name: String, value: String): NativeResult<LiveboardCheckedReceipt> =
        prepareClusterDistributionChecked(name, value).executeSync()

    internal fun prepareClusterDistributionChecked(name: String, value: String
        ): LiveboardPreparedOperation<LiveboardCheckedReceipt> =
        mutation({ core.clusterDistributionChecked(name, value) }, LiveboardReceiptDecoder::decodeChecked) {
            it.kind == LiveboardKind.Create
        }

    fun recoverChecked(secretId: String): NativeResult<LiveboardCheckedReceipt> =
        prepareRecoverChecked(secretId).executeSync()

    internal fun prepareRecoverChecked(secretId: String): LiveboardPreparedOperation<LiveboardCheckedReceipt> =
        mutation({ core.recoverChecked(secretId) }, LiveboardReceiptDecoder::decodeChecked) {
            it.kind == LiveboardKind.Recover && it.identity.secretId == secretId
        }

    fun decideJoinRequest(requestJson: String): NativeResult<LiveboardCheckedReceipt> =
        prepareDecideJoinRequest(requestJson).executeSync()

    internal fun prepareDecideJoinRequest(requestJson: String): LiveboardPreparedOperation<LiveboardCheckedReceipt> =
        decide(requestJson, LiveboardKind.Join) { core.decideJoinRequest(requestJson) }

    fun decideRemoveMember(requestJson: String): NativeResult<LiveboardCheckedReceipt> =
        prepareDecideRemoveMember(requestJson).executeSync()

    internal fun prepareDecideRemoveMember(requestJson: String): LiveboardPreparedOperation<LiveboardCheckedReceipt> =
        decide(requestJson, LiveboardKind.RemoveDevice) { core.decideRemoveMember(requestJson) }

    fun deleteSecretChecked(secretId: String): NativeResult<LiveboardProducerReceipt> =
        prepareDeleteSecretChecked(secretId).executeSync()

    internal fun prepareDeleteSecretChecked(secretId: String): LiveboardPreparedOperation<LiveboardProducerReceipt> =
        mutation({ core.deleteSecretChecked(secretId) }, LiveboardReceiptDecoder::decodeProducer) {
            it.identity.kind == LiveboardKind.DeleteSecret && it.identity.secretId == secretId
        }

    fun removeMemberChecked(targetDeviceId: String): NativeResult<LiveboardProducerReceipt> =
        prepareRemoveMemberChecked(targetDeviceId).executeSync()

    internal fun prepareRemoveMemberChecked(targetDeviceId: String
        ): LiveboardPreparedOperation<LiveboardProducerReceipt> =
        mutation({ core.removeMemberChecked(targetDeviceId) }, LiveboardReceiptDecoder::decodeProducer) {
            it.identity.kind == LiveboardKind.RemoveDevice && it.identity.targetDeviceId == targetDeviceId
        }

    fun reconcileProducerReceipt(reference: LiveboardProducerReference): NativeResult<LiveboardProducerReceipt> =
        prepareReconcileProducerReceipt(reference).executeSync()

    internal fun prepareReconcileProducerReceipt(reference: LiveboardProducerReference
        ): LiveboardPreparedOperation<LiveboardProducerReceipt> {
        val encoded = referenceJson(reference)
            ?: return LiveboardPreparedOperation.Rejected(NativeFailure.InvalidProducerReference)
        return LiveboardPreparedOperation.Ready(
            native = { core.reconcileProducerReceipt(encoded) },
            decode = { raw ->
                when (val result = LiveboardReceiptDecoder.decodeProducer(raw)) {
                    is NativeResult.Success -> if (reference.matches(result.value)) result else unavailable()
                    else -> result
                }
            },
            failure = unavailable(),
        )
    }

    fun continueLocalRemoveChecked(reference: LiveboardProducerReference): NativeResult<LiveboardProducerReceipt> =
        prepareContinueLocalRemoveChecked(reference).executeSync()

    internal fun prepareContinueLocalRemoveChecked(reference: LiveboardProducerReference
        ): LiveboardPreparedOperation<LiveboardProducerReceipt> {
        if (!reference.isRemove()) return LiveboardPreparedOperation.Rejected(NativeFailure.InvalidProducerReference)
        val encoded = referenceJson(reference)
            ?: return LiveboardPreparedOperation.Rejected(NativeFailure.InvalidProducerReference)
        return mutation({ core.continueLocalRemoveChecked(encoded) }, LiveboardReceiptDecoder::decodeProducer,
            reference::matches)
    }

    internal fun showLocalSecret(secretId: String): String = core.showLocalSecret(secretId)
    internal fun decideDeleteSecret(requestJson: String): String = core.decideDeleteSecret(requestJson)
    fun showRecovered(claimId: String): String = core.showRecovered(claimId)
    fun acceptRecover(claimId: String): String = core.acceptRecover(claimId)
    fun declineRecover(claimId: String): String = core.declineRecover(claimId)

    private fun decide(
        requestJson: String,
        kind: LiveboardKind,
        action: () -> String,
    ): LiveboardPreparedOperation<LiveboardCheckedReceipt> {
        val requestId = decisionId(requestJson)
            ?: return LiveboardPreparedOperation.Rejected(NativeFailure.InvalidDecisionInput)
        return mutation(action, LiveboardReceiptDecoder::decodeChecked) {
            it.kind == kind && it.identity.requestId == requestId
        }
    }

    private fun decisionId(raw: String): String? = try {
        require(raw.encodeToByteArray().size <= MAX_DECISION_INPUT_BYTES) { "InvalidDecisionInput" }
        val input = LiveboardJson.record(LiveboardJson.parse(raw))
        LiveboardJson.fields(input, "requestId", "accepted")
        LiveboardJson.boolean(input["accepted"])
        LiveboardJson.uuid(input["requestId"])
    } catch (_: Exception) {
        null
    }

    private fun referenceJson(reference: LiveboardProducerReference): String? = try {
        reference.toJson()
    } catch (_: Exception) {
        null
    }

    private fun <T> mutation(
        action: () -> String,
        decode: (String) -> NativeResult<T>,
        matches: (T) -> Boolean,
    ): LiveboardPreparedOperation<T> = LiveboardPreparedOperation.Ready(
        native = action,
        decode = { raw ->
            when (val result = decode(raw)) {
                is NativeResult.Success -> if (matches(result.value)) result else NativeResult.Uncertain
                is NativeResult.Unavailable -> if (result.code == NativeFailure.InvalidResponse) NativeResult
                    .Uncertain else result
                NativeResult.Uncertain -> NativeResult.Uncertain
            }
        },
        failure = NativeResult.Uncertain,
    )

    private fun unavailable(code: NativeFailure = NativeFailure.OperationUnavailable) = NativeResult.Unavailable(code)
}

private const val MAX_DECISION_INPUT_BYTES: Int = 1024
