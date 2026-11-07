package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardShowObservationRecord

/** A view attachment is distinct from the native owner or keyed command lifetime. */
interface LiveboardRootAttachment {
    fun isCurrent(): Boolean
    fun close()
}

interface LiveboardPlatformPort {
    val navigationObservationEnabled: Boolean get() = false
    fun emitNavigationObservation(record: LiveboardNavigationObservationRecord) {}
    fun closeNavigationObservation(continuous: Boolean) {}
    val recoverObservationEnabled: Boolean get() = false
    fun emitRecoverObservation(record: LiveboardRecoverObservationRecord) {}
    fun closeRecoverObservation(continuous: Boolean) {}
    val decisionObservationEnabled: Boolean get() = false
    fun emitDecisionObservation(record: LiveboardDecisionObservationRecord) {}
    fun emitShowObservation(record: LiveboardShowObservationRecord) {}
    val reducedMotion: Boolean get() = false
    fun attach(owner: Any, onBack: () -> Boolean): LiveboardRootAttachment
    suspend fun readClipboard(): String?
    fun copyText(value: String)
}
