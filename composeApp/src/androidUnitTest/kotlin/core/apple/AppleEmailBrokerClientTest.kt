package core.apple

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleEmailBrokerClientTest {
    private val id = "A".repeat(43)
    private val verifier = "B".repeat(42) + "A"

    private class Http : AppleBrokerHttp {
        val calls = mutableListOf<Pair<String, String>>()
        var reply = AppleBrokerHttpReply(200, "{\"status\":\"pending\"}")
        var wait: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        override suspend fun post(url: String, body: String): AppleBrokerHttpReply {
            calls += url to body
            failure?.let { throw it }
            wait?.await()
            return reply
        }
        override fun close() = Unit
    }

    @Test
    fun proofUsesPkceAsciiVerifierHash() {
        assertEquals("DwBzhbb51LfusnSGBa_hqYSgo7-j8BTQnip4TOnlzRo", AppleEmailProof.challenge("A".repeat(43)))
        assertEquals("6oZqdX5MOLq_qBJ8vppAnT4fk6AP8UiP9zX8-Rev_9A",
            AppleEmailProof.challenge("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"))
        val actual = AppleEmailProof.verifier()
        assertEquals(43, actual.length)
        assertTrue(actual.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertFalse(actual == AppleEmailProof.verifier())
    }

    @Test
    fun invalidOrUnsetBrokerNeverSendsRequest() = runTest {
        for (url in listOf("", "http://auth.example.com", "https://localhost", "https://127.0.0.1",
            "https://user@auth.example.com", "https://auth.example.com?next=evil", "https://auth.example.com/path")) {
            val http = Http()
            val client = AppleEmailBrokerClient(url, http)
            assertNull(client.origin)
            assertFailsWith<AppleBrokerException> { client.start(id) }
            assertTrue(http.calls.isEmpty())
            client.close()
        }
    }

    @Test
    fun verifierOnlyEntersFixedHttpsPostBodyAndRelayIsExact() = runTest {
        val http = Http().apply { reply = AppleBrokerHttpReply(200, "{\"status\":\"success\",\"email\":\"Relay@privaterelay.appleid.com\"}") }
        val client = AppleEmailBrokerClient("https://auth.example.com", http)
        assertEquals(AppleBrokerResult.Success("Relay@privaterelay.appleid.com"), client.take(id, verifier))
        val (url, body) = http.calls.single()
        assertEquals("https://auth.example.com/apple/take", url)
        assertFalse(url.contains(verifier))
        assertEquals(verifier, Json.parseToJsonElement(body).jsonObject["verifier"]!!.jsonPrimitive.content)
        client.close()
    }

    @Test
    fun malformedOversizedRedirectAndUnknownResultsFailClosed() = runTest {
        val http = Http()
        val client = AppleEmailBrokerClient("https://auth.example.com", http)
        for (response in listOf(
            AppleBrokerHttpReply(302, "{\"status\":\"success\",\"email\":\"attacker@example.com\"}"),
            AppleBrokerHttpReply(403, "secret provider failure"), AppleBrokerHttpReply(410, "expired"),
            AppleBrokerHttpReply(200, "x".repeat(16_385)), AppleBrokerHttpReply(200, "[]"),
            AppleBrokerHttpReply(200, "{\"status\":\"unknown\"}"),
            AppleBrokerHttpReply(200, "{\"status\":\"pending\",\"email\":\"attacker@example.com\"}"),
            AppleBrokerHttpReply(200, "{\"status\":\"success\"}"),
            AppleBrokerHttpReply(200, "{\"status\":\"success\",\"email\":\"a\\nb@example.com\"}"),
            AppleBrokerHttpReply(200, "{\"status\":\"pending\",\"status\":\"success\",\"email\":\"x@example.com\"}"),
            AppleBrokerHttpReply(200, "{\"status\":\"success\",\"email\":\"first@example.com\",\"email\":\"second@example.com\"}"),
        )) {
            http.reply = response
            val error = assertFailsWith<AppleBrokerException> { client.take(id, verifier) }
            assertFalse(error.message.orEmpty().contains("secret"))
        }
        client.close()
    }

    @Test
    fun unknownLengthChunkedBodyStopsAtBoundWithoutWaitingForEof() = runTest {
        val channel = ByteChannel(autoFlush = true)
        val gate = CompletableDeferred<Unit>()
        val writer = launch {
            channel.writeFully(ByteArray(8192) { 'x'.code.toByte() })
            channel.writeFully(ByteArray(8192) { 'x'.code.toByte() })
            channel.writeFully(byteArrayOf('x'.code.toByte()))
            gate.await() // No EOF; the bounded reader must already reject.
        }
        assertFailsWith<AppleBrokerException> { readAppleBrokerBody(channel) }
        assertFalse(gate.isCompleted)
        writer.cancel()
    }

    @Test
    fun canonicalCoreStartTakeCancelWireMatchesAndroidClient() = runTest {
        val http = Http()
        val client = AppleEmailBrokerClient("https://broker.example.test", http)
        val requestId = "ERERERERERERERERERERERERERERERERERERERERERE"
        val proof = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val authorizationUrl = "https://appleid.apple.com/auth/authorize?client_id=test.apple.service&" +
            "redirect_uri=https%3A%2F%2Fbroker.example.test%2Fapple%2Fcallback&response_type=code+id_token&" +
            "response_mode=form_post&scope=email&state=IiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiI&" +
            "nonce=MzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzM"
        http.reply = AppleBrokerHttpReply(200,
            "{\"requestId\":\"$requestId\",\"authorizationUrl\":\"$authorizationUrl\",\"expiresAt\":1300}")
        val start = client.start(AppleEmailProof.challenge(proof))
        assertEquals(requestId, start.requestId)
        assertEquals(1300L, start.expiresAt)
        assertTrue(AppleEmailPolicy.start(start, client.origin!!, 1000))
        val body = Json.parseToJsonElement(http.calls.single().second).jsonObject
        assertEquals("android", body["platform"]!!.jsonPrimitive.content)
        assertEquals("6oZqdX5MOLq_qBJ8vppAnT4fk6AP8UiP9zX8-Rev_9A", body["challenge"]!!.jsonPrimitive.content)
        http.reply = AppleBrokerHttpReply(200, "{\"status\":\"pending\"}")
        assertIs<AppleBrokerResult.Pending>(client.take(requestId, proof))
        http.reply = AppleBrokerHttpReply(200, "{\"status\":\"cancelled\"}")
        assertIs<AppleBrokerResult.Cancelled>(client.take(requestId, proof))
        client.cancel(requestId, proof)
        assertEquals("https://broker.example.test/apple/cancel", http.calls.last().first)
        http.reply = AppleBrokerHttpReply(200, "{\"status\":\"error\",\"code\":\"invalid_response\"}")
        assertIs<AppleBrokerResult.Error>(client.take(requestId, proof))
        client.close()
    }

    @Test
    fun authorizationPolicyRejectsUnsafeTargetsWrongCallbackAndExpiry() {
        val url = "https://appleid.apple.com/auth/authorize?client_id=test.service&" +
            "redirect_uri=https%3A%2F%2Fauth.example.com%2Fapple%2Fcallback&response_type=code+id_token&" +
            "response_mode=form_post&scope=email&state=$id&nonce=$id"
        val start = AppleBrokerStart(id, url, 1300)
        assertTrue(AppleEmailPolicy.start(start, "https://auth.example.com", 1000))
        for (badUrl in listOf(url.replace("appleid.apple.com", "attacker.example"),
            url.replace("auth.example.com", "foreign.example"), url + "&code=untrusted",
            url + "&nonce=$id", url.replace("form_post", "query"), url + "#fragment")) {
            assertFalse(AppleEmailPolicy.start(start.copy(authorizationUrl = badUrl), "https://auth.example.com", 1000))
        }
        assertFalse(AppleEmailPolicy.start(start.copy(expiresAt = 1000), "https://auth.example.com", 1000))
        assertFalse(AppleEmailPolicy.start(start.copy(expiresAt = 1331), "https://auth.example.com", 1000))
    }

    @Test
    fun networkDeadlineIsBoundedAndExceptionsCannotExposeRequestPayload() = runTest {
        val http = Http().apply { wait = CompletableDeferred() }
        val client = AppleEmailBrokerClient("https://auth.example.com", http)
        val request = async { assertFailsWith<AppleBrokerException> { client.take(id, verifier) } }
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        assertTrue(request.isCompleted)
        request.await()
        http.wait = null
        http.failure = IllegalStateException("sensitive verifier=$verifier")
        val failure = assertFailsWith<AppleBrokerException> { client.take(id, verifier) }
        assertFalse(failure.toString().contains(verifier))
        assertNull(failure.cause)
        client.close()
    }
}
