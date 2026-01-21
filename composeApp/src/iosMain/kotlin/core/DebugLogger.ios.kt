package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class DebugLoggerIos(
    logFormatter: LogFormatterInterface,
    private val swiftBridge: SwiftBridge = SwiftBridge()
) : DebugLogger(logFormatter) {
    override fun setOuterLoggerVisibility(isVisible: Boolean) {
        swiftBridge.setiOSLogsVisibility(isVisible)
    }
}

