package core.email

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object AndroidAppleAuthGateway {
    private val pendingResult = AtomicReference<CompletableDeferred<EmailSelectionResult>?>(null)
    private val expectedState = AtomicReference<String?>(null)
    private val expectedRedirectUri = AtomicReference<String?>(null)

    suspend fun startAuth(
        activity: FragmentActivity,
        config: AndroidEmailAuthConfig,
    ): EmailSelectionResult {
        if (config.appleClientId.isBlank() || config.appleRedirectUri.isBlank()) {
            return EmailSelectionResult.Error(
                message = "Apple sign-in is not configured",
                provider = EmailProvider.APPLE,
            )
        }

        val deferred = CompletableDeferred<EmailSelectionResult>()
        val state = UUID.randomUUID().toString()
        pendingResult.set(deferred)
        expectedState.set(state)
        expectedRedirectUri.set(config.appleRedirectUri)

        val authUri = Uri.parse("https://appleid.apple.com/auth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", config.appleClientId)
            .appendQueryParameter("redirect_uri", config.appleRedirectUri)
            .appendQueryParameter("response_type", "code id_token")
            .appendQueryParameter("response_mode", "fragment")
            .appendQueryParameter("scope", "email")
            .appendQueryParameter("state", state)
            .build()

        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, authUri)
        } catch (exception: Exception) {
            clearPending()
            return EmailSelectionResult.Error(
                message = exception.message ?: "Failed to open Apple sign-in",
                provider = EmailProvider.APPLE,
                cause = exception::class.simpleName,
            )
        }

        return withTimeoutOrNull(120_000L) {
            deferred.await()
        } ?: run {
            clearPending()
            EmailSelectionResult.Cancelled
        }
    }

    fun handleRedirect(intent: Intent?) {
        val deferred = pendingResult.get() ?: return
        val data = intent?.data ?: return
        val redirectUri = expectedRedirectUri.get() ?: return
        if (data.toString().startsWith(redirectUri).not()) return

        val params = data.queryParameterNames.associateWith { name ->
            data.getQueryParameter(name).orEmpty()
        }.toMutableMap()
        params.putAll(parseFragmentParams(data.fragment))

        val returnedState = params["state"].orEmpty()
        if (returnedState.isNotBlank() && returnedState != expectedState.get()) {
            completeIfNeeded(
                EmailSelectionResult.Error(
                    message = "Apple sign-in state mismatch",
                    provider = EmailProvider.APPLE,
                )
            )
            return
        }

        when {
            params["error"].equals("access_denied", ignoreCase = true) -> {
                completeIfNeeded(EmailSelectionResult.Cancelled)
            }
            params["email"].isNullOrBlank().not() -> {
                val email = params["email"].orEmpty()
                completeIfNeeded(mapEmailResult(email))
            }
            params["id_token"].isNullOrBlank().not() -> {
                val email = extractEmailFromIdToken(params["id_token"].orEmpty())
                if (email.isNullOrBlank()) {
                    completeIfNeeded(
                        EmailSelectionResult.Error(
                            message = "Apple did not return an email address",
                            provider = EmailProvider.APPLE,
                        )
                    )
                } else {
                    completeIfNeeded(mapEmailResult(email))
                }
            }
            else -> {
                completeIfNeeded(
                    EmailSelectionResult.Error(
                        message = "Apple did not return an email address",
                        provider = EmailProvider.APPLE,
                    )
                )
            }
        }
    }

    private fun mapEmailResult(email: String): EmailSelectionResult {
        val normalized = normalizeEmailInput(email)
        return when {
            normalized.isBlank() -> EmailSelectionResult.Error(
                message = "Apple did not return an email address",
                provider = EmailProvider.APPLE,
            )
            isApplePrivateRelayEmail(normalized) -> EmailSelectionResult.PrivateRelay(
                email = normalized,
                provider = EmailProvider.APPLE,
            )
            isValidEmail(normalized) -> EmailSelectionResult.Success(
                email = normalized,
                provider = EmailProvider.APPLE,
            )
            else -> EmailSelectionResult.Error(
                message = "Apple returned an invalid email address",
                provider = EmailProvider.APPLE,
            )
        }
    }

    private fun extractEmailFromIdToken(idToken: String): String? {
        val segments = idToken.split(".")
        if (segments.size < 2) return null
        val payloadBytes = android.util.Base64.decode(
            segments[1],
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
        val payload = payloadBytes.decodeToString()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(payload)
        return json.jsonObject["email"]?.jsonPrimitive?.content
    }

    private fun parseFragmentParams(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank()) return emptyMap()
        return fragment.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val key = Uri.decode(parts[0])
                val value = Uri.decode(parts[1])
                key to value
            }
            .toMap()
    }

    private fun completeIfNeeded(result: EmailSelectionResult) {
        pendingResult.getAndSet(null)?.complete(result)
        expectedState.set(null)
        expectedRedirectUri.set(null)
    }

    private fun clearPending() {
        pendingResult.getAndSet(null)?.cancel()
        expectedState.set(null)
        expectedRedirectUri.set(null)
    }
}
