package ui.liveboard

import core.metaSecretCore.liveboard.LiveboardSession

private const val NAVIGATION_TRACE_LIMIT = 8
private const val NAVIGATION_ROW_LIMIT = 6
private const val NAVIGATION_WRITE_LIMIT = 3
private const val NAVIGATION_BYTE_LIMIT = 256

/** Closed marked-E2E metadata; never navigation or native authority. */
enum class NavigationObservationFlag { UNKNOWN, TRUE, FALSE }
enum class NavigationObservationStage { READY, CALLBACK, RESULT, ROUTE_WRITE, RETURN, CLOSE }
enum class NavigationObservationRoute { UNKNOWN, HOME, SECRETS, PROFILE }
data class LiveboardNavigationObservationFlags(
    val rendered: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
    val rootCurrent: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
    val sessionPresent: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
    val nativeScope: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
    val finalCurrent: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
)
data class LiveboardNavigationObservationRecord(
    val sequence: Int,
    val stage: NavigationObservationStage,
    val flags: LiveboardNavigationObservationFlags = LiveboardNavigationObservationFlags(),
    val route: NavigationObservationRoute = NavigationObservationRoute.UNKNOWN,
    val continuous: NavigationObservationFlag = NavigationObservationFlag.UNKNOWN,
)

fun LiveboardNavigationObservationRecord.encodedNavigationObservation(): String? {
    val lifecycle = stage == NavigationObservationStage.READY || stage == NavigationObservationStage.CLOSE
    val continuityAllowed = stage == NavigationObservationStage.CLOSE || continuous == NavigationObservationFlag.UNKNOWN
    if (sequence !in 0..NAVIGATION_TRACE_LIMIT || !continuityAllowed || lifecycle != (sequence == 0)) return null
    val value = "E2E_NAV_V1 Q=$sequence ST=$stage R=${flags.rendered} C=${flags.rootCurrent} " +
        "S=${flags.sessionPresent} N=${flags.nativeScope} F=${flags.finalCurrent} ROUTE=$route CONT=$continuous"
    return value.takeIf { it.encodeToByteArray().size + 1 <= NAVIGATION_BYTE_LIMIT }
}

internal fun Boolean.navigationFlag() = if (this) NavigationObservationFlag.TRUE else NavigationObservationFlag.FALSE

class LiveboardNavigationObservationTicket internal constructor(internal val sequence: Int) {
    internal var rendered = NavigationObservationFlag.UNKNOWN
    internal var root = NavigationObservationFlag.UNKNOWN
    internal var session = NavigationObservationFlag.UNKNOWN
    internal var native = NavigationObservationFlag.UNKNOWN
    internal var finalCurrent = NavigationObservationFlag.UNKNOWN
    internal val writes = mutableListOf<NavigationObservationRoute>()
    internal var deliveredWrites = 0
    internal var rows = 0
    internal fun flags() = LiveboardNavigationObservationFlags(rendered, root, session, native, finalCurrent)
}

/** Primitive buffer only during guards; platform calls occur after the original decision. */
internal class LiveboardNavigationObservation(private val platform: LiveboardPlatformPort) {
    private var healthy = true
    private val enabled = try { platform.navigationObservationEnabled } catch (_: Throwable) { healthy = false; false }
    private var sequence = 0
    private var active: LiveboardNavigationObservationTicket? = null
    private var last: LiveboardNavigationObservationTicket? = null
    private var closed = false
    private var draining = false
    private var closeSent = false

    fun ready() {
        if (enabled) drain { emit(LiveboardNavigationObservationRecord(0, NavigationObservationStage.READY)) }
    }

    fun navigate(rendered: () -> Boolean, commit: (LiveboardNavigationObservationTicket?) -> Unit) {
        val busy = closed || draining || active != null
        if (!enabled || busy || sequence == NAVIGATION_TRACE_LIMIT) {
            if (enabled) healthy = false
            if (rendered()) commit(null)
            return
        }
        val ticket = LiveboardNavigationObservationTicket(++sequence)
        active = ticket
        var completed = false
        try {
            val owns = rendered()
            ticket.rendered = owns.navigationFlag()
            if (owns) commit(ticket)
            completed = true
        } finally {
            if (!completed) healthy = false
            active = null
            last = ticket
            drain {
                row(ticket, NavigationObservationStage.CALLBACK)
                row(ticket, NavigationObservationStage.RESULT)
                flushTicket(ticket)
                row(ticket, NavigationObservationStage.RETURN)
            }
        }
    }

    // Diagnostic bookkeeping starts only after the original update returns successfully.
    fun updateRoute(route: LiveboardRootRoute, ticket: LiveboardNavigationObservationTicket?, update: () -> Unit) {
        update()
        if (draining) { healthy = false; return }
        val target = if (enabled) ticket ?: active ?: last else null
        if (target == null) return
        if (closed || target.writes.size == NAVIGATION_WRITE_LIMIT) healthy = false
        else target.writes.add(when (route) {
            LiveboardRootRoute.Home -> NavigationObservationRoute.HOME
            LiveboardRootRoute.Secrets -> NavigationObservationRoute.SECRETS
            LiveboardRootRoute.Profile -> NavigationObservationRoute.PROFILE
        })
    }

    fun flushWrites() {
        if (!enabled || active != null || draining) return
        val ticket = last ?: return
        drain { flushTicket(ticket) }
    }

    private fun flushTicket(ticket: LiveboardNavigationObservationTicket) {
        while (ticket.deliveredWrites < ticket.writes.size) {
            row(ticket, NavigationObservationStage.ROUTE_WRITE, ticket.writes[ticket.deliveredWrites++])
        }
    }

    private fun drain(rows: () -> Unit) {
        draining = true
        try { rows() } finally {
            draining = false
            if (closed) finishClose()
        }
    }

    fun close() {
        if (!enabled || closed) return
        closed = true
        if (active != null || draining) { healthy = false; return }
        finishClose()
    }

    private fun finishClose() {
        if (closeSent) return
        closeSent = true
        try { platform.closeNavigationObservation(healthy) } catch (_: Throwable) { healthy = false }
    }

    private fun row(ticket: LiveboardNavigationObservationTicket, stage: NavigationObservationStage,
                    route: NavigationObservationRoute = NavigationObservationRoute.UNKNOWN) {
        if (ticket.rows == NAVIGATION_ROW_LIMIT) { healthy = false; return }
        ticket.rows++
        emit(LiveboardNavigationObservationRecord(ticket.sequence, stage, ticket.flags(), route))
    }

    private fun emit(record: LiveboardNavigationObservationRecord) {
        try { platform.emitNavigationObservation(record) } catch (_: Throwable) { healthy = false }
    }
}

internal fun observeNavigationSession(candidate: LiveboardSession?, trace: LiveboardNavigationObservationTicket?,
    current: () -> Boolean): LiveboardSession? {
    trace?.session = (candidate != null).navigationFlag()
    return candidate?.takeIf {
        val owns = current()
        trace?.root = owns.navigationFlag()
        owns && it.ownsNativeScope().also { native -> trace?.native = native.navigationFlag() }
    }
}
