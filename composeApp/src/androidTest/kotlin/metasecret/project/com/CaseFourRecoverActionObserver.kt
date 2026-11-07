package metasecret.project.com

import ui.liveboard.LiveboardRecoverObservationRecord
import ui.liveboard.RecoverObservationFlag
import ui.liveboard.RecoverObservationStage
import ui.liveboard.RecoverObservationCompletion
import ui.liveboard.encodedRecoverObservation

/** Test-owned primitive sink; normal application platforms never instantiate this type. */
internal class CaseFourRecoverActionObserver(private val log: (String) -> Unit) {
    private var opened = false
    private var closed = false
    private var continuous = true
    private var ownerClosed = false
    private var count = 0
    private val stages = mutableSetOf<Pair<Int, RecoverObservationStage>>()
    private val complete = mutableSetOf<Int>()
    private val bodies = mutableSetOf<Int>()
    private val intents = mutableSetOf<Int>()
    @Synchronized fun emit(record: LiveboardRecoverObservationRecord) {
        if (closed || ownerClosed) { continuous = false; return }
        if (!opened) { opened = true; write("E2E: RECOVER_ACTION READY V1\n") }
        val key = record.sequence to record.stage
        if (record.sequence !in 1..6 || !stages.add(key)) { continuous = false; return }
        if (record.facts.bodyReached == RecoverObservationFlag.ONE) bodies.add(record.sequence)
        if (record.facts.intentPresent == RecoverObservationFlag.ONE) intents.add(record.sequence)
        val line = runCatching { record.encodedRecoverObservation() }.getOrNull()
        if (line == null) { continuous = false; return }
        if (record.stage == RecoverObservationStage.SAME_JOB_COMPLETION &&
            record.completion == RecoverObservationCompletion.COMPLETED) complete.add(record.sequence)
        write(line)
    }
    @Synchronized fun ownerClosed(healthy: Boolean) {
        if (closed || ownerClosed) { continuous = false; return }
        ownerClosed = true
        if (!healthy) continuous = false
    }
    @Synchronized fun close() {
        if (closed) return
        closed = true
        val registered = stages.filter { it.second == RecoverObservationStage.ROOT_CALLBACK }.map { it.first }.toSet()
        val postRegistered = stages.filter { it.second == RecoverObservationStage.POST_REGISTRATION }.map { it.first }.toSet()
        val returned = stages.filter { it.second == RecoverObservationStage.RETURNED }.map { it.first }.toSet()
        val status = if (opened && ownerClosed && continuous && registered == complete && registered.size == 6 &&
            registered == postRegistered && returned.containsAll(bodies.intersect(intents))) "CONTINUOUS" else "UNKNOWN"
        write("E2E: RECOVER_ACTION CLOSE V1 COVERAGE=$status\n")
    }
    private fun write(line: String) {
        if (count >= 26 || line.encodeToByteArray().size > 256) { continuous = false; return }
        count += 1
        runCatching { log(line) }.onFailure { continuous = false }
    }
}
