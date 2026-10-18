package core

import core.apple.AppleBrokerResult
import core.apple.AppleEmailBroker
import core.apple.AppleEmailBrowser
import core.apple.AppleEmailPolicy
import core.apple.AppleEmailProof
import core.apple.AppleEmailRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AppleEmailRequesterAndroid internal constructor(
    private val broker: AppleEmailBroker,
    private val browser: AppleEmailBrowser,
    private val runtime: AppleEmailRuntime = AppleEmailRuntime(),
) : AppleEmailRequesterInterface {
    private class Attempt(var verifier: String) {
        val result = CompletableDeferred<AppleEmailAuthResult>()
        var requestId: String? = null
        var launched = false
        var departed = false
        var taking = false
        var callbackQueued = false
        var start: Job? = null
        var take: Job? = null
        var expiry: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + runtime.dispatcher)
    private var active: Attempt? = null
    private var closed = false

    override suspend fun requestAppleEmail(): AppleEmailAuthResult = withContext(runtime.dispatcher) {
        if (closed || broker.origin == null || active != null) return@withContext unavailable()
        val attempt = try { Attempt(runtime.verifier()) } catch (_: Exception) { return@withContext unavailable() }
        if (!AppleEmailProof.valid(attempt.verifier)) return@withContext unavailable()
        active = attempt
        attempt.start = scope.launch {
            val verifier = attempt.verifier
            try {
                val start = broker.start(AppleEmailProof.challenge(verifier))
                if (active !== attempt) {
                    cancelRemote(start.requestId, verifier)
                    return@launch
                }
                attempt.requestId = start.requestId
                if (!AppleEmailPolicy.start(start, checkNotNull(broker.origin), runtime.nowSeconds())) {
                    finish(attempt, unavailable(), true)
                    return@launch
                }
                browser.launch(start.authorizationUrl)
                attempt.launched = true
                attempt.expiry = scope.launch {
                    delay((start.expiresAt - runtime.nowSeconds()).coerceAtLeast(1) * 1000)
                    finish(attempt, unavailable(), true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finish(attempt, unavailable(), true)
            }
        }
        try {
            attempt.result.await()
        } catch (cancelled: CancellationException) {
            finish(attempt, AppleEmailAuthResult.Cancelled, true)
            throw cancelled
        }
    }

    /** Intent carries only a wakeup; verified email always comes from proof-bound take. */
    fun onReturn(action: String?, uri: String?) {
        val id = AppleEmailPolicy.returnedId(action, uri) ?: return
        scope.launch {
            val attempt = active ?: return@launch
            if (attempt.requestId != id || !attempt.launched) return@launch
            attempt.departed = false
            if (attempt.taking) {
                attempt.callbackQueued = true
                return@launch
            }
            take(attempt, false)
        }
    }

    fun onHostPaused() {
        scope.launch { active?.takeIf { it.launched }?.departed = true }
    }

    fun onHostResumed() {
        scope.launch {
            val attempt = active ?: return@launch
            if (!attempt.launched || !attempt.departed) return@launch
            attempt.departed = false
            take(attempt, true)
        }
    }

    /** A recreated Activity must not leave its retained ViewModel with a dead port. */
    fun onHostDetached() {
        scope.launch { active?.let { finish(it, AppleEmailAuthResult.Cancelled, true) } }
    }

    fun close() {
        scope.launch {
            if (closed) return@launch
            closed = true
            active?.let { finish(it, AppleEmailAuthResult.Cancelled, false) }
            try { broker.close() } finally { scope.cancel() }
        }
    }

    private fun take(attempt: Attempt, returningWithoutCallback: Boolean) {
        if (active !== attempt || attempt.taking) return
        val id = attempt.requestId ?: return
        attempt.taking = true
        attempt.take = scope.launch {
            try {
                val result = broker.take(id, attempt.verifier)
                if (active !== attempt) return@launch
                when (result) {
                    AppleBrokerResult.Pending -> if (returningWithoutCallback && !attempt.callbackQueued) {
                        finish(attempt, AppleEmailAuthResult.Cancelled, true)
                    }
                    AppleBrokerResult.Cancelled -> finish(attempt, AppleEmailAuthResult.Cancelled, false)
                    AppleBrokerResult.Error -> finish(attempt, unavailable(), false)
                    is AppleBrokerResult.Success -> finish(attempt,
                        if (AppleEmailPolicy.email(result.email)) AppleEmailAuthResult.Success(result.email) else unavailable(), false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finish(attempt, unavailable(), true)
            } finally {
                attempt.taking = false
                if (active === attempt && attempt.callbackQueued) {
                    attempt.callbackQueued = false
                    take(attempt, false)
                }
            }
        }
    }

    private fun finish(attempt: Attempt, result: AppleEmailAuthResult, cancelBroker: Boolean) {
        if (active !== attempt) return
        active = null
        attempt.start?.cancel()
        attempt.take?.cancel()
        attempt.expiry?.cancel()
        val verifier = attempt.verifier
        attempt.verifier = ""
        val id = attempt.requestId
        attempt.result.complete(result)
        if (cancelBroker && id != null) cancelRemote(id, verifier)
    }

    private fun cancelRemote(id: String, verifier: String) {
        if (!AppleEmailProof.valid(id) || !AppleEmailProof.valid(verifier)) return
        scope.launch {
            try { withTimeoutOrNull(5_000) { broker.cancel(id, verifier) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Broker TTL bounds leftovers; no raw error output. */ }
        }
    }

    private fun unavailable() = AppleEmailAuthResult.Error("Apple sign-in is unavailable")
}
