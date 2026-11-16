package core.metaSecretCore.liveboard

import core.metaSecretCore.MetaSecretJoinResultEvent
import core.metaSecretCore.MetaSecretJoinResultOutcome
import core.metaSecretCore.MetaSecretJoinResultSubscription
import core.metaSecretCore.MetaSecretSocketClient
import core.metaSecretCore.MetaSecretStateEventsClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import models.apiModels.AppStateModel
import models.apiModels.DeviceData
import models.apiModels.JsonConfig
import models.apiModels.Message
import models.apiModels.OpenBox
import models.apiModels.State
import models.apiModels.UserData
import models.apiModels.UserDataOutsider
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.VaultFullInfo
import models.appInternalModels.SocketRequestModel

/** Existing Ktor-core engine API: no MockEngine dependency, sockets or network. */
@OptIn(InternalAPI::class)
private class NativeRecordedJoinEngine : HttpClientEngineBase("native-join-test") {
    override val config = HttpClientEngineConfig()
    val requests = MutableStateFlow<List<String?>>(emptyList())
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        requests.update { it + data.headers[HttpHeaders.Authorization] }
        return HttpResponseData(
            HttpStatusCode.OK, GMTDate(), headersOf(HttpHeaders.ContentType, "text/event-stream"),
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel("event: join_result\ndata: {\"requestId\":\"test-request\",\"outcome\":\"accepted\"}\n\n".encodeToByteArray()),
            callContext(),
        )
    }
}

class NativeSocketTransportCancellationTest {
    @Test
    fun testRetiredRealJoinProviderCancelsActualTransportWithoutTerminalErrorOrHttp() = runBlocking {
        withTimeout(10_000) {
            val f = NativeTransitionFixture()
            val engine = NativeRecordedJoinEngine()
            val http = HttpClient(engine)
            val transport = MetaSecretStateEventsClient("https://unit.invalid", "remote", "https://unused.invalid", http)
            val streamJobs = Channel<Job>(Channel.UNLIMITED)
            val completionCauses = Channel<Throwable?>(Channel.UNLIMITED)
            var configured = false
            val observedClient = object : MetaSecretSocketClient by transport {
                override fun configureJoinResult(subscription: MetaSecretJoinResultSubscription?) {
                    if (subscription == null) transport.configureJoinResult(null)
                    else {
                        configured = true
                        transport.configureJoinResult(subscription.copy(authorizationTokenProvider = {
                            val actualJob = currentCoroutineContext().job
                            actualJob.invokeOnCompletion { completionCauses.trySend(it) }
                            check(streamJobs.trySend(actualJob).isSuccess)
                            subscription.authorizationTokenProvider()
                        }))
                    }
                }
            }
            val handler = f.socket(observedClient)
            val events = MutableStateFlow<List<MetaSecretJoinResultEvent>>(emptyList())
            val collector = launch(f.immediate, CoroutineStart.UNDISPATCHED) {
                transport.joinResultEvents.collect { event -> events.update { it + event } }
            }
            var liveJob: Job? = null
            var retiredJob: Job? = null
            try {
                f.initialize(this)
                f.pump(this)
                val user = UserData(DeviceData("join-device", "Join phone", "Android", keys = OpenBox("d", "t")), "join-vault")
                f.core.response.value = JsonConfig.json.encodeToString(AppStateModel(
                    Message(State.Vault(VaultFullInfo.Outsider(UserDataOutsider(user, UserDataOutsiderStatus.PENDING)))), true,
                ))
                handler.actionsToFollow(listOf(SocketRequestModel.WAIT_FOR_JOIN_APPROVE), null)
                f.cache.synchronization.first { it.activeRefreshes == 0 && !it.loading }
                assertTrue(configured, "Actual Socket handler must configure its captured native provider")

                transport.connectJoinResult()
                liveJob = streamJobs.receive()
                liveJob.join()
                assertEquals(null, completionCauses.receive())
                assertEquals(listOf("Bearer test-join-token"), engine.requests.value)
                assertTrue(events.value.any { it is MetaSecretJoinResultEvent.Resolved && it.outcome == MetaSecretJoinResultOutcome.ACCEPTED })
                assertEquals(1, f.core.tokens.value)
                f.cache.synchronization.first { it.activeRefreshes == 0 && !it.loading }

                // Reuse the actual captured A provider, now fenced by a distinct uninitialized owner.
                f.initialization.invalidate()
                transport.connectJoinResult()
                retiredJob = streamJobs.receive()
                retiredJob.join()
                val cause = completionCauses.receive()
                val failures = mutableListOf<String>()
                if (!retiredJob.isCancelled || cause !is CancellationException) failures.add("retired provider cancellation was swallowed by actual join transport")
                if (events.value.any { it is MetaSecretJoinResultEvent.Error && it.terminal }) failures.add("retired provider emitted a terminal join error")
                if (engine.requests.value.size != 1) failures.add("retired provider sent an additional HTTP request")
                if (f.core.tokens.value != 1) failures.add("retired captured provider entered native token generation")
                if (f.notifications.errorMessages.isNotEmpty()) failures.add("transport reported retired provider as an authorization failure")
                assertEquals(emptyList(), failures)
            } finally {
                withContext(NonCancellable) {
                    val drain = f.io.drain(this)
                    try {
                        transport.disconnectJoinResult()
                        retiredJob?.join()
                        liveJob?.join()
                        collector.cancel(); collector.join()
                        handler.pauseRefreshesAndAwait()
                        handler.onAppBackground()
                    } finally {
                        http.close()
                        f.close()
                        drain.cancel(); drain.join()
                    }
                }
            }
        }
    }
}
