package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import models.apiModels.VaultFullInfo
import models.apiModels.liveboard.LiveboardStateDecoder

/** One exact scoped attachment; the relay neither initializes native state nor grants authority. */
class LiveboardPrivacyOwner {
    internal class ErrorEvent(val attachment: LiveboardSession?, val session: LiveboardSession?)
    private val hidden = MutableStateFlow(false)
    private val attached = MutableStateFlow<LiveboardSession?>(null)
    internal fun attach(session: LiveboardSession, frame: NativeReadFrame): Boolean {
        while (true) {
            val previous = attached.value
            if (!session.isFrameCurrent(frame)) return false
            if (!attached.compareAndSet(previous, session)) continue
            if (session.isFrameCurrent(frame)) {
                if (hidden.value) {
                    session.inbox.invalidateReady()
                    session.sensitive.background()
                }
                return session.isFrameCurrent(frame)
            }
            detach(session)
            return false
        }
    }
    internal fun detach(session: LiveboardSession) { attached.compareAndSet(session, null) }
    internal fun beforeError(message: String): ErrorEvent {
        val attachment = attached.value
        val session = attachment?.takeIf { it.ownsNativeScope() }
        val event = ErrorEvent(attachment, session)
        if (session == null) return event
        session.inbox.invalidateReady()
        session.sensitive.conceal(false)
        if (attached.value === session && session.ownsNativeScope()) session.inbox.onError(message)
        return event
    }
    /** A command reports only into its original keyed owner, never the generic successor notification. */
    internal fun commandError(expected: LiveboardSession, message: String): Boolean {
        if (attached.value !== expected || !expected.ownsNativeScope()) return false
        expected.inbox.invalidateReady()
        expected.sensitive.conceal(false)
        if (attached.value !== expected || !expected.ownsNativeScope()) return false
        expected.inbox.onError(message)
        return true
    }
    internal fun mayDisplay(event: ErrorEvent): Boolean = attached.value === event.attachment &&
        (event.session == null || event.session.ownsNativeScope())
    internal fun beforeBackground(scope: NativeReadScope?) {
        hidden.value = true
        val session = attached.value ?: return
        if (session.scope == scope && session.ownsNativeScope()) {
            session.inbox.invalidateReady()
            session.sensitive.background()
            if (attached.value === session) session.inbox.publishReadiness()
        }
    }
    internal fun beforeForeground(scope: NativeReadScope?) {
        hidden.value = false
        val session = attached.value ?: return
        if (session.scope == scope && session.ownsNativeScope()) {
            session.inbox.invalidateReady()
            session.sensitive.foreground()
            if (attached.value === session) session.inbox.publishReadiness()
        }
    }
    internal fun beforeKey(owner: NativeOwnerToken) {
        val session = attached.value ?: return
        if (session.scope.owner === owner && session.ownsNativeScope()) {
            session.inbox.invalidateReady()
            session.sensitive.conceal(true)
            if (attached.value === session) session.inbox.publishReadiness()
        }
    }
    internal fun beforeCanonical(frame: NativeReadFrame) {
        val session = attached.value ?: return
        if (session.scope != frame.scope || !session.ownsNativeScope()) return
        session.inbox.prepareCanonical(frame)
        if (attached.value !== session || !session.ownsNativeScope()) return
        if (frame.outcome == NativeReadOutcome.FAILED) {
            session.inbox.invalidateReady()
            session.sensitive.conceal(false)
            if (attached.value === session && session.ownsNativeScope()) session.inbox.onReadFailure()
            return
        }
        if (frame.outcome != NativeReadOutcome.SUCCESS || frame.state?.success != true) return
        if (frame.state.getVaultFullInfo() !is VaultFullInfo.Member) {
            session.inbox.invalidateReady()
            session.sensitive.conceal(true)
            if (attached.value === session) session.inbox.publishReadiness()
            return
        }
        val snapshot = LiveboardStateDecoder.decode(frame.state)
        // Unsupported metadata is a soft unavailable projection, never canonical membership loss.
        if (snapshot == null || !session.matches(session.scope, snapshot.identity)) {
            session.inbox.invalidateReady()
            session.sensitive.conceal(snapshot != null)
            if (attached.value === session) session.inbox.publishReadiness()
        } else session.sensitive.observeMember(snapshot, frame.attempt)
    }
}
