package core.metaSecretCore

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

class AndroidMetaSecretSocketClient(
    endpoint: String,
) : MetaSecretSocketClient by MetaSecretStateEventsClient(
    endpoint = endpoint,
    httpClient = HttpClient(OkHttp) {
        engine {
            config {
                readTimeout(0, TimeUnit.MILLISECONDS)
            }
        }
    },
)
