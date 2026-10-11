package core.metaSecretCore

import kotlin.test.Test
import kotlin.test.assertEquals

class MetaSecretJoinEventsEndpointTest {
    @Test
    fun endpointReplacesStateEventsPathForLocalEnvironment() {
        assertEquals(
            "http://127.0.0.1:3000/join-events",
            normalizeJoinEventsEndpoint("http://127.0.0.1:3000/state-events", "local", "http://localhost/state-events"),
        )
    }

    @Test
    fun endpointKeepsAnExplicitJoinEventsPath() {
        assertEquals(
            "https://example.test/custom/join-events",
            normalizeJoinEventsEndpoint("https://example.test/custom/join-events", "remote", "http://localhost/state-events"),
        )
    }
}
