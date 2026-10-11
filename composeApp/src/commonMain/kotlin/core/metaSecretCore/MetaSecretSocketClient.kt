package core.metaSecretCore

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface MetaSecretSocketClient {
    val events: SharedFlow<MetaSecretSocketEvent>
    val joinResultEvents: SharedFlow<MetaSecretJoinResultEvent>
    fun configure(subscription: MetaSecretSocketSubscription?)
    fun connect()
    fun disconnect()
    fun configureJoinResult(subscription: MetaSecretJoinResultSubscription?) = Unit
    fun connectJoinResult() = Unit
    fun disconnectJoinResult() = Unit
}

data class MetaSecretSocketSubscription(
    val vaultName: String,
    val deviceId: String,
    val authorizationTokenProvider: suspend () -> String,
)

data class MetaSecretJoinResultSubscription(
    val vaultName: String,
    val authorizationTokenProvider: suspend () -> String,
)

enum class MetaSecretJoinResultOutcome {
    ACCEPTED,
    DECLINED,
}

sealed class MetaSecretJoinResultEvent {
    data class Resolved(val outcome: MetaSecretJoinResultOutcome) : MetaSecretJoinResultEvent()
    data class Error(val message: String, val terminal: Boolean = false) : MetaSecretJoinResultEvent()
}

sealed class MetaSecretSocketEvent {
    data object Connected : MetaSecretSocketEvent()
    data object Disconnected : MetaSecretSocketEvent()
    data class StateInvalidated(
        val scope: String? = null,
        val claimId: String? = null,
        val decisionOutcome: String? = null,
    ) : MetaSecretSocketEvent()
    data class Error(val message: String) : MetaSecretSocketEvent()
}

class NoopMetaSecretSocketClient : MetaSecretSocketClient {
    override val events: SharedFlow<MetaSecretSocketEvent> = MutableSharedFlow()
    override val joinResultEvents: SharedFlow<MetaSecretJoinResultEvent> = MutableSharedFlow()
    override fun configure(subscription: MetaSecretSocketSubscription?) = Unit
    override fun connect() = Unit
    override fun disconnect() = Unit
}

private const val DEFAULT_STATE_EVENTS_URL = "https://api.meta-secret.org/state-events"
private const val DEFAULT_JOIN_EVENTS_URL = "https://api.meta-secret.org/join-events"
private const val STATE_INVALIDATED_EVENT_NAME = "state_invalidated"
private const val JOIN_RESULT_EVENT_NAME = "join_result"
private const val RECONNECT_DELAY_MS = 1_000L
private const val MAX_RECONNECT_DELAY_MS = 30_000L

