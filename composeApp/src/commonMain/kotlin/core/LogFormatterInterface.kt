package core

interface LogFormatterInterface {
    fun formatLogMessage(message: String): String

    /** Platform sink for payload-free diagnostics collected by local UI E2E. */
    fun logE2eDiagnostic(message: String) {
        println(formatLogMessage("E2E_DIAG $message"))
    }
}
