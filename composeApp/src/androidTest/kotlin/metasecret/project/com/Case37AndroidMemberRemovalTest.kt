package metasecret.project.com

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.*
import core.metaSecretCore.liveboard.LiveboardKind
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import core.AppString
import core.StringProviderAndroid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import models.apiModels.liveboard.LiveboardStateDecoder

/** Visible-UI membership-removal and local-Show hooks for Tests #37–#43. */
@RunWith(AndroidJUnit4::class)
class Case37AndroidMemberRemovalTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun assertSecretsVisibleForTest37() {
        val cycle = argument("cycle", "2")
        val secretNames = expectedSecretNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST37_SECRETS_VISIBLE_$cycle")
    }

    @Test
    fun approveIosRemovalForTest37() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #37 requires the observed registered removal target" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST37_REMOVAL_RECEIVER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST37_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-ios-approve", "1")
        composeRule.lbDecide(incoming, true)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("ios")}")

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST37_IOS_REMOVAL_APPROVED")
    }

    /** Test #15 approves removal while a Recover claim is pending. */
    @Test
    fun approveRemovalDuringRecoveryForTest15() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val cycle = argument("cycle", "1")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #15 requires the exact device name being removed" } }
        val secretNames = expectedSecretNames()
        val fifo = test15FifoContext(coordinator)
        if (fifo != null) {
            check(cycle == "1" && argument("step", "1") == "1" && targetDeviceName == fifo.devices.getValue("ios").second)
            check(secretNames == listOf(fifo.secret.second))
        }

        openSecretsAndWaitFor(secretNames)
        openDevices()
        waitForMemberBadges(expectedMembers = 3)
        val targetId = exactDeviceId(targetDeviceName, "Member")
        assertTrue("Removal target must expose a Remove action", composeRule.lbInspectRemove(composeRule.lbMember(targetDeviceName, false)))
        if (fifo == null) marker("ANDROID_TEST15_REMOVAL_APPROVER_READY")
        else test15Marker("ANDROID_TEST15_REMOVAL_APPROVER_READY", fifo, null, 0)

        if (fifo != null) {
            val expectedRecovery = waitForTest15RecoveryHead(fifo)
            val recovery = composeRule.lbIncoming("Recover", fifo.secret.second, fifo.devices.getValue("web").second)
            check(recovery == expectedRecovery) { "Test15 head changed before opening the exact Recover" }
            check(test15Head(fifo, recovery, fifo.recoveryId, 2))
            test15Marker("ANDROID_TEST15_RECOVERY_HEAD_VISIBLE_1_1", fifo, recovery.requestId, 2)
            waitForApproval(coordinator, "test15-recover-decline", cycle)
            check(test15Head(fifo, recovery, fifo.recoveryId, 2))
            composeRule.lbDecide(recovery, false)
            test15Marker("ANDROID_TEST15_RECOVERY_DECLINED_1_1", fifo, recovery.requestId, 1)
        }
        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        if (fifo == null) marker("ANDROID_TEST15_REMOVAL_REQUEST_VISIBLE")
        else {
            check(test15Head(fifo, incoming, incoming.requestId, 1))
            test15Marker("ANDROID_TEST15_REMOVAL_REQUEST_VISIBLE", fifo, incoming.requestId, 1)
        }

        waitForApproval(coordinator, "test15-remove-approve", cycle)
        if (fifo != null) check(test15Head(fifo, incoming, incoming.requestId, 1))
        composeRule.lbDecide(incoming, true)
        composeRule.waitUntil(180_000) { deviceRowCount() == 2 }
        assertDeviceRowAbsent(targetDeviceName)

        openSecretsAndWaitFor(secretNames)
        secretNames.forEach { secretName ->
            val id = composeRule.lbSecret(secretName)
            composeRule.lbValueAbsent(id)
        }
        if (fifo == null) marker("ANDROID_TEST15_REMOVAL_APPROVED_RECOVERY_CLEARED")
        else {
            check(composeRule.lbVisibleIncoming() == null && composeRule.lbIncomingCount() == 0)
            test15Marker("ANDROID_TEST15_REMOVAL_APPROVED_RECOVERY_CLEARED", fifo, incoming.requestId, 0)
        }
        waitForApproval(coordinator, "test15-cli-member-accepted", cycle)
        openDevices()
        openSecretsAndWaitFor(secretNames)
        marker("ANDROID_TEST15_CLI_STATE_REFRESHED")
        waitForApproval(coordinator, "test15-cli-redistribution-observed", cycle)
    }

    private data class Test15Scope(val runId: String, val testNumber: Int, val block: String, val cycle: Int, val step: Int)
    private data class Test15Fifo(
        val scope: Test15Scope, val vault: String, val secret: Pair<String, String>,
        val devices: Map<String, Pair<String, String>>, val recoveryId: String,
    )

    private fun test15FifoContext(coordinator: String): Test15Fifo? {
        val raw = argument("test15FifoContext", "")
        if (raw.isEmpty()) return null
        val json = JSONObject(raw)
        val scope = json.getJSONObject("scope").let {
            Test15Scope(it.getString("runId"), it.getInt("testNumber"), it.getString("block"), it.getInt("cycle"), it.getInt("step"))
        }
        val owning = lbFifoIdentity(coordinator)
        check(scope.runId == owning.runId && scope.testNumber == 15 && scope.testNumber == owning.testNumber &&
            scope.block == "scenario-block1" && scope.block == owning.blockId && scope.cycle == 1 && scope.step == 1)
        check(json.getString("sender") == "web" && json.getString("removalApprover") == "android" && json.getString("removalTarget") == "ios")
        val secret = json.getJSONObject("secret").let { it.getString("passId") to it.getString("name") }
        val approvals = json.getJSONArray("approvals")
        check(approvals.length() == 1)
        approvals.getJSONObject(0).let {
            check(it.getString("platform") == "android" && it.getString("action") == "decline" && it.getString("secret") == secret.second)
        }
        val devices = listOf("web", "android", "ios").associateWith { role ->
            json.getJSONObject("devices").getJSONObject(role).let { it.getString("id") to it.getString("name") }
        }
        val request = json.getString("recoveryId")
        check(request.isNotBlank() && secret.first.isNotBlank() && secret.second.isNotBlank() &&
            devices.values.all { it.first.isNotBlank() && it.second.isNotBlank() } && devices.values.map { it.first }.distinct().size == 3)
        val baseline = json.getJSONArray("baselineRequestIds")
        check((0 until baseline.length()).none { baseline.getString(it) == request })
        return Test15Fifo(scope, json.getString("vaultId").also { check(it.isNotBlank()) }, secret, devices, request)
    }

    private fun waitForTest15RecoveryHead(context: Test15Fifo): LiveboardTestIncoming {
        var captured: LiveboardTestIncoming? = null
        composeRule.waitUntil(180_000) {
            val head = composeRule.lbVisibleIncoming() ?: return@waitUntil false
            check(head.kind == "Recover" && head.requestId == context.recoveryId &&
                head.requesterName == context.devices.getValue("web").second && head.subject == context.secret.second) {
                "Test15 requires the exact original Recover as the visible FIFO head"
            }
            check(head.count in 1..2) { "Test15 visible queue count is outside the declared concurrent pair" }
            if (head.count != 2 || !test15Head(context, head, context.recoveryId, 2)) return@waitUntil false
            captured = head
            true
        }
        return checkNotNull(captured)
    }

    private fun test15Head(context: Test15Fifo, head: LiveboardTestIncoming, requestId: String, count: Int): Boolean {
        check(composeRule.lbVisibleIncoming() == head && head.requestId == requestId && head.count == count)
        val view = lbReadCanonical() ?: return false
        val operation = lbBindIncoming(head, view.source)
        val snapshot = checkNotNull(LiveboardStateDecoder.decode(view.source))
        check(snapshot.identity.vaultName == context.vault && snapshot.identity.currentDeviceId == context.devices.getValue("android").first)
        check(snapshot.devices.size == 3 && context.devices.values.all { expected ->
            snapshot.devices.singleOrNull { it.deviceId == expected.first }?.name == expected.second
        })
        check(snapshot.secrets.single { it.identity.passId == context.secret.first }.identity.name == context.secret.second)
        check(operation.identity.requesterId == context.devices.getValue("web").first &&
            context.devices.getValue("android").first in operation.permissions.eligiblePeerIds)
        if (head.kind == "Recover") check(operation.identity.secretId == context.secret.first && requestId == context.recoveryId)
        else check(head.kind == "RemoveDevice" && operation.identity.targetDeviceId == context.devices.getValue("ios").first)
        val current = lbReadCanonical(checkNotNull(view.frame.scope)) ?: return false
        return current.frame === view.frame && current.source === view.source &&
            current.frame.scope === view.frame.scope && composeRule.lbVisibleIncoming() == head
    }

    private fun test15Marker(prefix: String, context: Test15Fifo, requestId: String?, count: Int) {
        val scope = context.scope
        val fields = mutableListOf("\"runId\":" + JSONObject.quote(scope.runId), "\"testNumber\":${scope.testNumber}",
            "\"block\":" + JSONObject.quote(scope.block), "\"cycle\":${scope.cycle}", "\"step\":${scope.step}")
        if (requestId != null) fields.add("\"claimId\":" + JSONObject.quote(requestId))
        fields.add("\"count\":$count")
        marker("$prefix {${fields.joinToString(",")}}")
    }

    /** Android is Test #42's sole approver and reverse resharer for Web removal. */
    @Test
    fun approveWebRemovalForTest42() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #42 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST42_REMOVAL_RECEIVER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST42_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-web-approve-test42", "1")
        composeRule.lbDecide(incoming, true)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("web")}")

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST42_WEB_REMOVAL_APPROVED")
    }

    /** Android is Test #43's sole approver and reverse resharer for Web removal. */
    @Test
    fun approveWebRemovalForTest43() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #43 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST43_REMOVAL_RECEIVER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST43_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-web-approve-test43", "1")
        composeRule.lbDecide(incoming, true)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("web")}")

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST43_WEB_REMOVAL_APPROVED")
    }

    @Test
    fun showLocalSecretForTest37() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST37",
            fallbackSecretName = "test37-secret1",
            fallbackSecretValue = "test37-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest38() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST38",
            fallbackSecretName = "test38-secret1",
            fallbackSecretValue = "test38-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest41() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST41",
            fallbackSecretName = "test41-secret1",
            fallbackSecretValue = "test41-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest42() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST42",
            fallbackSecretName = "test42-secret1",
            fallbackSecretValue = "test42-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest43() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST43",
            fallbackSecretName = "test43-secret1",
            fallbackSecretValue = "test43-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest45() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST45",
            fallbackSecretName = "test45-secret1",
            fallbackSecretValue = "test45-secret1-value",
        )
    }

    @Test
    fun showLocalSecretForTest46() {
        showLocalSecret(
            markerPrefix = "ANDROID_TEST46",
            fallbackSecretName = "test46-secret1",
            fallbackSecretValue = "test46-secret1-value",
        )
    }

    private fun showLocalSecret(
        markerPrefix: String,
        fallbackSecretName: String,
        fallbackSecretValue: String,
    ) {
        val secretName = argument("secretName", fallbackSecretName)
        val secretValue = argument("secretValue", fallbackSecretValue)
        val cycle = argument("cycle", "1")
        val secretNames = expectedSecretNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        waitForExactPrimaryAction(secretName, "Show", 180_000)
        val revealedId = composeRule.lbShow(secretName, secretValue)
        marker("${markerPrefix}_LOCAL_SECRET_VALUE_VERIFIED_$cycle")

        composeRule.lbHide(revealedId)
        composeRule.waitForExactTextGone(secretValue, 30_000)
        waitForExactPrimaryAction(secretName, "Show", 30_000)
        assertNoStaleSecretUi(secretNames)
        marker("${markerPrefix}_LOCAL_SECRET_CLOSED_$cycle")
    }

    /** Android requests Web removal only after iOS and Web are ready. */
    @Test
    fun requestWebRemovalForTest38() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #38 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST38_WEB_REMOVAL_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-web-start", "1")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("web"),
            targetDeviceName = targetDeviceName,
        )
        // A requester intentionally does not render its own pending-removal
        // row. The iOS receiver releases this gate only after it sees the
        // persisted request and exact dialog.
        waitForApproval(coordinator, "android-web-request-observed", "1")
        marker("ANDROID_TEST38_WEB_REMOVAL_REQUESTED")

        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("web")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST38_WEB_REMOVAL_COMPLETED")
    }

    /** Android requests iOS removal only after Web and iOS are ready. */
    @Test
    fun requestIosRemovalForTest41() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #41 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST41_IOS_REMOVAL_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-ios-start-test41", "1")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        // The visible confirmation is complete and Core has accepted the
        // request locally. Web now reloads from this persisted boundary.
        marker("ANDROID_TEST41_IOS_REMOVAL_REQUESTED")

        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST41_IOS_REMOVAL_COMPLETED")
    }

    /** Android requests iOS removal in Test #45 after creating Secret3. */
    @Test
    fun requestIosRemovalForTest45() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #45 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST45_IOS_REMOVAL_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-ios-start-test45", "1")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        marker("ANDROID_TEST45_IOS_REMOVAL_REQUESTED")

        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST45_IOS_REMOVAL_COMPLETED")
    }

    /** Android requests Web removal in Test #46 after all three Secrets exist. */
    @Test
    fun requestWebRemovalForTest46() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #46 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST46_WEB_REMOVAL_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-web-start-test46", "1")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("web"),
            targetDeviceName = targetDeviceName,
        )
        marker("ANDROID_TEST46_WEB_REMOVAL_REQUESTED")

        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("lb-remove-${threeMemberIds.getValue("web")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST46_WEB_REMOVAL_COMPLETED")
    }

    /** Android B terminally declines iOS C's request to remove Web A. */
    @Test
    fun declineWebRemovalForTest47() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #47 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST47_PHASE1_DECLINER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST47_PHASE1_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test47", "1")
        composeRule.lbDecide(incoming, false)
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST47_PHASE1_REMOVAL_DECLINED")
    }

    /** Android B requests removal of iOS C and waits for Web A's Decline. */
    @Test
    fun requestIosRemovalForTest47() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #47 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST47_PHASE2_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-ios-start-test47", "2")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        marker("ANDROID_TEST47_PHASE2_REMOVAL_REQUESTED")

        waitForApproval(coordinator, "android-phase2-decline-observed-test47", "2")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST47_PHASE2_DECLINE_OBSERVED")
    }

    /** Android B remains a member after iOS C declines Web A's request. */
    @Test
    fun observeAndroidRemovalDeclinedForTest47() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        marker("ANDROID_TEST47_PHASE3_TARGET_READY")

        waitForApproval(coordinator, "android-phase3-decline-observed-test47", "3")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST47_PHASE3_DECLINE_OBSERVED")
    }

    /** Android A remains a member after iOS B declines Web C's removal request. */
    @Test
    fun observeAndroidRemovalDeclinedForTest48() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST48_PHASE1_TARGET_READY")

        waitForApproval(coordinator, "android-phase1-decline-observed-test48", "1")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST48_PHASE1_DECLINE_OBSERVED")
    }

    /** Android A terminally declines iOS B's request to remove Web C. */
    @Test
    fun declineWebRemovalForTest48() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #48 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST48_PHASE2_DECLINER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST48_PHASE2_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test48", "2")
        composeRule.lbDecide(incoming, false)
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST48_PHASE2_REMOVAL_DECLINED")
    }

    /** Android A requests removal of iOS B and waits for Web C's Decline. */
    @Test
    fun requestIosRemovalForTest48() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #48 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST48_PHASE3_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-ios-start-test48", "3")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        marker("ANDROID_TEST48_PHASE3_REMOVAL_REQUESTED")

        waitForApproval(coordinator, "android-phase3-decline-observed-test48", "3")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST48_PHASE3_DECLINE_OBSERVED")
    }

    /** Android C requests removal of iOS A and waits for Web B's Decline. */
    @Test
    fun requestIosRemovalForTest49() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #49 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST49_PHASE1_REQUESTER_READY")

        waitForApproval(coordinator, "android-remove-ios-start-test49", "1")
        revealAndConfirmRemoval(
            deviceId = threeMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        marker("ANDROID_TEST49_PHASE1_REMOVAL_REQUESTED")

        waitForApproval(coordinator, "android-phase1-decline-observed-test49", "1")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST49_PHASE1_DECLINE_OBSERVED")
    }

    /** Android C remains a member after iOS A declines Web B's removal request. */
    @Test
    fun observeAndroidRemovalDeclinedForTest49() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST49_PHASE2_TARGET_READY")

        waitForApproval(coordinator, "android-phase2-decline-observed-test49", "2")
        openDevices()
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST49_PHASE2_DECLINE_OBSERVED")
    }

    /** Android C terminally declines iOS A's request to remove Web B. */
    @Test
    fun declineWebRemovalForTest49() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #49 requires the canonical Web target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertEquals(deviceNames.web, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST49_PHASE3_DECLINER_READY")

        val incoming = composeRule.lbIncoming("RemoveDevice", targetDeviceName)
        assertEquals("Removal request must name the exact target", targetDeviceName, incoming.subject)
        marker("ANDROID_TEST49_PHASE3_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test49", "3")
        composeRule.lbDecide(incoming, false)
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST49_PHASE3_REMOVAL_DECLINED")
    }

    /** The two-to-one transition is local and has no remote approval. */
    @Test
    fun removeIosLocallyForTest38() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #38 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST38_IOS_LOCAL_REMOVAL_READY")

        waitForApproval(coordinator, "android-remove-ios-local", "2")
        revealAndConfirmRemoval(
            deviceId = twoMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        marker("ANDROID_TEST38_IOS_LOCAL_REMOVAL_REQUESTED")
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${twoMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST38_IOS_LOCAL_REMOVAL_COMPLETED")
    }

    /** Test #42's second removal is the local Android/iOS two-to-one path. */
    @Test
    fun removeIosLocallyForTest42() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #42 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST42_IOS_LOCAL_REMOVAL_READY")

        waitForApproval(coordinator, "android-remove-ios-local-test42", "2")
        revealAndConfirmRemoval(
            deviceId = twoMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        marker("ANDROID_TEST42_IOS_LOCAL_REMOVAL_REQUESTED")
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${twoMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST42_IOS_LOCAL_REMOVAL_COMPLETED")
    }

    /** Test #43's second removal is the local Android/iOS two-to-one path. */
    @Test
    fun removeIosLocallyForTest43() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #43 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST43_IOS_LOCAL_REMOVAL_READY")

        waitForApproval(coordinator, "android-remove-ios-local-test43", "2")
        revealAndConfirmRemoval(
            deviceId = twoMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        marker("ANDROID_TEST43_IOS_LOCAL_REMOVAL_REQUESTED")
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${twoMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST43_IOS_LOCAL_REMOVAL_COMPLETED")
    }

    /** Test #46's second removal is the local Android/iOS two-to-one path. */
    @Test
    fun removeIosLocallyForTest46() {
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val targetDeviceName = argument("joinTarget", "")
            .also { check(it.isNotBlank()) { "Test #46 requires the canonical iOS target name" } }
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertEquals(deviceNames.ios, targetDeviceName)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        marker("ANDROID_TEST46_IOS_LOCAL_REMOVAL_READY")

        waitForApproval(coordinator, "android-remove-ios-local-test46", "2")
        revealAndConfirmRemoval(
            deviceId = twoMemberIds.getValue("ios"),
            targetDeviceName = targetDeviceName,
        )
        waitForExactSettledDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        marker("ANDROID_TEST46_IOS_LOCAL_REMOVAL_REQUESTED")
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("lb-remove-${twoMemberIds.getValue("ios")}")
        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        marker("ANDROID_TEST46_IOS_LOCAL_REMOVAL_COMPLETED")
    }

    @Test
    fun observeOwnRemovalForTest37() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST37_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST37_REMOVED_RESET")
    }

    @Test
    fun observeOwnRemovalForTest39() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST39_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST39_REMOVED_RESET")
    }

    /** Test #40 leaves Android passive while Web requests and iOS approves its removal. */
    @Test
    fun observeOwnRemovalForTest40() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST40_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST40_REMOVED_RESET")
    }

    /** Test #44 leaves Android passive while Web requests and iOS approves its removal. */
    @Test
    fun observeOwnRemovalForTest44() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val threeMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST44_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST44_REMOVED_RESET")
    }

    /** Test #41 leaves Android passive while Web performs the local 2 -> 1 removal. */
    @Test
    fun observeOwnRemovalForTest41() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST41_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST41_REMOVED_RESET")
    }

    /** Test #45 leaves Android passive while Web performs the local 2 -> 1 removal. */
    @Test
    fun observeOwnRemovalForTest45() {
        val secretNames = expectedSecretNames()
        val deviceNames = expectedDeviceNames()

        openSecretsAndWaitFor(secretNames)
        assertNoStaleSecretUi(secretNames)
        openDevices()
        val twoMemberIds = waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertNoIncoming(LiveboardKind.RemoveDevice)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
        assertNoIncoming(LiveboardKind.Recover)
        marker("ANDROID_TEST45_REMOVAL_TARGET_READY")

        composeRule.lbWait("orbit-enter", 180_000)
        check(!composeRule.lbHas("lb-home") && !composeRule.lbHas("lb-secrets") && !composeRule.lbHas("lb-profile"))
        check(composeRule.lbTagged("lb-device-").isEmpty() && composeRule.lbTagged("lb-secret-").isEmpty())
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        check(composeRule.lbTagged("lb-inbox-head-").isEmpty() && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-remove-").isEmpty() && composeRule.lbTagged("lb-approve-").isEmpty())
        marker("ANDROID_TEST45_REMOVED_RESET")
    }

    private fun openSecretsAndWaitFor(secretNames: List<String>) {
        composeRule.lbSecrets()
        secretNames.forEach { composeRule.lbSecret(it) }
    }

    private fun openDevices() = composeRule.lbHome()

    private fun revealAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        val member = composeRule.lbMember(targetDeviceName, false)
        check(member.deviceId == deviceId) { "Removal target identity changed" }
        composeRule.lbPreviewRemoval(deviceId)
        composeRule.lbSubmitRemoval()
    }

    private fun assertNoStaleSecretUi(secretNames: List<String>) {
        val snapshot = checkNotNull(lbSnapshot())
        val ids = snapshot.secrets.filter { it.identity.name in secretNames }.map { it.identity.passId }.toSet()
        check(snapshot.operations.none { it.identity.secretId in ids && it.status.outcome == null &&
            it.identity.kind == LiveboardKind.Recover }) { "Stale canonical Recovery remains" }
        check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
        composeRule.lbHome()
        check(composeRule.lbVisibleIncoming() == null && composeRule.lbIncomingCount() == 0)
        check(composeRule.lbTagged("lb-approve-").isEmpty() && composeRule.lbTagged("lb-remove-").isEmpty())
    }

    private fun waitForMemberBadges(expectedMembers: Int) {
        composeRule.lbHome()
        composeRule.waitUntil(180_000) { composeRule.lbMembers().size == expectedMembers }
        check(composeRule.lbMembers().count { it.current } == 1)
    }

    private data class ExpectedDeviceNames(
        val web: String,
        val android: String,
        val ios: String,
    )

    private data class DeviceRole(
        val platform: String,
        val name: String,
        val status: String,
        val expectsRemoveAction: Boolean,
    )

    private fun expectedDeviceNames(): ExpectedDeviceNames {
        val names = ExpectedDeviceNames(
            web = argument("expectedWebDeviceName", ""),
            android = argument("expectedAndroidDeviceName", ""),
            ios = argument("expectedIosDeviceName", ""),
        )
        check(names.web.isNotBlank() && names.android.isNotBlank() && names.ios.isNotBlank()) {
            "Membership removal tests require exact Web, Android, and iOS device names"
        }
        return names
    }

    private fun waitForExactDeviceMembership(expectedRoles: List<DeviceRole>): Map<String, String> {
        val byName = composeRule.lbAssertMembers(expectedRoles.map { it.name to (it.status == "Current") })
        expectedRoles.forEach { check(it.expectsRemoveAction == (it.status != "Current")) }
        return expectedRoles.associate { it.platform to byName.getValue(it.name) }
    }

    private fun waitForExactSettledDeviceMembership(expectedRoles: List<DeviceRole>): Map<String, String> {
        composeRule.waitUntil(180_000) {
            val snapshot = lbSnapshot() ?: return@waitUntil false
            snapshot.operations.none { it.identity.kind == LiveboardKind.RemoveDevice && it.status.outcome == null } &&
                snapshot.devices.size == expectedRoles.size
        }
        return waitForExactDeviceMembership(expectedRoles)
    }

    private fun exactDeviceId(name: String, status: String): String =
        composeRule.lbMember(name, status == "Current").deviceId

    private fun assertDeviceRowAbsent(name: String) {
        check(composeRule.lbMembers().none { it.name == name }) { "Removed Device remained visible" }
        check(lbSnapshot()?.devices?.none { it.name == name } == true) { "Removed Device remained canonical" }
    }

    private fun deviceRowCount(): Int = composeRule.lbMembers().size

    private fun waitForExactPrimaryAction(secretName: String, expected: String, timeoutMillis: Long) {
        composeRule.lbWaitPrimary(secretName, expected, timeoutMillis)
    }

    private fun hasTag(tag: String): Boolean = composeRule.lbHas(tag)

    private fun assertTagAbsent(tag: String) {
        check(!composeRule.lbHas(tag)) { "Unexpected current UI control" }
    }

    private fun assertNoIncoming(kind: LiveboardKind) {
        check(lbSnapshot()?.operations?.none { it.identity.kind == kind && it.status.outcome == null } == true)
        check(composeRule.lbVisibleIncoming()?.kind != kind.name)
    }

    private fun expectedSecretNames(): List<String> =
        argument("expectedSecretNames", "")
            .split(',')
            .filter { it.isNotBlank() }
            .also { check(it.isNotEmpty()) { "Membership removal tests require expectedSecretNames" } }

    private fun argument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun clickIfPresent(tag: String, timeoutMillis: Long) {
        runCatching {
            composeRule.waitForTag(tag, timeoutMillis)
            composeRule.onNodeWithTag(tag).performClick()
        }
    }

    private fun waitForApproval(url: String, platform: String, cycle: String) {
        composeRule.waitUntil(240_000) {
            runCatching {
                val connection = URL("$url/approval?platform=$platform&cycle=$cycle")
                    .openConnection() as HttpURLConnection
                val result = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                result == "allowed"
            }.getOrDefault(false)
        }
    }

    private fun marker(message: String) {
        Log.i("MetaSecretE2E", "E2E: $message")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForExactText(
        text: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForExactTextGone(
        text: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
