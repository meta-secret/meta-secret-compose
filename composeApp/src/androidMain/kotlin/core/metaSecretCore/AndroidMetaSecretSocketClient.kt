package core.metaSecretCore

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

class AndroidMetaSecretSocketClient(
    endpoint: String,
    environment: String,
) : MetaSecretSocketClient by MetaSecretStateEventsClient(
    endpoint = endpoint,
    environment = environment,
    localEndpoint = "http://10.0.2.2:3000/state-events",
    httpClient = HttpClient(OkHttp) {
        engine {
            config {
                readTimeout(0, TimeUnit.MILLISECONDS)
            }
        }
    },
)
