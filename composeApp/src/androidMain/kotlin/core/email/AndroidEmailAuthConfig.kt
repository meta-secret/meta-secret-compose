package core.email

data class AndroidEmailAuthConfig(
    val googleClientId: String,
    val appleClientId: String,
    val appleRedirectUri: String,
)
