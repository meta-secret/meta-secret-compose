package core

import com.metaSecret.ios.SwiftBridge
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class DebugLoggerIos(
    private val swiftBridge: SwiftBridge = SwiftBridge()
) : DebugLogger() {
    override fun setOuterLoggerVisibility(isVisible: Boolean) {
        swiftBridge.setiOSLogsVisibility(isVisible)
    }
}

