package core.metaSecretCore.liveboard

/** Pure route preparation shares the synchronous adapter's validation and receipt rules. */
internal sealed interface LiveboardPreparedOperation<out T> {
    class Ready<T>(
        val native: () -> String,
        val decode: (String) -> NativeResult<T>,
        val failure: NativeResult<T>,
    ) : LiveboardPreparedOperation<T>

    class Rejected(val code: NativeFailure) : LiveboardPreparedOperation<Nothing>
}

internal fun <T> LiveboardPreparedOperation<T>.executeSync(): NativeResult<T> = when (this) {
    is LiveboardPreparedOperation.Ready -> try {
        decode(native())
    } catch (_: Exception) {
        failure
    }
    is LiveboardPreparedOperation.Rejected -> NativeResult.Unavailable(code)
}
