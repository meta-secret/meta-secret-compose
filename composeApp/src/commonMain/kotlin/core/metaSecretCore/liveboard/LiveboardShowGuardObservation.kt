package core.metaSecretCore.liveboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_GUARD_TICKETS = 32
private const val MAX_GUARD_RECORD_BYTES = 512

internal enum class ShowGuardPoint { SELECT, BIND, CAPTURE, STAGE, RESTORE, RETIRE, CONTROL }
internal enum class ShowGuardResult { TRUE, FALSE, CAPPED }
internal enum class ShowGuardFlag { TRUE, FALSE, UNOBSERVED }
internal enum class ShowGuardCause {
    NONE, ACCEPTED_ABSENT, DONE_REQUIRED, RECEIPT_ABSENT, RECEIPT_MISMATCH, BIND_NOT_WON, CAPPED,
}
internal data class ShowGuardValues(
    val accepted: ShowGuardFlag = ShowGuardFlag.UNOBSERVED,
    val receipt: ShowGuardFlag = ShowGuardFlag.UNOBSERVED,
    val match: ShowGuardFlag = ShowGuardFlag.UNOBSERVED,
    val bound: ShowGuardFlag = ShowGuardFlag.UNOBSERVED,
)
internal data class ShowGuardFacts(
    val point: ShowGuardPoint,
    val result: ShowGuardResult,
    val cause: ShowGuardCause = ShowGuardCause.NONE,
    val values: ShowGuardValues = ShowGuardValues(),
)

