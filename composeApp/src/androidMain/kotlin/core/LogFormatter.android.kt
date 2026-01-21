package core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFormatterAndroid : LogFormatterInterface {
    override fun formatLogMessage(message: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "[$timestamp] $message"
    }
}
