package core.metaSecretCore.liveboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Actual native entry is distinct from permission, application and current UI publication. */
sealed interface NativeLiveboardOutcome<out T> {
    data class Entered<T>(val scope: NativeReadScope, val result: NativeResult<T>) : NativeLiveboardOutcome<T>
    data class NotEntered(val scope: NativeReadScope, val code: NativeFailure) : NativeLiveboardOutcome<Nothing>
}

/** Stateless checked routes. Session-owned result lifetime and publication are separate concerns. */
class NativeLiveboardOperations(
    private val adapter: LiveboardCoreAdapter,
    private val operations: NativeOperationPort,
    private val environment: NativeReadEnvironment,
) {
    suspend fun clusterDistributionChecked(expected: NativeReadScope, name: String, value: String
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareClusterDistributionChecked(name, value))

    suspend fun recoverChecked(expected: NativeReadScope, secretId: String
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareRecoverChecked(secretId))

    suspend fun decideJoinRequest(expected: NativeReadScope, requestJson: String
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareDecideJoinRequest(requestJson))

    suspend fun decideRemoveMember(expected: NativeReadScope, requestJson: String
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareDecideRemoveMember(requestJson))

    suspend fun deleteSecretChecked(expected: NativeReadScope, secretId: String
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareDeleteSecretChecked(secretId))

    suspend fun removeMemberChecked(expected: NativeReadScope, targetDeviceId: String
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareRemoveMemberChecked(targetDeviceId))

    internal suspend fun removeMemberChecked(
        expected: NativeReadScope,
        targetDeviceId: String,
        admission: LiveboardCommandAdmission,
    ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareRemoveMemberChecked(targetDeviceId), admission.entry)

    suspend fun reconcileProducerReceipt(expected: NativeReadScope, reference: LiveboardProducerReference
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareReconcileProducerReceipt(reference))

    suspend fun continueLocalRemoveChecked(expected: NativeReadScope, reference: LiveboardProducerReference
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareContinueLocalRemoveChecked(reference))

    internal suspend fun createSelected(expected: NativeReadScope, admission: LiveboardEntryAdmission
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        when (val entered = invokeRaw(expected, admission) { createAtEntry(admission) }) {
            is NativeInvocation.Value -> NativeLiveboardOutcome.Entered(entered.scope, decodeCreated(entered.value))
            is NativeInvocation.Failure -> NativeLiveboardOutcome.Entered(entered.scope, NativeResult.Uncertain)
            NativeInvocation.Retired -> NativeLiveboardOutcome.NotEntered(expected, NativeFailure.OperationUnavailable)
        }

    private fun createAtEntry(admission: LiveboardEntryAdmission): String {
        val payload = admission.payload() ?: error("Create selection retired")
        try {
            val value = if (payload.mode == LiveboardDraftMode.Plain) payload.value
                else payload.words.take(payload.wordCount).joinToString(" ") { it.trim() }
            val prepared = adapter.prepareClusterDistributionChecked(payload.name, value)
            return (prepared as LiveboardPreparedOperation.Ready<LiveboardCheckedReceipt>).native()
        } finally {
            admission.discardPayload()
        }
    }

    private fun decodeCreated(raw: String): NativeResult<LiveboardCheckedReceipt> = when (val decoded =
        LiveboardReceiptDecoder.decodeChecked(raw)) {
        is NativeResult.Success -> if (decoded.value.kind == LiveboardKind.Create) decoded else NativeResult.Uncertain
        is NativeResult.Unavailable -> if (decoded.code == NativeFailure.InvalidResponse) NativeResult
            .Uncertain else decoded
        NativeResult.Uncertain -> NativeResult.Uncertain
    }

    internal suspend fun recoverChecked(expected: NativeReadScope, id: String, admission: LiveboardEntryAdmission
        ): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareRecoverChecked(id), admission)
    internal suspend fun deleteSecretChecked(expected: NativeReadScope, id: String,
        admission: LiveboardEntryAdmission): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareDeleteSecretChecked(id), admission)
    internal suspend fun decideJoinRequest(expected: NativeReadScope, body: String,
        admission: LiveboardEntryAdmission): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareDecideJoinRequest(body), admission)
    internal suspend fun decideRemoveMember(expected: NativeReadScope, body: String,
        admission: LiveboardEntryAdmission): NativeLiveboardOutcome<LiveboardCheckedReceipt> =
        execute(expected, adapter.prepareDecideRemoveMember(body), admission)
    internal suspend fun continueLocalRemoveChecked(expected: NativeReadScope,
        reference: LiveboardProducerReference, admission: LiveboardEntryAdmission
        ): NativeLiveboardOutcome<LiveboardProducerReceipt> =
        execute(expected, adapter.prepareContinueLocalRemoveChecked(reference), admission)
    internal suspend fun showSelected(expected: NativeReadScope, id: String, recovered: Boolean,
        admission: LiveboardEntryAdmission): NativeInvocation<String> =
        showSelected(expected, id, recovered, admission, null)
    internal suspend fun showSelected(expected: NativeReadScope, id: String, recovered: Boolean,
        admission: LiveboardEntryAdmission, observation: LiveboardShowObservationTicket?): NativeInvocation<String> {
        val result = invokeObservedRaw(expected, admission, observation?.rejection) {
            observation?.checkedCallStarted()
            if (recovered) adapter.showRecovered(id) else adapter.showLocalSecret(id)
        }
        observation?.raw(when (result) {
            is NativeInvocation.Value -> ShowObservationRaw.VALUE
            is NativeInvocation.Failure -> ShowObservationRaw.FAILURE
            NativeInvocation.Retired -> ShowObservationRaw.RETIRED
        })
        return result
    }
    internal suspend fun decideSelected(expected: NativeReadScope, intent: LiveboardDecisionIntent, body: String,
        admission: LiveboardEntryAdmission): NativeInvocation<String> =
        decideSelectedObserved(expected, intent, body, admission, null)
    internal suspend fun decideSelectedObserved(expected: NativeReadScope, intent: LiveboardDecisionIntent,
        body: String, admission: LiveboardEntryAdmission, trace: LiveboardDecisionRejectionTrace?
        ): NativeInvocation<String> =
        invokeObservedRaw(expected, admission, trace) {
            if (intent.identity.kind == LiveboardKind.DeleteSecret) adapter.decideDeleteSecret(body)
            else if (intent.accepted) adapter.acceptRecover(intent.identity.requestId)
            else adapter.declineRecover(intent.identity.requestId)
        }

    private suspend fun invokeRaw(expected: NativeReadScope, admission: LiveboardEntryAdmission, native: () ->
        String): NativeInvocation<String> = invokeObservedRaw(expected, admission, null, native)
    private suspend fun invokeObservedRaw(expected: NativeReadScope, admission: LiveboardEntryAdmission,
        trace: LiveboardDecisionRejectionTrace?, native: () -> String): NativeInvocation<String> =
        when (val result = operations.invoke(expected) {
            if (admission.admitObserved(expected, trace)) LiveboardNativeEntry.Called(native())
            else LiveboardNativeEntry.Skipped
        }) {
            is NativeInvocation.Value -> when (val actual = result.value) {
                is LiveboardNativeEntry.Called -> NativeInvocation.Value(result.scope, actual.raw)
                LiveboardNativeEntry.Skipped -> NativeInvocation.Retired
            }
            is NativeInvocation.Failure -> if (admission.isAdmitted) result else {
                trace?.note(LiveboardDecisionRejectionSource.RAW_FAILURE_BEFORE_ADMISSION)
                NativeInvocation.Retired
            }
            NativeInvocation.Retired -> {
                trace?.note(LiveboardDecisionRejectionSource.RAW_OWNER_ENTRY_RETIRED)
                NativeInvocation.Retired
            }
        }

    internal suspend fun showLocalSecret(expected: NativeReadScope, secretId: String): NativeInvocation<String> =
        invokeRaw(expected) { adapter.showLocalSecret(secretId) }

    internal suspend fun decideDeleteSecret(expected: NativeReadScope, requestJson: String): NativeInvocation<String> =
        invokeRaw(expected) { adapter.decideDeleteSecret(requestJson) }

    internal suspend fun showRecovered(expected: NativeReadScope, claimId: String): NativeInvocation<String> =
        invokeRaw(expected) { adapter.showRecovered(claimId) }

    internal suspend fun acceptRecover(expected: NativeReadScope, claimId: String): NativeInvocation<String> =
        invokeRaw(expected) { adapter.acceptRecover(claimId) }

    internal suspend fun declineRecover(expected: NativeReadScope, claimId: String): NativeInvocation<String> =
        invokeRaw(expected) { adapter.declineRecover(claimId) }

    private suspend fun invokeRaw(expected: NativeReadScope, native: () -> String): NativeInvocation<String> =
        operations.invoke(expected, native)

    private suspend fun <T> execute(expected: NativeReadScope, prepared: LiveboardPreparedOperation<T>
        ): NativeLiveboardOutcome<T> =
        execute(expected, prepared, null)

    private suspend fun <T> execute(
        expected: NativeReadScope,
        prepared: LiveboardPreparedOperation<T>,
        admission: LiveboardEntryAdmission?,
    ): NativeLiveboardOutcome<T> = when (prepared) {
        is LiveboardPreparedOperation.Rejected -> NativeLiveboardOutcome.NotEntered(expected, prepared.code)
        is LiveboardPreparedOperation.Ready -> withContext(environment.io) {
            when (val entered = operations.invoke(expected) {
                if (admission == null || admission.admit(expected)) LiveboardNativeEntry.Called(prepared.native())
                else LiveboardNativeEntry.Skipped
            }) {
                is NativeInvocation.Value -> when (val actual = entered.value) {
                    LiveboardNativeEntry.Skipped -> NativeLiveboardOutcome.NotEntered(expected, NativeFailure
                        .OperationUnavailable)
                    is LiveboardNativeEntry.Called -> {
                        // The actual native lease is released before strict decoding or returned truth.
                        val result = try {
                            prepared.decode(actual.raw)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            prepared.failure
                        }
                        NativeLiveboardOutcome.Entered(entered.scope, result)
                    }
                }
                is NativeInvocation.Failure -> if (admission != null && !admission.isAdmitted) {
                    NativeLiveboardOutcome.NotEntered(expected, NativeFailure.OperationUnavailable)
                } else NativeLiveboardOutcome.Entered(entered.scope, prepared.failure)
                NativeInvocation.Retired -> NativeLiveboardOutcome.NotEntered(expected, NativeFailure
                    .OperationUnavailable)
            }
        }
    }
}
