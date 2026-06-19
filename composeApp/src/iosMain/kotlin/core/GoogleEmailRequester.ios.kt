package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class GoogleEmailRequesterIos : GoogleEmailRequesterInterface {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestGoogleEmail(): GoogleEmailAuthResult = withContext(kotlinx.coroutines.Dispatchers.Default) {
        val response = runCatching {
            SwiftBridge().requestGoogleEmail()
        }.getOrElse { error ->
            return@withContext GoogleEmailAuthResult.Error(error.message ?: "Google sign-in failed")
        }

        parseResponse(response)
    }

    private fun parseResponse(response: String): GoogleEmailAuthResult {
        val payload = runCatching {
            json.parseToJsonElement(response).jsonObject
        }.getOrElse { error ->
            return GoogleEmailAuthResult.Error(error.message ?: "Google sign-in failed")
        }

        return when (payload["kind"]?.jsonPrimitive?.content) {
            "success" -> {
                val email = payload["email"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (email.isBlank()) {
                    GoogleEmailAuthResult.Error("Google did not return an email address")
                } else {
                    GoogleEmailAuthResult.Success(email)
                }
            }

            "cancelled" -> GoogleEmailAuthResult.Cancelled
            else -> {
                val message = payload["message"]?.jsonPrimitive?.content
                    ?: "Google sign-in failed"
                GoogleEmailAuthResult.Error(message)
            }
        }
    }
}
