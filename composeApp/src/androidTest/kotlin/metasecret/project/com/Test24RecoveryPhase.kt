package metasecret.project.com

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

internal data class Test24PhaseEntry(
    val raw: String?, val required: Boolean, val selectedProfile: String?, val expected: String?,
)

// TEST-only canonical codec. This is a closed wire grammar, not permissive JSON.
internal class Test24RecoveryPhaseContext private constructor(private val fields: List<String>, private val selectedProfile: String) {
    val runId get() = fields[0]
    val cycle get() = fields[3].toInt()
    val contextId get() = fields[4]
    val invocationId get() = fields[5]
    val platform get() = fields[6]
    val role get() = fields[7]

    fun requireCurrent(expectedRole: String, expectedCycle: String, step: String) {
        check(platform == "android" && role == expectedRole && cycle.toString() == expectedCycle && step == "1") {
            "Recovery phase invocation mismatch"
        }
    }

    fun marker(phase: String): String {
        check(phase in listOf("READY", "REQUEST_SENT", "INCOMING_VISIBLE"))
        // Existing marker() adds the E2E prefix once; do not duplicate it.
        return "RECOVERY_PHASE_V1 context=$contextId invocation=$invocationId platform=$platform phase=$phase"
    }

    companion object {
        private const val PROFILE24 = "test24:ios-multi-secret-lifecycle"
        private const val PROFILE25 = "test25:web-secondary-creator-lifecycle"
        private val grammar = Regex("""\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":24,"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":(1[3-9]|2[0-9]|3[0-9]),"logicalStep":1,"mode":"ios-multi-secret-lifecycle","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z""")
        private val grammar25 = Regex("""\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":25,"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":(1[3-9]|2[0-9]|3[0-9]),"logicalStep":1,"mode":"web-secondary-creator-lifecycle","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z""")
        fun readEntry(entry: Test24PhaseEntry): Test24RecoveryPhaseContext? {
            if (!entry.required) {
                check(entry.selectedProfile == null && entry.expected == null) { "Unexpected recovery phase launch metadata" }
                return read(entry.raw, false)
            }
            check(TestRecoveryProfile.selected(entry.selectedProfile) != null) { "Invalid selected recovery profile" }
            if (entry.selectedProfile == PROFILE24) check(entry.expected == null) { "Unexpected literal24 expected scope" }
            else check(!entry.expected.isNullOrEmpty()) { "Missing selected25 expected scope" }
            val context = checkNotNull(read(entry.raw, true))
            check(context.selectedProfile == entry.selectedProfile) { "Recovery phase selected profile mismatch" }
            if (entry.expected != null) {
                val expected = checkNotNull(read(entry.expected, true))
                check(expected.selectedProfile == entry.selectedProfile && entry.raw == entry.expected) { "Recovery phase full scope mismatch" }
            }
            return context
        }
        fun read(raw: String?, required: Boolean): Test24RecoveryPhaseContext? {
            if (!required) {
                check(raw.isNullOrEmpty()) { "Unexpected recovery phase context" }
                return null
            }
            check(raw != null && raw.length <= 2048 && raw.all { it.code in 0..127 }) { "Invalid recovery phase context" }
            val literal24 = grammar.matchEntire(raw)
            val literal25 = grammar25.matchEntire(raw)
            val future = if (literal24 == null && literal25 == null) TestRecoveryProfile.readFuture(raw) else null
            val fields = checkNotNull(literal24?.groupValues?.drop(1) ?: literal25?.groupValues?.drop(1) ?: future?.first) {
                "Invalid canonical recovery phase context"
            }
            check(fields[4] != fields[5]) { "Recovery phase identities overlap" }
            val selected = if (literal24 != null) PROFILE24 else if (literal25 != null) PROFILE25 else checkNotNull(future).second
            return Test24RecoveryPhaseContext(fields, selected)
        }
    }
}

internal data class Test24PhaseReply(val state: String, val deadlineMs: Long, val badgeMs: Long) {
    companion object {
        private val grammar = Regex("""\A\{"version":1,"contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","view":"(dispatch|request-sent)","state":"(WAITING|DISPATCH_PERMITTED|REQUEST_SENT)","deadlineRemainingMs":([1-9][0-9]{0,5}),"badgeRemainingMs":(0|[1-9][0-9]{0,5})\}\z""")
        fun read(bytes: ByteArray, context: Test24RecoveryPhaseContext, view: String): Test24PhaseReply {
            check(view == "dispatch" && context.role == "sender" || view == "request-sent" && context.role == "receiver")
            check(bytes.size <= 2048 && bytes.all { it.toInt() in 0..127 }) { "Invalid recovery phase bytes" }
            val fields = checkNotNull(grammar.matchEntire(bytes.toString(Charsets.US_ASCII))) {
                "Invalid canonical recovery phase response"
            }.groupValues
            check(fields[1] == context.contextId && fields[2] == context.invocationId && fields[3] == view) {
                "Recovery phase response mismatch"
            }
            val result = Test24PhaseReply(fields[4], fields[5].toLong(), fields[6].toLong())
            check(result.deadlineMs in 1..600_000 && result.badgeMs in 0..180_000)
            check(if (result.state == "REQUEST_SENT") view == "request-sent" && result.badgeMs in 1..result.deadlineMs
                else result.badgeMs == 0L && (result.state == "WAITING" || view == "dispatch"))
            return result
        }
    }
}

