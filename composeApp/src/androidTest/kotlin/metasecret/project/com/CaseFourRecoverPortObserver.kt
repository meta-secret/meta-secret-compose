package metasecret.project.com

import core.metaSecretCore.MetaSecretCoreInterface

/** Test-only observation of the same real consumer port; never a Claim/commit receipt. */
internal class CaseFourRecoverPortObserver(
    private val original: MetaSecretCoreInterface,
    private val sink: (String) -> Unit,
) : MetaSecretCoreInterface by original {
    private var started = false
    private var closed = false
    private var healthy = true
    private var sequence = 0
    private val pending = mutableSetOf<Int>()

    @Synchronized
    fun ready() {
        if (started || closed) return
        started = true
        emit("READY", 0)
    }

    override fun recoverChecked(secretId: String): String {
        val ticket = try { enter() } catch (_: Throwable) { markUnknown(); null }
        return try {
            val raw = original.recoverChecked(secretId)
            safeFinish(ticket, "RETURN")
            raw
        } catch (error: Throwable) {
            safeFinish(ticket, "THROW")
            throw error
        }
    }

    @Synchronized
    private fun enter(): Int? {
        if (!started || closed) return null
        if (sequence >= MAX_CALLS) { healthy = false; return null }
        val ticket = ++sequence
        pending.add(ticket)
        emit("ENTER", ticket)
        return ticket
    }

    private fun safeFinish(ticket: Int?, stage: String) {
        try { finish(ticket, stage) } catch (_: Throwable) { markUnknown() }
    }

    @Synchronized
    private fun markUnknown() { healthy = false }

    @Synchronized
    private fun finish(ticket: Int?, stage: String) {
        if (ticket == null) return
        if (!pending.remove(ticket)) healthy = false
        if (!closed) emit(stage, ticket)
    }

    @Synchronized
    fun close() {
        if (!started || closed) return
        if (pending.isNotEmpty()) healthy = false
        closed = true
        emit("CLOSE", sequence)
    }

    private fun emit(stage: String, ticket: Int) {
        val coverage = if (healthy) "CONTINUOUS" else "UNKNOWN"
        try {
            sink("E2E_RECOVER_PORT_V1 $stage SEQ=$ticket PENDING=${pending.size} COVERAGE=$coverage")
        } catch (_: Throwable) { healthy = false }
    }

    private companion object { const val MAX_CALLS = 6 }
}
