package ui.liveboard

import com.metaSecret.ios.SwiftBridge
import core.metaSecretCore.liveboard.LiveboardShowObservationRecord
import core.metaSecretCore.liveboard.encodedShowObservation
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIPasteboard
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** Clipboard behavior stays system-native; only current authorized values reach this port. */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
class LiveboardPlatformIos(identityAdmitted: Boolean = false) : LiveboardPlatformPort {
    override val decisionObservationEnabled: Boolean = identityAdmitted && Platform.isDebugBinary &&
        NSBundle.mainBundle.objectForInfoDictionaryKey("META_SECRET_ENV") == "local" &&
        NSProcessInfo.processInfo.environment["METASECRET_UI_TEST_MODE"]?.toString() == "true"

    override val navigationObservationEnabled: Boolean get() = decisionObservationEnabled

    override fun emitNavigationObservation(record: LiveboardNavigationObservationRecord) {
        if (!navigationObservationEnabled) return
        record.encodedNavigationObservation()?.let { SwiftBridge().logE2eDiagnostic(it) }
    }

    override fun closeNavigationObservation(continuous: Boolean) {
        emitNavigationObservation(LiveboardNavigationObservationRecord(0, NavigationObservationStage.CLOSE,
            continuous = continuous.navigationFlag()))
    }

    override fun emitDecisionObservation(record: LiveboardDecisionObservationRecord) {
        if (!decisionObservationEnabled) return
        record.encoded()?.let { SwiftBridge().logE2eDiagnostic(it) }
    }

    override fun emitShowObservation(record: LiveboardShowObservationRecord) {
        if (!decisionObservationEnabled) return
        record.encodedShowObservation()?.let { SwiftBridge().logE2eDiagnostic(it) }
    }

    private inner class Attachment(val owner: Any, val back: () -> Boolean) : LiveboardRootAttachment {
        override fun isCurrent(): Boolean = current === this
        override fun close() { if (current === this) current = null }
    }
    private var current: Attachment? = null
    override val reducedMotion: Boolean get() = UIAccessibilityIsReduceMotionEnabled()
    override fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment = Attachment(owner, onBack)
        .also { current = it }
    override suspend fun readClipboard(): String? = UIPasteboard.generalPasteboard.string
    override fun copyText(value: String) { UIPasteboard.generalPasteboard.string = value }
}
