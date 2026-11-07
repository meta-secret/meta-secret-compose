package core.metaSecretCore.liveboard

/** A selection belongs to one Session generation and is never caller authority. */
class LiveboardRemoveIntent internal constructor(
    internal val generation: Any,
    val targetDeviceId: String,
) : LiveboardProducerIntent

/** Receipt metadata is not a claim of canonical application, approval or readiness. */
data class LiveboardProducerPresentation(
    val targetDeviceId: String,
    val result: NativeResult<LiveboardProducerReceipt>,
)

internal class LiveboardSessionDependencies(
    val checked: NativeLiveboardOperations,
    val reads: NativeCanonicalReadOwner,
    val operations: NativeOperationPort,
    val environment: NativeReadEnvironment,
    val client: LiveboardClientPorts,
)
