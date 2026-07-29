package core.metaSecretCore

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

class IosMetaSecretSocketClient(
    endpoint: String,
) : MetaSecretSocketClient by MetaSecretStateEventsClient(
    endpoint = endpoint,
    httpClient = HttpClient(Darwin),
)
