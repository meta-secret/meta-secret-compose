package core.metaSecretCore.liveboard

/** Payload-free failures; no parser or native exception text crosses this boundary. */
sealed interface NativeResult<out T> {
    data class Success<out T>(val value: T) : NativeResult<T>
    data class Unavailable(val code: NativeFailure) : NativeResult<Nothing>

    /** The session owns admission fencing when an entered mutation has no usable receipt. */
    data object Uncertain : NativeResult<Nothing>
}

enum class NativeFailure {
    NotInitialized,
    OperationUnavailable,
    InvalidDecisionInput,
    InvalidProducerReference,
    InvalidResponse,
    InvalidInput,
}
