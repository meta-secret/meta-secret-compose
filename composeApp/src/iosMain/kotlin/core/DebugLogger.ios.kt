package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class DebugLoggerIos(
    logFormatter: LogFormatterInterface,
    private val swiftBridge: SwiftBridge = SwiftBridge()
) : DebugLogger(logFormatter) {
    init {
        swiftBridge.logE2eDiagnostic("logger-init")
    }

    override fun diagnostic(message: String) {
        swiftBridge.logE2eDiagnostic(message)
    }

    override fun e2eDiagnostic(message: String) {
        swiftBridge.logE2eDiagnostic(message)
    }

    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
        super.log(message, extra, success)
        if (message.text.contains("E2E_DIAG") || extra?.contains("E2E_DIAG") == true) {
            swiftBridge.logE2eDiagnostic("${message.tag.displayName}: ${message.text} $extra")
        }
    }

    override fun setOuterLoggerVisibility(isVisible: Boolean) {
        swiftBridge.setiOSLogsVisibility(isVisible)
    }
}
