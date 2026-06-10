package core.email

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import core.DebugLoggerInterface
import core.LogTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import metasecret.project.com.BuildConfig
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException

class AndroidEmailSelectionCoordinator(
    private val logger: DebugLoggerInterface,
    private val config: AndroidEmailAuthConfig,
) : EmailSelectionCoordinatorInterface {

    override suspend fun selectEmail(provider: EmailProvider): EmailSelectionResult {
        val activity = AndroidEmailAuthEnvironment.activity
            ?: return EmailSelectionResult.Error(
                message = "No active activity is available",
                provider = provider,
            )

        return when (provider) {
            EmailProvider.GOOGLE -> selectGoogleEmail(activity)
            EmailProvider.APPLE -> AndroidAppleAuthGateway.startAuth(activity, config)
            EmailProvider.MANUAL -> EmailSelectionResult.Error(
                message = "Manual provider selection is handled in shared UI",
                provider = provider,
            )
        }
    }

    private suspend fun selectGoogleEmail(activity: FragmentActivity): EmailSelectionResult {
        if (config.googleClientId.isBlank()) {
            return EmailSelectionResult.Error(
                message = "Google sign-in is not configured",
                provider = EmailProvider.GOOGLE,
            )
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(config.googleClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = extractEmailFromGoogleCredential(googleCredential)
                when {
                    email.isNullOrBlank() -> EmailSelectionResult.Error(
                        message = "Google did not return an email address",
                        provider = EmailProvider.GOOGLE,
                    )
                    isValidEmail(email) -> EmailSelectionResult.Success(
                        email = normalizeEmailInput(email),
                        provider = EmailProvider.GOOGLE,
                    )
                    else -> EmailSelectionResult.Error(
                        message = "Google returned an invalid email address",
                        provider = EmailProvider.GOOGLE,
                    )
                }
            } else {
                EmailSelectionResult.Error(
                    message = "Unsupported Google credential type",
                    provider = EmailProvider.GOOGLE,
                )
            }
        } catch (cancellation: GetCredentialCancellationException) {
            EmailSelectionResult.Cancelled
        } catch (credentialException: GetCredentialException) {
            logger.log(
                LogTag.SignInVM.Message.WaitingForSignUp,
                "Google credential error: ${credentialException.message}",
                success = false
            )
            EmailSelectionResult.Error(
                message = credentialException.message ?: "Google sign-in failed",
                provider = EmailProvider.GOOGLE,
                cause = credentialException::class.simpleName,
            )
        } catch (exception: Exception) {
            logger.log(
                LogTag.SignInVM.Message.WaitingForSignUp,
                "Google sign-in exception: ${exception.message}",
                success = false
            )
            EmailSelectionResult.Error(
                message = exception.message ?: "Google sign-in failed",
                provider = EmailProvider.GOOGLE,
                cause = exception::class.simpleName,
            )
        }
    }

    private fun extractEmailFromGoogleCredential(credential: GoogleIdTokenCredential): String? {
        val idToken = credential.idToken
        if (!idToken.isNullOrBlank()) {
            runCatching {
                val segments = idToken.split(".")
                if (segments.size >= 2) {
                    val payload = android.util.Base64.decode(
                        segments[1],
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                    ).decodeToString()
                    val json = Json.parseToJsonElement(payload)
                    json.jsonObject["email"]?.jsonPrimitive?.content
                } else {
                    null
                }
            }.getOrNull()?.let { return it }
        }

        return credential.id.takeIf { isValidEmail(it) }
    }
}

internal fun androidEmailAuthConfig(): AndroidEmailAuthConfig {
    return AndroidEmailAuthConfig(
        googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
        appleClientId = BuildConfig.APPLE_CLIENT_ID,
        appleRedirectUri = BuildConfig.APPLE_REDIRECT_URI,
    )
}
