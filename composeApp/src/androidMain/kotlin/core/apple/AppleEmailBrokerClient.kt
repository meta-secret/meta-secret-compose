package core.apple

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class AppleBrokerHttpReply(val status: Int, val body: String)

internal interface AppleBrokerHttp {
    suspend fun post(url: String, body: String): AppleBrokerHttpReply
    fun close()
}

internal class AppleEmailBrokerClient(baseUrl: String, http: AppleBrokerHttp? = null) : AppleEmailBroker {
    override val origin = AppleEmailPolicy.origin(baseUrl)
    private var http: AppleBrokerHttp? = http
    private var closed = false

    override suspend fun start(challenge: String): AppleBrokerStart {
        if (!AppleEmailProof.valid(challenge)) throw AppleBrokerException()
        val result = post("start", buildJsonObject { put("platform", "android"); put("challenge", challenge) })
        if (result.keys != setOf("requestId", "authorizationUrl", "expiresAt")) throw AppleBrokerException()
        val expiry = result["expiresAt"] as? JsonPrimitive ?: throw AppleBrokerException()
        if (expiry.isString) throw AppleBrokerException()
        return AppleBrokerStart(string(result, "requestId"), string(result, "authorizationUrl"),
            expiry.longOrNull ?: throw AppleBrokerException())
    }

    override suspend fun take(requestId: String, verifier: String): AppleBrokerResult {
        val result = post("take", proof(requestId, verifier))
        if (!setOf("status", "email", "code").containsAll(result.keys)) throw AppleBrokerException()
        val status = string(result, "status")
        if ("code" in result && (!string(result, "code").matches(Regex("[a-z_]{1,64}")) || status !in setOf("error",
            "cancelled"))) {
            throw AppleBrokerException()
        }
        if (status != "success" && "email" in result) throw AppleBrokerException()
        return when (status) {
            "pending" -> AppleBrokerResult.Pending
            "cancelled" -> AppleBrokerResult.Cancelled
            "error" -> AppleBrokerResult.Error
            "success" -> AppleBrokerResult.Success(string(result, "email").also {
                if (!AppleEmailPolicy.email(it)) throw AppleBrokerException()
            })
            else -> throw AppleBrokerException()
        }
    }

    override suspend fun cancel(requestId: String, verifier: String) {
        val result = post("cancel", proof(requestId, verifier))
        if (result.keys != setOf("status") || string(result, "status") != "cancelled") throw AppleBrokerException()
    }

    override fun close() {
        if (closed) return
        closed = true
        http?.close()
        http = null
    }

    private fun proof(requestId: String, verifier: String): JsonObject {
        if (!AppleEmailProof.valid(requestId) || !AppleEmailProof.valid(verifier)) throw AppleBrokerException()
        return buildJsonObject { put("requestId", requestId); put("verifier", verifier) }
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val base = origin ?: throw AppleBrokerException()
        if (closed) throw AppleBrokerException()
        return try {
            val transport = http ?: KtorAppleBrokerHttp().also { http = it }
            val response = withTimeoutOrNull(30_000) { transport.post("$base/apple/$path", body.toString()) }
                ?: throw AppleBrokerException()
            if (response.status != 200 || response.body.toByteArray(Charsets.UTF_8).size > 16_384
                ) throw AppleBrokerException()
            appleBrokerObject(response.body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // HTTP/provider exceptions can contain complete URLs or response bodies.
            throw AppleBrokerException()
        }
    }

    private fun string(value: JsonObject, name: String): String {
        val field = value[name] as? JsonPrimitive ?: throw AppleBrokerException()
        if (!field.isString) throw AppleBrokerException()
        return field.content
    }
}

private class KtorAppleBrokerHttp : AppleBrokerHttp {
    private val client = HttpClient(OkHttp) {
        followRedirects = false
        engine {
            config {
                followRedirects(false)
                followSslRedirects(false)
                callTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    override suspend fun post(url: String, body: String): AppleBrokerHttpReply = withContext(Dispatchers.IO) {
        withTimeoutOrNull(30_000) {
            val response = client.post(url) { contentType(ContentType.Application.Json); setBody(body) }
            if (response.status.value != 200) {
                response.bodyAsChannel().cancel(null)
                return@withTimeoutOrNull AppleBrokerHttpReply(response.status.value, "")
            }
            AppleBrokerHttpReply(200, readAppleBrokerBody(response.bodyAsChannel()))
        } ?: throw AppleBrokerException()
    }

    override fun close() = client.close()
}
