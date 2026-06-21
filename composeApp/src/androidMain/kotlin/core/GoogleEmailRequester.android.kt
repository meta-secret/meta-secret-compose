package core

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metasecret.project.com.BuildConfig

class GoogleEmailRequesterAndroid(
    private val activity: FragmentActivity,
) : GoogleEmailRequesterInterface {

    private val credentialManager = CredentialManager.create(activity)

    override suspend fun requestGoogleEmail(): GoogleEmailAuthResult = withContext(Dispatchers.Main) {
        val clientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            return@withContext GoogleEmailAuthResult.Error("Google web client ID is not configured")
        }

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential

            when (credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val email = googleCredential.id.trim()
                        if (email.isBlank()) {
                            GoogleEmailAuthResult.Error("Google did not return an email address")
                        } else {
                            GoogleEmailAuthResult.Success(email)
                        }
                    } else {
                        GoogleEmailAuthResult.Error("Unexpected Google credential type: ${credential.type}")
                    }
                }

                else -> GoogleEmailAuthResult.Error("Unsupported credential returned by Google sign-in")
            }
        } catch (e: Exception) {
            val message = e.localizedMessage ?: e.message.orEmpty()
            if (message.contains("cancel", ignoreCase = true)) {
                GoogleEmailAuthResult.Cancelled
            } else {
                GoogleEmailAuthResult.Error(message.ifBlank { "Google sign-in failed" })
            }
        }
    }
}
