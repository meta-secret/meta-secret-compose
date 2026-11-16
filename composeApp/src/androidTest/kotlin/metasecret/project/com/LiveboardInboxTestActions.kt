package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule

import androidx.compose.ui.test.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.os.SystemClock
import core.AppStateCacheProviderInterface
import core.metaSecretCore.liveboard.NativeReadOutcome
import models.apiModels.AppStateModel
import models.apiModels.VaultFullInfo
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.liveboard.LiveboardOperation
import models.apiModels.liveboard.LiveboardStateDecoder
import org.koin.core.context.GlobalContext

internal data class LiveboardTestIncoming(
    val kind: String,
    val requestId: String,
    val requesterName: String,
    val subject: String?,
    val count: Int,
)

internal fun ComposeTestRule.lbIncomingCount(): Int = lbProjectInbox(lbCaptureInbox()).count

internal fun ComposeTestRule.lbVisibleIncoming(): LiveboardTestIncoming? = lbProjectInbox(lbCaptureInbox()).incoming

internal fun ComposeTestRule.lbObserveIncoming(kind: String, subject: String? = null, requester: String? = null): LiveboardTestIncoming {
    lbHome()
    var found: LiveboardTestIncoming? = null
    waitUntil(180_000) {
        found = lbVisibleIncoming()
        found?.let { it.kind == kind && (subject == null || it.subject == subject) && (requester == null || it.requesterName == requester) } == true
    }
    val head = checkNotNull(found)
    lbBindCurrentIncoming(head)
    check(lbVisibleIncoming() == head) { "Rendered request changed during canonical correlation" }
    lbScope().incoming = head
    return head
}

/** The operation and label are derived from one identical canonical payload, never separate snapshots. */
internal fun lbBindIncoming(head: LiveboardTestIncoming, source: AppStateModel): LiveboardOperation {
    val snapshot = checkNotNull(LiveboardStateDecoder.decode(source)) { "Canonical request metadata is invalid" }
    val matches = snapshot.operations.filter { it.identity.requestId == head.requestId && it.identity.kind.name == head.kind }
    check(matches.size == 1) { "Rendered request is missing, duplicated or stale in this canonical source" }
    val operation = matches.single()
    val identity = operation.identity
    check(identity.requesterId != snapshot.identity.currentDeviceId && operation.status.outcome == null &&
        snapshot.identity.currentDeviceId in operation.permissions.eligiblePeerIds &&
        operation.permissions.canApprove && operation.permissions.canDecline)
    val member = (source.getVaultFullInfo() as? VaultFullInfo.Member)?.member
        ?: error("Current canonical Member is required")
    val requesters = member.member.vault.users.entries.filter { (_, user) ->
        user.member?.userData?.device?.deviceId == identity.requesterId ||
            user.outsider?.userData?.device?.deviceId == identity.requesterId
    }
    check(requesters.size == 1) { "Canonical requester mapping is missing or duplicated" }
    val entry = requesters.single()
    check(entry.key == identity.requesterId)
    val user = if (head.kind == "Join") {
        check(entry.value.member == null && entry.value.outsider?.status == UserDataOutsiderStatus.PENDING)
        checkNotNull(entry.value.outsider).userData
    } else {
        check(entry.value.outsider == null)
        checkNotNull(entry.value.member).userData
    }
    check(user.vaultName == snapshot.identity.vaultName && user.device.deviceId == identity.requesterId &&
        user.device.deviceName.isNotBlank() && user.device.deviceName == head.requesterName) {
        "Rendered requester differs from the same canonical requester identity"
    }
    val subject = identity.secretId?.let { id -> snapshot.secrets.single { it.identity.passId == id }.identity.name }
        ?: identity.targetDeviceId?.let { id -> snapshot.devices.single { it.deviceId == id }.name }
    check(head.subject == subject) { "Rendered subject differs from the exact canonical operation" }
    return operation
}

/** Read-only current-frame guard; advancing/revoked/failed or detached cache proof cannot be borrowed. */
internal fun lbBindCurrentIncoming(head: LiveboardTestIncoming): LiveboardOperation {
    val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
    val frame = checkNotNull(cache.canonicalFrames.value) { "A canonical frame is required" }
    val source = checkNotNull(frame.state)
    check(frame.outcome == NativeReadOutcome.SUCCESS && frame.scope != null && source === cache.appState.value)
    val operation = lbBindIncoming(head, source)
    check(cache.canonicalFrames.value === frame && cache.appState.value === source) { "Canonical source advanced during correlation" }
    return operation
}

internal fun ComposeTestRule.lbIncoming(kind: String, subject: String? = null, requester: String? = null): LiveboardTestIncoming {
    val head = lbObserveIncoming(kind, subject, requester)
    lbTap("lb-inbox")
    lbWait("lb-approve-${head.requestId}")
    check(lbVisibleIncoming() == head) { "Current request changed before interaction" }
    return head
}