class MetaSecretStateEventsClient(
    endpoint: String,
    environment: String = "remote",
    localEndpoint: String,
    private val httpClient: HttpClient,
) : MetaSecretSocketClient {
    private val stateEventsUrl = normalizeStateEventsEndpoint(endpoint, environment, localEndpoint)
    private val joinEventsUrl = normalizeJoinEventsEndpoint(endpoint, environment, localEndpoint)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    // Keep the latest connection state available to a collector that starts just
    // after the native lifecycle initializes. Without replay, the first Connected
    // event can be lost and no refresh follows until the next invalidation.
    private val _events = MutableSharedFlow<MetaSecretSocketEvent>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    private val _joinResultEvents = MutableSharedFlow<MetaSecretJoinResultEvent>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    private var subscription: MetaSecretSocketSubscription? = null
    private var streamJob: Job? = null
    private var joinResultSubscription: MetaSecretJoinResultSubscription? = null
    private var joinResultJob: Job? = null
    private var connectedJoinResultVault: String? = null
    private var connectedVaultName: String? = null

    override val events: SharedFlow<MetaSecretSocketEvent> = _events
    override val joinResultEvents: SharedFlow<MetaSecretJoinResultEvent> = _joinResultEvents

    override fun configure(subscription: MetaSecretSocketSubscription?) {
        this.subscription = subscription
        if (subscription == null) {
            disconnect()
        }
    }

    override fun connect() {
        val nextSubscription = subscription ?: return
        if (streamJob?.isActive == true && connectedVaultName == nextSubscription.vaultName) return
        disconnect()
        connectedVaultName = nextSubscription.vaultName
        streamJob = scope.launch {
            streamStateEvents(nextSubscription)
        }
    }

    override fun disconnect() {
        val job = streamJob ?: return
        streamJob = null
        connectedVaultName = null
        scope.launch {
            job.cancelAndJoin()
            _events.emit(MetaSecretSocketEvent.Disconnected)
        }
    }

    override fun configureJoinResult(subscription: MetaSecretJoinResultSubscription?) {
        joinResultSubscription = subscription
        if (subscription == null) disconnectJoinResult()
    }

    override fun connectJoinResult() {
        val nextSubscription = joinResultSubscription ?: return
        if (joinResultJob?.isActive == true && connectedJoinResultVault == nextSubscription.vaultName) return
        disconnectJoinResult()
        connectedJoinResultVault = nextSubscription.vaultName
        joinResultJob = scope.launch { streamJoinResult(nextSubscription) }
    }

    override fun disconnectJoinResult() {
        joinResultJob?.cancel()
        joinResultJob = null
        connectedJoinResultVault = null
    }

    private suspend fun streamStateEvents(subscription: MetaSecretSocketSubscription) {
        var reconnectDelayMs = RECONNECT_DELAY_MS
        while (true) {
            try {
                openStateEventsStream(subscription)
                reconnectDelayMs = RECONNECT_DELAY_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                _events.emit(MetaSecretSocketEvent.Error("state events timeout: ${e.message}"))
                delay(reconnectDelayMs)
                reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                continue
            } catch (e: Exception) {
                _events.emit(MetaSecretSocketEvent.Error("state events failed: ${e.message}"))
                delay(reconnectDelayMs)
                reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                continue
            }
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun openStateEventsStream(subscription: MetaSecretSocketSubscription) {
        val vaultName = subscription.vaultName
        val url = URLBuilder(stateEventsUrl).apply {
            parameters["vaultName"] = vaultName
        }.buildString()

        httpClient.prepareGet(url) {
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.Authorization, "Bearer ${subscription.authorizationTokenProvider()}")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                _events.emit(MetaSecretSocketEvent.Error("state events HTTP ${response.status.value}"))
                return@execute
            }
            if (response.status == HttpStatusCode.NoContent) return@execute

            _events.emit(MetaSecretSocketEvent.Connected)
            readServerSentEvents(vaultName, response.bodyAsChannel())
        }
    }

    private suspend fun streamJoinResult(subscription: MetaSecretJoinResultSubscription) {
        var reconnectDelayMs = RECONNECT_DELAY_MS
        while (true) {
            try {
                val completed = openJoinResultStream(subscription)
                if (completed) return
                reconnectDelayMs = RECONNECT_DELAY_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                _joinResultEvents.emit(MetaSecretJoinResultEvent.Error("join result stream timeout: ${e.message}"))
            } catch (e: Exception) {
                _joinResultEvents.emit(MetaSecretJoinResultEvent.Error("join result stream failed: ${e.message}"))
            }
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        }
    }

    private suspend fun openJoinResultStream(subscription: MetaSecretJoinResultSubscription): Boolean {
        val url = URLBuilder(joinEventsUrl).apply {
            parameters["vaultName"] = subscription.vaultName
        }.buildString()

        val authorizationToken = try {
            subscription.authorizationTokenProvider()
        } catch (e: Exception) {
            _joinResultEvents.emit(MetaSecretJoinResultEvent.Error(
                "unable to authorize this pending join request: ${e.message}", terminal = true
            ))
            return true
        }
        return httpClient.prepareGet(url) {
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.Authorization, "Bearer $authorizationToken")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val terminal = response.status == HttpStatusCode.Unauthorized ||
                    response.status == HttpStatusCode.Forbidden
                _joinResultEvents.emit(MetaSecretJoinResultEvent.Error(
                    "join result HTTP ${response.status.value}", terminal = terminal
                ))
                return@execute terminal
            }
            readJoinResultEvents(response.bodyAsChannel())
        }
    }

    private suspend fun readJoinResultEvents(channel: io.ktor.utils.io.ByteReadChannel): Boolean {
        var eventName: String? = null
        val dataLines = mutableListOf<String>()
        while (true) {
            val line = channel.readUTF8Line() ?: return false
            if (line.isEmpty()) {
                if (eventName == JOIN_RESULT_EVENT_NAME && dataLines.isNotEmpty()) {
                    val payload = try {
                        json.decodeFromString<JoinResultPayload>(dataLines.joinToString("\n"))
                    } catch (_: Exception) {
                        _joinResultEvents.emit(MetaSecretJoinResultEvent.Error("invalid join result payload"))
                        eventName = null
                        dataLines.clear()
                        continue
                    }
                    val outcome = when (payload.outcome) {
                        "accepted" -> MetaSecretJoinResultOutcome.ACCEPTED
                        "declined" -> MetaSecretJoinResultOutcome.DECLINED
                        else -> null
                    }
                    if (outcome != null) {
                        _joinResultEvents.emit(MetaSecretJoinResultEvent.Resolved(outcome))
                        return true
                    }
                    _joinResultEvents.emit(MetaSecretJoinResultEvent.Error("unknown join result outcome"))
                }
                eventName = null
                dataLines.clear()
                continue
            }
            when {
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                line.startsWith("data:") -> dataLines += line.substringAfter("data:").trimStart()
            }
        }
    }

    private suspend fun readServerSentEvents(vaultName: String, channel: io.ktor.utils.io.ByteReadChannel) {
        var eventName: String? = null
        val dataLines = mutableListOf<String>()

        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) {
                dispatchServerSentEvent(vaultName, eventName, dataLines.joinToString("\n"))
                eventName = null
                dataLines.clear()
                continue
            }
            when {
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                line.startsWith("data:") -> dataLines += line.substringAfter("data:").trimStart()
            }
        }
    }

    private suspend fun dispatchServerSentEvent(vaultName: String, eventName: String?, data: String) {
        if (eventName != STATE_INVALIDATED_EVENT_NAME || data.isBlank()) return
        val event = try {
            json.decodeFromString<StateInvalidationPayload>(data)
        } catch (_: Exception) {
            _events.emit(MetaSecretSocketEvent.Error("invalid state invalidation payload"))
            return
        }
        if (event.type == STATE_INVALIDATED_EVENT_NAME && event.vaultName == vaultName) {
            _events.emit(
                MetaSecretSocketEvent.StateInvalidated(
                    scope = event.scope,
                    claimId = event.e2eSignal?.claimId,
                    decisionOutcome = event.e2eSignal?.outcome,
                )
            )
        }
    }
}

