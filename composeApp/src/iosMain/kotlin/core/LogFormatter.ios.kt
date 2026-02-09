package core

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

class LogFormatterIos : LogFormatterInterface {
    override fun formatLogMessage(message: String): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = NSTimeZone.localTimeZone
        val timestamp = formatter.stringFromDate(NSDate())
        return "[$timestamp] $message"
    }
}
