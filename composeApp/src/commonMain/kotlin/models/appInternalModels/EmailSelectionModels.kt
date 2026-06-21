package models.appInternalModels

fun normalizeEmailInput(value: String): String = value.trim()

fun isValidEmail(value: String): Boolean {
    val normalized = normalizeEmailInput(value)
    if (normalized.isBlank()) {
        return false
    }

    return EMAIL_REGEX.matches(normalized)
}

fun isApplePrivateRelayEmail(value: String): Boolean {
    return normalizeEmailInput(value)
        .lowercase()
        .endsWith("@privaterelay.appleid.com")
}

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