enum class MetaSecretServerEnvironment {
    Local,
    Remote,
}

private fun parseMetaSecretServerEnvironment(environment: String): MetaSecretServerEnvironment {
    return when (environment.trim().lowercase()) {
        "local" -> MetaSecretServerEnvironment.Local
        "remote", "prod", "production" -> MetaSecretServerEnvironment.Remote
        else -> MetaSecretServerEnvironment.Remote
    }
}

private fun normalizeStateEventsEndpoint(endpoint: String, environment: String, localEndpoint: String): String {
    val trimmed = endpoint.trim()
    if (trimmed.isBlank() || trimmed.contains("\$(")) {
        return when (parseMetaSecretServerEnvironment(environment)) {
            MetaSecretServerEnvironment.Local -> localEndpoint
            MetaSecretServerEnvironment.Remote -> DEFAULT_STATE_EVENTS_URL
        }
    }
    return if (trimmed.endsWith("/state-events")) {
        trimmed
    } else {
        trimmed.trimEnd('/') + "/state-events"
    }
}

internal fun normalizeJoinEventsEndpoint(endpoint: String, environment: String, localEndpoint: String): String {
    val trimmed = endpoint.trim()
    if (trimmed.isBlank() || trimmed.contains("\$(")) {
        return when (parseMetaSecretServerEnvironment(environment)) {
            MetaSecretServerEnvironment.Local -> localEndpoint.substringBeforeLast('/') + "/join-events"
            MetaSecretServerEnvironment.Remote -> DEFAULT_JOIN_EVENTS_URL
        }
    }
    return when {
        trimmed.endsWith("/join-events") -> trimmed
        trimmed.endsWith("/state-events") -> trimmed.removeSuffix("/state-events") + "/join-events"
        else -> trimmed.trimEnd('/') + "/join-events"
    }
}

@Serializable
private data class StateInvalidationPayload(
    val type: String,
    @SerialName("vaultName")
    val vaultName: String,
    val scope: String? = null,
    @SerialName("e2eSignal")
    val e2eSignal: E2eSignalPayload? = null,
)

@Serializable
private data class E2eSignalPayload(
    val claimId: String,
    val outcome: String,
)

@Serializable
private data class JoinResultPayload(
    @SerialName("requestId")
    val requestId: String,
    val outcome: String,
)
