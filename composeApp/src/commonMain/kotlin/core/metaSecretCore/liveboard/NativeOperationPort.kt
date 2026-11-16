package core.metaSecretCore.liveboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Uses the existing native owner; stores no operation, proof or business authority. */
class NativeOperationPort(
    private val initializationOwner: NativeInitializationOwner,
    private val environment: NativeReadEnvironment,
) {
    fun capture(): NativeReadScope? = initializationOwner.currentScope()
    fun isCurrent(scope: NativeReadScope): Boolean = initializationOwner.currentScope() == scope

    suspend fun <T> invoke(scope: NativeReadScope, native: () -> T): NativeInvocation<T> =
        initializationOwner.invoke(scope, native)

    suspend fun <T, R> execute(
        native: () -> T,
        consume: suspend (NativeReadScope, T) -> R,
    ): R? {
        val scope = capture() ?: return null
        return execute(scope, native, consume)
    }

    suspend fun <T, R> execute(
        scope: NativeReadScope,
        native: () -> T,
        consume: suspend (NativeReadScope, T) -> R,
    ): R? {
        val entered = invoke(scope, native)
        var failure: Exception? = null
        // The native lease is already released before parsing or any observable effect.
        val result = withContext(environment.ui) {
            if (!isCurrent(scope)) return@withContext null
            when (entered) {
                is NativeInvocation.Value -> try {
                    consume(scope, entered.value).takeIf { isCurrent(scope) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error
                    null
                }
                is NativeInvocation.Failure -> {
                    failure = entered.cause
                    null
                }
                NativeInvocation.Retired -> null
            }
        }
        // Restoring a delayed caller must not return an older value or failure to its successor.
        if (!isCurrent(scope)) return null
        failure?.let { throw it }
        return result
    }
}