// Real JDK transport; injected opener in host tests still returns a genuine
// HttpURLConnection. A late worker has no permission callback or success port.
internal class Test24PhaseHttp(private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    fun fetch(url: URL, budgetMs: Int): ByteArray {
        check(budgetMs in 1..1000)
        val terminal = AtomicBoolean(false)
        val connection = AtomicReference<HttpURLConnection?>()
        val started = System.nanoTime()
        val executor = Executors.newSingleThreadExecutor { work -> Thread(work, "Test24PhaseHttp").apply { isDaemon = true } }
        val future = executor.submit(Callable {
            check(!terminal.get())
            val current = open(url)
            connection.set(current)
            try {
                check(!terminal.get())
                current.connectTimeout = budgetMs
                current.readTimeout = budgetMs
                current.instanceFollowRedirects = false
                current.useCaches = false
                current.setRequestProperty("Accept", "application/json")
                check(current.responseCode == 200) { "Recovery phase HTTP refusal" }
                check(current.contentType?.lowercase() == "application/json") { "Recovery phase content type refusal" }
                check(current.contentEncoding == null || current.contentEncoding == "identity")
                val expectedLength = current.contentLengthLong
                check(expectedLength <= 2048)
                current.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(512)
                    while (true) {
                        check(!terminal.get())
                        val count = input.read(buffer, 0, minOf(buffer.size, 2049 - output.size()))
                        if (count < 0) break
                        check(output.size() + count <= 2048) { "Recovery phase body exceeds bound" }
                        output.write(buffer, 0, count)
                    }
                    check(expectedLength < 0 || output.size().toLong() == expectedLength) { "Recovery phase body truncated" }
                    output.toByteArray()
                }
            } finally { current.disconnect() }
        })
        try {
            val bytes = future.get(budgetMs.toLong(), TimeUnit.MILLISECONDS)
            check(System.nanoTime() - started < budgetMs.toLong() * 1_000_000) { "Recovery phase call expired" }
            return bytes
        } finally {
            terminal.set(true)
            future.cancel(true)
            connection.get()?.disconnect()
            executor.shutdownNow()
        }
    }
}

internal class Test24RecoveryPhaseClient(
    val context: Test24RecoveryPhaseContext,
    private val base: String,
    private val clock: () -> Double = { System.nanoTime() / 1_000_000.0 },
    private val exchange: (URL, Int) -> ByteArray = Test24PhaseHttp()::fetch,
    private val pause: (Long) -> Unit = Thread::sleep,
) {
    private var terminal = false
    private var localDeadline = Double.POSITIVE_INFINITY
    private var dispatched = false
    private var lastClock = Double.NEGATIVE_INFINITY

    init { check(base == "http://10.0.2.2:5180") { "Invalid recovery phase coordinator" } }

    fun awaitDispatch() {
        check(context.role == "sender" && !dispatched)
        wait("dispatch", 240_000)
        dispatched = true
    }

    fun awaitRequestSent(): Long {
        check(context.role == "receiver")
        return wait("request-sent", 600_000)
    }

    private fun wait(view: String, ceilingMs: Long): Long {
        check(!terminal)
        localDeadline = minOf(localDeadline, now() + ceilingMs)
        try {
            while (true) {
                check(!Thread.currentThread().isInterrupted) { "Recovery phase caller interrupted" }
                val start = now()
                check(start.isFinite() && localDeadline - start > 0)
                val budget = minOf(1000.0, floor(localDeadline - start)).toInt()
                check(budget > 0)
                val url = URL("$base/recovery-phase?v=1&context=${context.contextId}&participant=${context.invocationId}&view=$view")
                val bytes = exchange(url, budget)
                check(!Thread.currentThread().isInterrupted) { "Recovery phase caller interrupted" }
                val response = Test24PhaseReply.read(bytes, context, view)
                val end = now()
                check(end.isFinite() && end >= start)
                val rtt = end - start
                localDeadline = minOf(localDeadline, end + response.deadlineMs - rtt)
                val remaining = floor(localDeadline - end).toLong()
                check(remaining > 0)
                check(!Thread.currentThread().isInterrupted) { "Recovery phase caller interrupted" }
                if (response.state == "DISPATCH_PERMITTED") return remaining
                if (response.state == "REQUEST_SENT") {
                    val badge = floor(minOf(remaining.toDouble(), response.badgeMs - rtt)).toLong()
                    check(badge > 0)
                    terminal = true
                    return badge
                }
                pause(minOf(200, remaining))
            }
        } catch (error: Throwable) {
            terminal = true
            throw error
        }
    }
    private fun now(): Double {
        val value = clock()
        check(value.isFinite() && value >= lastClock) { "Invalid recovery phase clock" }
        lastClock = value
        return value
    }
}
