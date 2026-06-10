package core.email

enum class EmailProvider {
    APPLE,
    GOOGLE,
    MANUAL,
}

sealed class EmailSelectionResult {
    data class Success(
        val email: String,
        val provider: EmailProvider,
    ) : EmailSelectionResult()

    data class PrivateRelay(
        val email: String,
        val provider: EmailProvider = EmailProvider.APPLE,
    ) : EmailSelectionResult()

    data object Cancelled : EmailSelectionResult()

    data class Error(
        val message: String,
        val provider: EmailProvider? = null,
        val cause: String? = null,
    ) : EmailSelectionResult()
}

interface EmailSelectionCoordinatorInterface {
    suspend fun selectEmail(provider: EmailProvider): EmailSelectionResult
}

data class EmailSelectionPlatformConfig(
    val providerOrder: List<EmailProvider>,
)

private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun normalizeEmailInput(email: String): String = email.trim()

fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

fun isApplePrivateRelayEmail(email: String): Boolean {
    val normalized = normalizeEmailInput(email)
    return normalized.contains("@") && normalized.substringAfter("@").equals(
        "privaterelay.appleid.com",
        ignoreCase = true,
    )
}
