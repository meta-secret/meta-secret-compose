package core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import metasecret.project.com.BuildConfig

class LogFormatterAndroid : LogFormatterInterface {
    override fun formatLogMessage(message: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "[$timestamp] $message"
    }

    override fun logE2eDiagnostic(message: String) {
        if (!BuildConfig.DEBUG || BuildConfig.META_SECRET_ENV != "local") return
        Log.i("MetaSecretE2E", "E2E_DIAG $message")
    }
}
