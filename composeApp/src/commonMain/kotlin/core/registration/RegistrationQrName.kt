package core.registration

/** QR carries a plain Core Vault name, not an executable link or an authorization token. */
fun registrationQrName(raw: String): String? {
    val name = raw.trim()
    return name.takeIf {
        it.isNotEmpty() && it.length <= 254 && it.none(Char::isISOControl) &&
            !it.contains("://") && !it.startsWith("{") && !it.startsWith("[")
    }
}
