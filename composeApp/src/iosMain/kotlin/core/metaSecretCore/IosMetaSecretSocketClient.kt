package core.metaSecretCore

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

class IosMetaSecretSocketClient(
    endpoint: String,
    environment: String,
) : MetaSecretSocketClient by MetaSecretStateEventsClient(
    endpoint = endpoint,
    environment = environment,
    localEndpoint = "http://127.0.0.1:3000/state-events",
    httpClient = HttpClient(Darwin),
)