internal fun ComposeTestRule.lbDecide(head: LiveboardTestIncoming, accepted: Boolean) {
    check(lbVisibleIncoming() == head) { "Only the exact currently visible request may be decided" }
    lbTap("lb-${if (accepted) "approve" else "decline"}-${head.requestId}")
    lbApproveSystemIfPresent()
    lbIncomingTerminal(head)
}

/** Leave the board composition without a local decision or entry authentication. */
internal fun ComposeTestRule.lbDismissIncomingObserver(head: LiveboardTestIncoming, deadlineNanos: Long) {
    fun remaining(): Long = ((deadlineNanos - System.nanoTime()) / 1_000_000).also {
        check(it > 0) { "Observer dismissal deadline expired" }
    }

    fun requireCurrentHome() {
        remaining()
        check(lbExactlyOne("lb-home") && lbExactlyOne("lb-inbox") && lbAbsent("lb-profile")) {
            "Observer requires the current Home and Inbox"
        }
        val inbox = lbProjectInbox(lbCaptureInbox())
        check(!inbox.pinnedError && inbox.incoming == head && inbox.count == head.count) {
            "Observer pending head changed"
        }
        lbBindCurrentIncoming(head)
        remaining()
    }

    fun profileIsRendered(): Boolean =
        lbExactlyOne("lb-profile") && lbAbsent("lb-home") && lbAbsent("lb-inbox")

    fun requireCurrentProfile() {
        remaining()
        check(profileIsRendered()) { "Profile must replace Home and Inbox before Back" }
        lbBindCurrentIncoming(head)
        remaining()
    }

    fun clickRouteControl(tag: String, route: String, requireRoute: () -> Unit) {
        remaining()
        check(lbExactlyOne(tag)) { "Exact observer route control missing" }
        val matcher = hasTestTag(tag) and hasAnyAncestor(hasTestTag(route))
        val id = onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().id
        val control = onNode(matcher and SemanticsMatcher("same observer route control") { it.id == id },
            useUnmergedTree = true)
        requireRoute()
        if (!control.isDisplayed()) {
            remaining()
            control.performScrollTo()
            remaining()
        }
        requireRoute()
        check(lbExactlyOne(tag)) { "Observer route control changed after readiness" }
        control.assertIsDisplayed().assertIsEnabled()
        remaining()
        control.performClick()
        remaining()
    }

    clickRouteControl("lb-open-profile", "lb-home", ::requireCurrentHome)
    waitUntil(remaining()) { profileIsRendered() }
    requireCurrentProfile()
    clickRouteControl("lb-profile-back", "lb-profile", ::requireCurrentProfile)
    waitUntil(remaining()) {
        lbExactlyOne("lb-home") && lbExactlyOne("lb-inbox") && lbAbsent("lb-profile")
    }
    requireCurrentHome()
    check(lbAbsent("lb-approve-${head.requestId}") && lbAbsent("lb-decline-${head.requestId}")) {
        "Observer decision controls remain expanded"
    }
    remaining()
    lbScope().previewDevice = null
}

