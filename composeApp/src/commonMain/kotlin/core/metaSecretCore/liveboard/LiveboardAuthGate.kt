package core.metaSecretCore.liveboard

import core.BiometricAuthenticatorInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Uses the existing OS-owned prompt; callback bytes never become a retained result. */
internal class LiveboardAuthGate(
    private val auth: BiometricAuthenticatorInterface,
    private val environment: NativeReadEnvironment,
    private val denied: () -> Unit,
) {
    suspend fun confirm(): Boolean = withContext(environment.ui) {
        val accepted = suspendCancellableCoroutine<Boolean> { continuation ->
            val answered = MutableStateFlow(false)
            fun answer(accepted: Boolean) {
                if (answered.compareAndSet(false, true)) {
                    continuation.resume(accepted)
                }
            }
            continuation.invokeOnCancellation { answered.compareAndSet(false, true) }
            try {
                auth.authenticate(onSuccess = { answer(true) }, onError = { answer(false) }, onFallback = { answer(
                    false) })
            } catch (_: Exception) {
                answer(false)
            }
        }
        // LAContext may answer from its own queue; only the resumed UI context touches state.
        if (!accepted) denied()
        accepted
    }
}
