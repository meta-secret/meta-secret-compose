package core.apple

import core.AppleEmailAuthResult
import core.AppleEmailRequesterAndroid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleEmailRequesterAndroidTest {
    private val id = "A".repeat(43)
    private val verifier = "B".repeat(42) + "A"
    private val now = 1_800_000_000L

    private class Broker : AppleEmailBroker {
        override var origin: String? = "https://auth.example.com"
        var starts = 0
        var takes = 0
        var cancels = 0
        var closes = 0
        var lastChallenge = ""
        var lastVerifier = ""
        var outcome: AppleBrokerResult = AppleBrokerResult.Pending
        var startResult: AppleBrokerStart? = null
        var takeGate: CompletableDeferred<Unit>? = null
        var startGate: CompletableDeferred<Unit>? = null
        override suspend fun start(challenge: String): AppleBrokerStart {
            starts++
            lastChallenge = challenge
            startGate?.let { withContext(NonCancellable) { it.await() } }
            return checkNotNull(startResult)
        }
        override suspend fun take(requestId: String, verifier: String): AppleBrokerResult {
            takes++
            lastVerifier = verifier
            val response = outcome
            takeGate?.await()
            return response
        }
        override suspend fun cancel(requestId: String, verifier: String) { cancels++ }
        override fun close() { closes++ }
    }

    private fun broker() = Broker().apply {
        startResult = AppleBrokerStart(id, authorizationUrl(), now + 300)
    }

    private fun authorizationUrl() = "https://appleid.apple.com/auth/authorize?" +
        "client_id=org.metasecret.web&redirect_uri=https%3A%2F%2Fauth.example.com%2Fapple%2Fcallback&" +
        "response_type=code%20id_token&response_mode=form_post&scope=email&state=$id&nonce=$id"

    private fun returnUri(requestId: String = id) = "metasecret://apple-auth/callback?requestId=$requestId"

    @Test
    fun disabledConfigurationDoesNotStartNetworkOrBrowser() = runTest {
        val broker = broker().apply { origin = null }
        var launches = 0
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser { launches++ },
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        assertIs<AppleEmailAuthResult.Error>(adapter.requestAppleEmail())
        assertEquals(0, broker.starts)
        assertEquals(0, launches)
        adapter.close()
    }

    @Test
    fun verifiedRelayReturnsExactlyOnceAfterRealReturnSignal() = runTest {
        val broker = broker()
        val launched = mutableListOf<String>()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser { launched += it },
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        assertEquals(listOf(authorizationUrl()), launched)
        assertEquals(AppleEmailProof.challenge(verifier), broker.lastChallenge)
        broker.outcome = AppleBrokerResult.Success("Relay.Label@privaterelay.appleid.com")
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        adapter.onHostResumed()
        runCurrent()
        assertEquals(AppleEmailAuthResult.Success("Relay.Label@privaterelay.appleid.com"), result.await())
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        assertEquals(1, broker.takes)
        assertEquals(verifier, broker.lastVerifier)
        adapter.close()
    }

    @Test
    fun ordinaryResumeAndForeignIntentDoNotProbeOrCancel() = runTest {
        val broker = broker()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onHostResumed()
        for (uri in listOf(returnUri("C".repeat(43)), returnUri() + "&email=attacker@example.com",
            returnUri() + "&requestId=$id", "metasecret://apple-auth/other?requestId=$id",
            "https://apple-auth/callback?requestId=$id", returnUri() + "#fragment")) {
            adapter.onReturn("android.intent.action.VIEW", uri)
        }
        adapter.onReturn("android.intent.action.SEND", returnUri())
        runCurrent()
        assertEquals(0, broker.takes)
        assertEquals(0, broker.cancels)
        assertFalse(result.isCompleted)
        adapter.close()
        runCurrent()
        assertIs<AppleEmailAuthResult.Cancelled>(result.await())
    }

    @Test
    fun browserBackAfterPauseWithoutStopCancelsPendingAttempt() = runTest {
        val broker = broker()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onHostPaused()
        adapter.onHostResumed()
        runCurrent()
        assertIs<AppleEmailAuthResult.Cancelled>(result.await())
        assertEquals(1, broker.takes)
        assertEquals(1, broker.cancels)
        adapter.close()
    }

    @Test
    fun pendingWakeupDoesNotAuthorizeOrCancelAndMayLaterFinish() = runTest {
        val broker = broker()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(0, broker.cancels)
        broker.outcome = AppleBrokerResult.Success("Exact@Example.com")
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        assertEquals(AppleEmailAuthResult.Success("Exact@Example.com"), result.await())
        adapter.close()
    }

    @Test
    fun callbackAndResumeShareOneTakeWhileConcurrentTapDoesNotReplaceIt() = runTest {
        val broker = broker().apply {
            outcome = AppleBrokerResult.Success("test@example.com")
            takeGate = CompletableDeferred()
        }
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val first = async { adapter.requestAppleEmail() }
        runCurrent()
        assertIs<AppleEmailAuthResult.Error>(adapter.requestAppleEmail())
        adapter.onHostPaused()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        adapter.onHostResumed()
        runCurrent()
        assertEquals(1, broker.takes)
        broker.takeGate!!.complete(Unit)
        runCurrent()
        assertEquals(AppleEmailAuthResult.Success("test@example.com"), first.await())
        assertEquals(1, broker.starts)
        adapter.close()
    }

    @Test
    fun cancellationAndDestroyFenceLateResponsesAndColdReturn() = runTest {
        val broker = broker().apply { takeGate = CompletableDeferred() }
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val first = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        first.cancel()
        runCurrent()
        broker.outcome = AppleBrokerResult.Success("late@example.com")
        broker.takeGate!!.complete(Unit)
        runCurrent()
        assertTrue(first.isCancelled)
        adapter.close()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        adapter.onHostResumed()
        runCurrent()
        assertEquals(1, broker.takes)
        assertEquals(1, broker.closes)
        adapter.close()
        assertEquals(1, broker.closes)
    }

    @Test
    fun badStartAndUnavailableBrowserReturnSafeError() = runTest {
        for (badStart in listOf(true, false)) {
            val broker = broker().apply {
                if (badStart) startResult = AppleBrokerStart(id, "https://attacker.example/", now + 300)
            }
            val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser { error("secret URL must not leak") },
                AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
            val result = adapter.requestAppleEmail()
            assertIs<AppleEmailAuthResult.Error>(result)
            assertFalse(result.message.contains("secret"))
            adapter.close()
        }
    }

    @Test
    fun lateStartAfterCallerCancellationDoesNotOpenBrowserAndCancelsRemote() = runTest {
        val broker = broker().apply { startGate = CompletableDeferred() }
        var launches = 0
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser { launches++ },
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val request = async { adapter.requestAppleEmail() }
        runCurrent()
        request.cancel()
        runCurrent()
        broker.startGate!!.complete(Unit)
        runCurrent()
        assertTrue(request.isCancelled)
        assertEquals(0, launches)
        assertEquals(1, broker.cancels)
        adapter.close()
        runCurrent()
    }

    @Test
    fun hostRecreationCancelsAttemptButRetainedPortAcceptsFreshTap() = runTest {
        val broker = broker()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val first = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onHostDetached()
        runCurrent()
        assertIs<AppleEmailAuthResult.Cancelled>(first.await())
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        assertEquals(0, broker.takes)
        val freshId = "C".repeat(42) + "A"
        broker.startResult = AppleBrokerStart(freshId, authorizationUrl(), now + 300)
        val second = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        assertFalse(second.isCompleted)
        broker.outcome = AppleBrokerResult.Success("fresh@example.com")
        adapter.onReturn("android.intent.action.VIEW", returnUri(freshId))
        runCurrent()
        assertEquals(AppleEmailAuthResult.Success("fresh@example.com"), second.await())
        adapter.close()
        runCurrent()
    }

    @Test
    fun coldAdapterIgnoresReturnAndProtocolExpirySettlesOnce() = runTest {
        val broker = broker()
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        adapter.onHostResumed()
        runCurrent()
        assertEquals(0, broker.takes)
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        advanceTimeBy(300_001)
        runCurrent()
        assertIs<AppleEmailAuthResult.Error>(result.await())
        assertEquals(1, broker.cancels)
        adapter.close()
        runCurrent()
    }

    @Test
    fun callbackDuringPendingResumeProbeIsNotLost() = runTest {
        val broker = broker().apply { takeGate = CompletableDeferred() }
        val adapter = AppleEmailRequesterAndroid(broker, AppleEmailBrowser {},
            AppleEmailRuntime(StandardTestDispatcher(testScheduler), { now }, { verifier }))
        val result = async { adapter.requestAppleEmail() }
        runCurrent()
        adapter.onHostPaused()
        adapter.onHostResumed()
        runCurrent()
        adapter.onReturn("android.intent.action.VIEW", returnUri())
        runCurrent()
        broker.outcome = AppleBrokerResult.Success("completed@example.com")
        broker.takeGate!!.complete(Unit)
        runCurrent()
        assertEquals(2, broker.takes)
        assertEquals(AppleEmailAuthResult.Success("completed@example.com"), result.await())
        adapter.close()
        runCurrent()
    }
}
