package core.email

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@OptIn(ExperimentalForeignApi::class)
class EmailSelectionCoordinatorIos(
    private val bridge: SwiftBridge = SwiftBridge(),
) : EmailSelectionCoordinatorInterface {
    override suspend fun selectEmail(provider: EmailProvider): EmailSelectionResult {
        return when (provider) {
            EmailProvider.GOOGLE -> requestGoogleEmail()
            EmailProvider.APPLE -> requestAppleEmail()
            EmailProvider.MANUAL -> EmailSelectionResult.Error(
                message = "Manual email selection is handled in shared UI",
                provider = provider,
            )
        }
    }

    private suspend fun requestGoogleEmail(): EmailSelectionResult = withContext(Dispatchers.Default) {
        runCatching {
            bridge.requestGoogleEmail().toEmailSelectionResult(EmailProvider.GOOGLE)
        }.getOrElse { throwable ->
            EmailSelectionResult.Error(
                message = throwable.message ?: "Google sign-in failed",
                provider = EmailProvider.GOOGLE,
                cause = throwable::class.simpleName,
            )
        }
    }

    private suspend fun requestAppleEmail(): EmailSelectionResult = withContext(Dispatchers.Default) {
        runCatching {
            bridge.requestAppleEmail().toEmailSelectionResult(EmailProvider.APPLE)
        }.getOrElse { throwable ->
            EmailSelectionResult.Error(
                message = throwable.message ?: "Apple sign-in failed",
                provider = EmailProvider.APPLE,
                cause = throwable::class.simpleName,
            )
        }
    }

    private fun String.toEmailSelectionResult(provider: EmailProvider): EmailSelectionResult {
        val response = runCatching {
            Json.decodeFromString<EmailSelectionBridgeResponse>(this)
        }.getOrElse { throwable ->
            return EmailSelectionResult.Error(
                message = throwable.message ?: "Failed to decode email auth response",
                provider = provider,
                cause = throwable::class.simpleName,
            )
        }

        return when (response.kind) {
            "success" -> {
                val email = response.email.orEmpty()
                when {
                    email.isBlank() -> EmailSelectionResult.Error(
                        message = response.message ?: "No email was returned",
                        provider = provider,
                    )
                    isApplePrivateRelayEmail(email) && provider == EmailProvider.APPLE -> EmailSelectionResult.PrivateRelay(
                        email = email,
                        provider = provider,
                    )
                    isValidEmail(email) -> EmailSelectionResult.Success(
                        email = normalizeEmailInput(email),
                        provider = provider,
                    )
                    else -> EmailSelectionResult.Error(
                        message = response.message ?: "Invalid email returned",
                        provider = provider,
                    )
                }
            }
            "private_relay" -> EmailSelectionResult.PrivateRelay(
                email = response.email.orEmpty(),
                provider = provider,
            )
            "cancelled" -> EmailSelectionResult.Cancelled
            else -> EmailSelectionResult.Error(
                message = response.message ?: "Email selection failed",
                provider = provider,
            )
        }
    }
}

@Serializable
private data class EmailSelectionBridgeResponse(
    val kind: String,
    val email: String? = null,
    val message: String? = null,
)
