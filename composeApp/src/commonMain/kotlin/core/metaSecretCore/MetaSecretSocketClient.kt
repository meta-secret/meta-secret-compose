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
    fun configure(subscription: MetaSecretSocketSubscription?)
    fun connect()
    fun disconnect()
}

data class MetaSecretSocketSubscription(
    val vaultName: String,
    val deviceId: String,
)

sealed class MetaSecretSocketEvent {
    data object Connected : MetaSecretSocketEvent()
    data object Disconnected : MetaSecretSocketEvent()
    data class StateInvalidated(val claimId: String? = null) : MetaSecretSocketEvent()
    data class Error(val message: String) : MetaSecretSocketEvent()
}

class NoopMetaSecretSocketClient : MetaSecretSocketClient {
    override val events: SharedFlow<MetaSecretSocketEvent> = MutableSharedFlow()
    override fun configure(subscription: MetaSecretSocketSubscription?) = Unit
    override fun connect() = Unit
    override fun disconnect() = Unit
}

private const val DEFAULT_STATE_EVENTS_URL = "https://api.meta-secret.org/state-events"
private const val STATE_INVALIDATED_EVENT_NAME = "state_invalidated"
private const val RECONNECT_DELAY_MS = 1_000L
private const val MAX_RECONNECT_DELAY_MS = 30_000L

class MetaSecretStateEventsClient(
    endpoint: String,
    private val httpClient: HttpClient,
) : MetaSecretSocketClient {
    private val stateEventsUrl = normalizeStateEventsEndpoint(endpoint)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<MetaSecretSocketEvent>(extraBufferCapacity = 8)
    private var subscription: MetaSecretSocketSubscription? = null
    private var streamJob: Job? = null
    private var connectedVaultName: String? = null

    override val events: SharedFlow<MetaSecretSocketEvent> = _events

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
            streamStateEvents(nextSubscription.vaultName)
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

    private suspend fun streamStateEvents(vaultName: String) {
        var reconnectDelayMs = RECONNECT_DELAY_MS
        while (true) {
            try {
                openStateEventsStream(vaultName)
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

    private suspend fun openStateEventsStream(vaultName: String) {
        val url = URLBuilder(stateEventsUrl).apply {
            parameters["vaultName"] = vaultName
        }.buildString()

        httpClient.prepareGet(url) {
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
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
            _events.emit(MetaSecretSocketEvent.StateInvalidated())
        }
    }
}

private fun normalizeStateEventsEndpoint(endpoint: String): String {
    val trimmed = endpoint.trim()
    if (trimmed.isBlank() || trimmed.contains("\$(")) return DEFAULT_STATE_EVENTS_URL
    if (trimmed.contains("localhost")) return DEFAULT_STATE_EVENTS_URL
    return if (trimmed.endsWith("/state-events")) {
        trimmed
    } else {
        trimmed.trimEnd('/') + "/state-events"
    }
}

@Serializable
private data class StateInvalidationPayload(
    val type: String,
    @SerialName("vaultName")
    val vaultName: String,
)