/** Closed implemented wire shapes only; these facts never decide a business guard. */
private val supportedGuardFacts = setOf(
    ShowGuardFacts(ShowGuardPoint.SELECT, ShowGuardResult.TRUE,
        values = ShowGuardValues(accepted = ShowGuardFlag.TRUE)),
    ShowGuardFacts(ShowGuardPoint.SELECT, ShowGuardResult.FALSE,
        values = ShowGuardValues(accepted = ShowGuardFlag.FALSE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.ACCEPTED_ABSENT,
        ShowGuardValues(accepted = ShowGuardFlag.FALSE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.DONE_REQUIRED,
        ShowGuardValues(accepted = ShowGuardFlag.TRUE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.RECEIPT_ABSENT,
        ShowGuardValues(ShowGuardFlag.TRUE, ShowGuardFlag.FALSE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.RECEIPT_MISMATCH,
        ShowGuardValues(ShowGuardFlag.TRUE, ShowGuardFlag.TRUE, ShowGuardFlag.FALSE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.FALSE, ShowGuardCause.BIND_NOT_WON,
        ShowGuardValues(ShowGuardFlag.TRUE, ShowGuardFlag.TRUE, ShowGuardFlag.TRUE)),
    ShowGuardFacts(ShowGuardPoint.BIND, ShowGuardResult.TRUE,
        values = ShowGuardValues(ShowGuardFlag.TRUE, ShowGuardFlag.TRUE, ShowGuardFlag.TRUE, ShowGuardFlag.TRUE)),
    ShowGuardFacts(ShowGuardPoint.CAPTURE, ShowGuardResult.TRUE),
    ShowGuardFacts(ShowGuardPoint.STAGE, ShowGuardResult.TRUE),
    ShowGuardFacts(ShowGuardPoint.RESTORE, ShowGuardResult.TRUE),
    ShowGuardFacts(ShowGuardPoint.CONTROL, ShowGuardResult.CAPPED, ShowGuardCause.CAPPED),
)

/** Metadata wake only; no Root, platform, business object, callback or coroutine scope. */
internal class ShowGuardWake {
    val changes = MutableStateFlow(Any())
    fun signal() { runCatching { changes.value = Any() } }
}

/** First Boolean result per point; repetition has a separate coverage sentinel, never a false guard. */
internal class LiveboardShowGuardObserver(private val wake: ShowGuardWake?) {
    private data class State(val facts: List<ShowGuardFacts> = emptyList(), val emitted: Int = 0,
        val closed: Boolean = false)
    private val state = MutableStateFlow(State())

    fun note(fact: ShowGuardFacts) {
        runCatching {
            if (fact !in supportedGuardFacts || fact.point == ShowGuardPoint.CONTROL) return@runCatching
            while (true) {
                val old = state.value
                if (old.closed) return@runCatching
                val duplicate = old.facts.any { it.point == fact.point && it.result == fact.result }
                val next = if (duplicate) {
                    if (old.facts.any { it.point == ShowGuardPoint.CONTROL }) return@runCatching
                    old.copy(facts = old.facts + ShowGuardFacts(ShowGuardPoint.CONTROL, ShowGuardResult.CAPPED,
                        ShowGuardCause.CAPPED))
                } else old.copy(facts = old.facts + fact)
                if (state.compareAndSet(old, next)) { wake?.signal(); return@runCatching }
            }
        }
    }

    fun drain(): List<ShowGuardFacts> = runCatching {
        var result = emptyList<ShowGuardFacts>()
        var draining = true
        while (draining) {
            val old = state.value
            if (old.closed || old.emitted == old.facts.size) draining = false
            else if (state.compareAndSet(old, old.copy(emitted = old.facts.size))) {
                result = old.facts.drop(old.emitted)
                draining = false
            }
        }
        result
    }.getOrDefault(emptyList())

    fun close() {
        while (true) {
            val old = state.value
            if (old.closed || state.compareAndSet(old, State(closed = true))) return
        }
    }
}

internal fun LiveboardShowObservationTicket?.guard(fact: ShowGuardFacts?) {
    if (fact != null) this?.guards?.note(fact)
}
internal fun List<LiveboardShowObservationTicket>?.guardAll(point: ShowGuardPoint) {
    this?.forEach { it.guard(ShowGuardFacts(point, ShowGuardResult.TRUE)) }
}
internal fun List<LiveboardShowObservationTicket>.guardWhenPublished(published: Boolean, point: ShowGuardPoint) {
    if (published) guardAll(point)
}
/** The original current() result is passed once; only true reads the original cached ticket. */
internal fun LiveboardCardContinuity.cacheGuardWhenCurrent(current: Boolean,
    cache: MutableList<LiveboardShowObservationTicket>): Boolean {
    if (current) observation.cacheGuardTicket(cache)
    return current
}
/** Inert bounded local cache only: safe inside an original retry evaluation, no observer mutation or wake. */
internal fun LiveboardShowObservationTicket?.cacheGuardTicket(cache: MutableList<LiveboardShowObservationTicket>) {
    if (this != null) runCatching { if (cache.size < MAX_GUARD_TICKETS) cache.add(this) }
}
internal fun Boolean.guardFlag(): ShowGuardFlag = if (this) ShowGuardFlag.TRUE else ShowGuardFlag.FALSE

internal fun encodedShowGuard(attempt: Int, fact: ShowGuardFacts): String? {
    if (attempt !in 1..MAX_GUARD_TICKETS || fact !in supportedGuardFacts) return null
    val payload = buildJsonObject {
        put("VERSION", 1)
        put("KIND", "SHOW_CONTINUITY")
        put("ATTEMPT", attempt)
        put("POINT", fact.point.name)
        put("RESULT", fact.result.name)
        put("CAUSE", fact.cause.name)
        put("A", fact.values.accepted.name)
        put("R", fact.values.receipt.name)
        put("MATCH", fact.values.match.name)
        put("BOUND", fact.values.bound.name)
        put("COVERAGE", "FIRST_FIXED_GUARD_OBSERVATIONS_ONLY")
    }
    val line = "LIVEBOARD_SHOW_CONTINUITY $payload"
    return line.takeIf { ("E2E_DIAG " + it + "\n").encodeToByteArray().size <= MAX_GUARD_RECORD_BYTES }
}