/** Only this race actor may finish opening as terminal; generic lbTap stays strict. */
internal fun ComposeTestRule.lbOpenIncomingOrTerminal(head: LiveboardTestIncoming): Boolean {
    val deadline = System.nanoTime() + 180_000_000_000L
    fun remaining(): Long = ((deadline - System.nanoTime()) / 1_000_000).also {
        check(it > 0) { "Current request opening deadline expired" }
    }
    fun observe(): LiveboardInboxProjection {
        remaining()
        val current = lbProjectInbox(lbCaptureInbox())
        remaining()
        check(!current.pinnedError) { "Pinned notification error cannot be terminal" }
        val now = current.incoming
        if (now != null && now.requestId == head.requestId) {
            check(now == head) { "Current request metadata changed" }
        } else if (now != null) {
            check(current.count == head.count - 1) { "Foreign request cannot replace current readiness" }
        }
        return current
    }
    fun terminal(): Boolean {
        lbIncomingTerminal(head, deadline)
        remaining()
        return false
    }
    lbWait("lb-inbox", remaining())
    runCatching { onNodeWithTag("lb-inbox", useUnmergedTree = true).performScrollTo() }
    remaining()
    var settled = false
    waitUntil(remaining()) {
        val current = observe()
        settled = current.incoming != head && current.count == head.count - 1
        if (settled) true else if (current.incoming != head || !lbExactlyOne("lb-inbox")) false
        else runCatching {
            onNodeWithTag("lb-inbox", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
            remaining()
            true
        }.getOrDefault(false)
    }
    remaining()
    if (settled) return terminal()
    if (observe().incoming != head) return terminal()
    check(lbExactlyOne("lb-inbox")) { "Current control disappeared after readiness" }
    val control = onNodeWithTag("lb-inbox", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    if (observe().incoming != head) return terminal()
    remaining()
    control.performClick()
    remaining()
    return true
}

internal fun ComposeTestRule.lbIncomingTerminal(head: LiveboardTestIncoming, deadlineNanos: Long? = null) {
    fun remaining(limit: Long): Long = deadlineNanos?.let {
        minOf(limit, (it - System.nanoTime()) / 1_000_000).also { value ->
            check(value > 0) { "Current request terminal deadline expired" }
        }
    } ?: limit
    waitUntil(remaining(180_000)) {
        val current = lbProjectInbox(lbCaptureInbox())
        remaining(180_000)
        if (deadlineNanos != null && current.incoming?.requestId == head.requestId) {
            check(current.incoming == head) { "Current request metadata changed" }
        }
        check(!current.pinnedError) {
            "Pinned notification error cannot be mistaken for terminal request disappearance"
        }
        current.incoming?.let { it.kind == head.kind && it.requestId == head.requestId } != true &&
            current.count == head.count - 1
    }
    remaining(180_000)
    // Existing callers preserve their completion timing; bounded opening commits only after all proof.
    if (deadlineNanos == null) lbScope().completedRequests.add(head.requestId)
    if (head.count > 1) {
        waitUntil(remaining(30_000)) {
            val current = if (deadlineNanos == null) lbVisibleIncoming() else {
                val projected = lbProjectInbox(lbCaptureInbox())
                remaining(30_000)
                check(!projected.pinnedError) {
                    "Pinned notification error cannot be mistaken for the next request"
                }
                projected.incoming
            }
            remaining(30_000)
            current?.let { it.requestId != head.requestId && it.count == head.count - 1 } == true
        }
    }
    remaining(180_000)
    if (deadlineNanos != null) lbScope().completedRequests.add(head.requestId)
    lbScope().incoming = null
}

/** Coordinator metadata only. No Core call, UI mutation or hidden queue selection. */
internal data class LiveboardFifoIdentity(val runId: String, val testNumber: Int, val blockId: String)
internal fun lbFifoIdentity(coordinator: String): LiveboardFifoIdentity {
    val connection = URL("$coordinator/scenario").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 2_000; connection.readTimeout = 2_000
        check(connection.responseCode == 200)
        val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        check(body.getInt("fifoVersion") == 1)
        return LiveboardFifoIdentity(body.getString("runId"), body.getInt("testNumber"), body.getString("blockId"))
            .also { check(it.runId.isNotBlank() && it.blockId.isNotBlank()) }
    } finally { connection.disconnect() }
}
internal fun lbFifoContext(coordinator: String, cycle: Int, expected: LiveboardFifoIdentity): JSONObject {
    val deadline = SystemClock.elapsedRealtime() + 180_000
    while (SystemClock.elapsedRealtime() < deadline) {
        val connection = URL("$coordinator/fifo?cycle=$cycle").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            when (connection.responseCode) {
                200 -> return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).also {
                    check(it.getInt("version") == 1 && it.getInt("cycle") == cycle && it.getString("runId") == expected.runId &&
                        it.getInt("testNumber") == expected.testNumber && it.getString("blockId") == expected.blockId)
                }
                425 -> Unit
                else -> error("FIFO coordinator rejected the current cycle")
            }
        } finally { connection.disconnect() }
        // Existing bounded coordinator polling; this is not a delay hiding application readiness.
        SystemClock.sleep(100)
    }
    error("FIFO current-cycle context deadline")
}

internal data class LiveboardFifoStep(val logicalStep: Int, val incoming: LiveboardTestIncoming)
internal fun ComposeTestRule.lbFifoHead(context: JSONObject, platform: String, consumed: Set<Int>): LiveboardFifoStep? {
    val head = lbVisibleIncoming() ?: return null
    check(head.kind == "Recover")
    val identities = context.getJSONObject("identities")
    val receiver = identities.getJSONObject(platform).getString("deviceId")
    val requests = context.getJSONArray("requests").let { array -> (0 until array.length()).map(array::getJSONObject) }
    val pending = requests.filter { request ->
        request.getString("requestId") !in lbScope().completedRequests &&
            request.getJSONArray("eligiblePeerIds").let { peers -> (0 until peers.length()).any { peers.getString(it) == receiver } }
    }
    // Publication proves both sends; wait for their actual UI propagation before emitting a marker.
    if (head.count < pending.size) return null
    check(head.count == pending.size) { "Visible queue count does not equal addressed outstanding requests" }
    val request = pending.singleOrNull { it.getString("requestId") == head.requestId } ?: error("Foreign or settled current head")
    check(request.getString("kind") == "recover" && request.getString("requesterId") != receiver)
    check(head.requesterName == request.getString("requesterName") && head.subject == request.getString("subject"))
    val sender = identities.keys().asSequence().map { identities.getJSONObject(it) }
        .single { it.getString("deviceId") == request.getString("requesterId") }
    check(sender.getString("deviceName") == head.requesterName)
    val approvals = context.getJSONArray("approvals")
    val assigned = (0 until approvals.length()).map(approvals::getJSONObject).filter {
        it.getInt("logicalStep") !in consumed && it.getString("approverPlatform") == platform &&
            it.getString("passId") == request.getString("passId") && it.getString("subject") == head.subject
    }
    check(assigned.isNotEmpty() && assigned.map { it.getString("action") }.distinct().size == 1)
    return LiveboardFifoStep(assigned.minOf { it.getInt("logicalStep") }, head)
}
