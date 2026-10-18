package core.apple

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Bound consumption before UTF-8/JSON aggregation, even without Content-Length. */
internal suspend fun readAppleBrokerBody(channel: ByteReadChannel): String {
    try {
        val bytes = ByteArray(16_385)
        var used = 0
        while (used < bytes.size) {
            val read = channel.readAvailable(bytes, used, bytes.size - used)
            if (read < 0) break
            if (read == 0) {
                if (!channel.awaitContent(1)) break
            } else used += read
        }
        if (used > 16_384) throw AppleBrokerException()
        return bytes.copyOf(used).toString(Charsets.UTF_8)
    } finally { channel.cancel(null) }
}

/** The wire envelopes are flat; duplicate keys must not silently replace status/email. */
internal fun appleBrokerObject(body: String): JsonObject {
    if (body.toByteArray(Charsets.UTF_8).size > 16_384) throw AppleBrokerException()
    val result = Json.parseToJsonElement(body) as? JsonObject ?: throw AppleBrokerException()
    if (result.values.any { it !is JsonPrimitive }) throw AppleBrokerException()
    val keys = mutableSetOf<String>()
    var index = body.indexOf('{') + 1
    fun whitespace() { while (index < body.length && body[index].isWhitespace()) index++ }
    fun quoted(): String {
        val start = index++
        while (index < body.length) {
            when (body[index++]) {
                '\\' -> index++
                '"' -> return body.substring(start, index)
            }
        }
        throw AppleBrokerException()
    }
    while (index < body.length) {
        whitespace()
        if (body[index] == '}') break
        val key = Json.parseToJsonElement(quoted()).jsonPrimitive.content
        if (!keys.add(key)) throw AppleBrokerException()
        whitespace()
        index++ // The standard JSON parser already verified the colon and value syntax.
        whitespace()
        if (body[index] == '"') quoted()
        else while (index < body.length && body[index] !in ",}") index++
        whitespace()
        if (body[index] == '}') break
        index++
    }
    return result
}
