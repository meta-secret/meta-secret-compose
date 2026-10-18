package core.apple

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal interface AppleEmailBroker {
    val origin: String?
    suspend fun start(challenge: String): AppleBrokerStart
    suspend fun take(requestId: String, verifier: String): AppleBrokerResult
    suspend fun cancel(requestId: String, verifier: String)
    fun close()
}

internal data class AppleBrokerStart(val requestId: String, val authorizationUrl: String, val expiresAt: Long)

internal sealed interface AppleBrokerResult {
    data object Pending : AppleBrokerResult
    data class Success(val email: String) : AppleBrokerResult
    data object Cancelled : AppleBrokerResult
    data object Error : AppleBrokerResult
}

internal class AppleBrokerException : Exception("Apple sign-in is unavailable")

internal data class AppleEmailRuntime(
    val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    val verifier: () -> String = AppleEmailProof::verifier,
)

internal object AppleEmailProof {
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    fun verifier(): String = codec.encode(ByteArray(32).also(SecureRandom()::nextBytes))
    fun challenge(verifier: String): String = codec.encode(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
    )
    fun valid(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{43}")) && runCatching {
        val bytes = codec.decode(value)
        bytes.size == 32 && codec.encode(bytes) == value
    }.getOrDefault(false)
}

internal object AppleEmailPolicy {
    private val domain = Regex("(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}")
    private val authorizationKeys = setOf("client_id", "redirect_uri", "response_type", "response_mode", "scope", "state", "nonce")

    fun origin(value: String): String? = runCatching {
        val uri = URI(value)
        if (value.length > 2048 || uri.scheme != "https" || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.port !in setOf(-1, 443) ||
            uri.rawPath !in listOf("", "/") || !domain.matches(uri.host.orEmpty()) ||
            uri.host.endsWith(".localhost", ignoreCase = true)) null
        else "https://${uri.host.lowercase()}"
    }.getOrNull()

    fun start(start: AppleBrokerStart, origin: String, now: Long): Boolean = runCatching {
        val uri = URI(start.authorizationUrl)
        val query = query(uri.rawQuery) ?: return false
        AppleEmailProof.valid(start.requestId) && start.expiresAt > now && start.expiresAt <= now + 330 &&
            start.authorizationUrl.length <= 8192 && uri.scheme == "https" &&
            uri.rawAuthority == "appleid.apple.com" && uri.rawPath == "/auth/authorize" &&
            uri.rawFragment == null && query.keys == authorizationKeys &&
            query["client_id"]!!.matches(Regex("[A-Za-z0-9_.-]{1,255}")) &&
            query["redirect_uri"] == "$origin/apple/callback" && query["scope"] == "email" &&
            query["response_type"] == "code id_token" && query["response_mode"] == "form_post" &&
            AppleEmailProof.valid(query["state"]!!) && AppleEmailProof.valid(query["nonce"]!!)
    }.getOrDefault(false)

    fun returnedId(action: String?, value: String?): String? = runCatching {
        if (action != "android.intent.action.VIEW" || value == null || value.length > 256) return null
        val uri = URI(value)
        val id = uri.rawQuery?.removePrefix("requestId=") ?: return null
        if (uri.scheme == "metasecret" && uri.rawAuthority == "apple-auth" &&
            uri.rawPath == "/callback" && uri.rawFragment == null && AppleEmailProof.valid(id) &&
            uri.rawQuery == "requestId=$id") id else null
    }.getOrNull()

    fun email(value: String): Boolean = value.isNotBlank() && value.length <= 254 &&
        value == value.trim() && value.none { it.isISOControl() } &&
        value.count { it == '@' } == 1 && value.substringBefore('@').isNotBlank() &&
        value.substringAfter('@').contains('.')

    private fun query(value: String?): Map<String, String>? {
        if (value == null) return null
        val result = mutableMapOf<String, String>()
        for (part in value.split('&')) {
            if (!part.contains('=')) return null
            val key = URLDecoder.decode(part.substringBefore('='), "UTF-8")
            val decoded = URLDecoder.decode(part.substringAfter('='), "UTF-8")
            if (result.put(key, decoded) != null) return null
        }
        return result
    }
}
