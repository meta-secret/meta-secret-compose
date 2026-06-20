package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalForeignApi::class)
class AppleEmailRequesterIos : AppleEmailRequesterInterface {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestAppleEmail(): AppleEmailAuthResult = withContext(Dispatchers.Default) {
        val response = runCatching {
            SwiftBridge().requestAppleEmail()
        }.getOrElse { error ->
            return@withContext AppleEmailAuthResult.Error(error.message ?: "Apple sign-in failed")
        }

        parseResponse(response)
    }

    private fun parseResponse(response: String): AppleEmailAuthResult {
        val payload = runCatching {
            json.parseToJsonElement(response).jsonObject
        }.getOrElse { error ->
            return AppleEmailAuthResult.Error(error.message ?: "Apple sign-in failed")
        }

        return when (payload["kind"]?.jsonPrimitive?.content) {
            "success" -> {
                val email = payload["email"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (email.isBlank()) {
                    AppleEmailAuthResult.Error("Apple did not return an email address")
                } else {
                    AppleEmailAuthResult.Success(email)
                }
            }

            "cancelled" -> AppleEmailAuthResult.Cancelled
            "private_relay" -> {
                val email = payload["email"]?.jsonPrimitive?.content?.trim().orEmpty()
                AppleEmailAuthResult.Error(
                    payload["message"]?.jsonPrimitive?.content
                        ?: if (email.isBlank()) "Apple provided a private relay address" else "Apple provided a private relay address: $email"
                )
            }

            else -> {
                val message = payload["message"]?.jsonPrimitive?.content
                    ?: "Apple sign-in failed"
                AppleEmailAuthResult.Error(message)
            }
        }
    }
}
