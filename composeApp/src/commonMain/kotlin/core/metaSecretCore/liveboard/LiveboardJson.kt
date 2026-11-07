package core.metaSecretCore.liveboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Strict wire helpers. Numeric identity stays in the original decimal token. */
internal object LiveboardJson {
    private val json = Json { isLenient = false }
    private val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val decimalPattern = Regex("0|[1-9][0-9]*")
    private val literalPattern = Regex("true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")

    fun parse(raw: String): JsonElement {
        DuplicateGuard(raw).check()
        return json.parseToJsonElement(raw)
    }

    fun record(value: JsonElement?): JsonObject = value as? JsonObject ?: invalid()

    fun fields(value: JsonObject, vararg allowed: String) {
        require(value.keys.all { it in allowed }) { "InvalidResponse" }
    }

    fun text(value: JsonElement?): String {
        val primitive = value as? JsonPrimitive ?: invalid()
        require(primitive.isString && primitive.content.isNotEmpty()) { "InvalidResponse" }
        require(primitive.content.encodeToByteArray().size <= MAX_METADATA_TEXT_BYTES) { "InvalidResponse" }
        return primitive.content
    }

    fun optionalText(value: JsonElement?): String? =
        if (value == null || value == JsonNull) null else text(value)

    fun boolean(value: JsonElement?): Boolean {
        val primitive = value as? JsonPrimitive ?: invalid()
        require(!primitive.isString) { "InvalidResponse" }
        return primitive.booleanOrNull ?: invalid()
    }

    fun choice(value: JsonElement?, vararg allowed: String): String =
        text(value).also { require(it in allowed) { "InvalidResponse" } }

    fun array(value: JsonElement?): List<JsonElement> =
        (value as? JsonArray)?.toList() ?: invalid()

    fun unique(values: List<String>) {
        require(values.toSet().size == values.size) { "InvalidResponse" }
    }

    fun uuid(value: JsonElement?): String =
        text(value).also { require(uuidPattern.matches(it)) { "InvalidResponse" } }

    fun u64(value: JsonElement?): String {
        val primitive = value as? JsonPrimitive ?: invalid()
        require(!primitive.isString && decimalPattern.matches(primitive.content)) { "InvalidResponse" }
        require(primitive.content.toULongOrNull() != null) { "InvalidResponse" }
        return primitive.content
    }

    fun version(value: JsonElement?) {
        require(u64(value) == "1") { "InvalidResponse" }
    }

    fun kind(value: JsonElement?): LiveboardKind = when (text(value)) {
        "join" -> LiveboardKind.Join
        "create" -> LiveboardKind.Create
        "recover" -> LiveboardKind.Recover
        "deleteSecret" -> LiveboardKind.DeleteSecret
        "removeDevice" -> LiveboardKind.RemoveDevice
        else -> invalid()
    }

    fun phase(value: JsonElement?): LiveboardPhase = when (text(value)) {
        "queued" -> LiveboardPhase.Queued
        "unknown" -> LiveboardPhase.Unknown
        else -> invalid()
    }

    private fun invalid(): Nothing = throw IllegalArgumentException("InvalidResponse")

    /** Run before JsonObject creation, which would silently replace duplicate keys. */
    private class DuplicateGuard(private val raw: String) {
        private var offset = 0

        fun check() {
            value(0)
            whitespace()
            require(offset == raw.length) { "InvalidResponse" }
        }

        private fun value(depth: Int) {
            require(depth < MAX_JSON_DEPTH) { "InvalidResponse" }
            whitespace()
            when (raw.getOrNull(offset)) {
                '{' -> record(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                null -> invalid()
                else -> {
                    val start = offset
                    while (raw.getOrNull(offset)?.let { it !in ",]} \n\r\t" } == true) offset++
                    require(literalPattern.matches(raw.substring(start, offset))) { "InvalidResponse" }
                }
            }
        }

        private fun record(depth: Int) {
            offset++
            whitespace()
            if (take('}')) return
            val keys = mutableSetOf<String>()
            do {
                whitespace()
                require(keys.add(string())) { "InvalidResponse" }
                whitespace()
                require(take(':')) { "InvalidResponse" }
                value(depth)
                whitespace()
                if (take('}')) return
            } while (take(','))
            invalid()
        }

        private fun array(depth: Int) {
            offset++
            whitespace()
            if (take(']')) return
            do {
                value(depth)
                whitespace()
                if (take(']')) return
            } while (take(','))
            invalid()
        }

        private fun string(): String {
            val start = offset
            require(take('"')) { "InvalidResponse" }
            while (offset < raw.length) {
                when (raw[offset++]) {
                    '\\' -> offset++
                    '"' -> return json.decodeFromString<String>(raw.substring(start, offset))
                }
            }
            invalid()
        }

        private fun whitespace() {
            while (raw.getOrNull(offset)?.let { it in " \n\r\t" } == true) offset++
        }

        private fun take(character: Char): Boolean =
            (raw.getOrNull(offset) == character).also { if (it) offset++ }
    }
}

private const val MAX_METADATA_TEXT_BYTES: Int = 4096
private const val MAX_JSON_DEPTH: Int = 128
