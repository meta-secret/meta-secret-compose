package metasecret.project.com

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST37_REMOVAL_RECEIVER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Removal dialog must expose one exact localized iOS title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST37_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-ios-approve", "1")
        composeRule.onNodeWithTag("remove-device-dialog-accept", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("web", deviceNames.web, "Member", expectsRemoveAction = true),
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
            ),
        )
        assertDeviceRowAbsent(deviceNames.ios)
        assertTagAbsent("remove-device-${threeMemberIds.getValue("ios")}")

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

        openSecretsAndWaitFor(secretNames)
        openDevices()
        waitForMemberBadges(expectedMembers = 3)
        val targetId = exactDeviceId(targetDeviceName, "Member")
        assertTrue("Removal target must expose a Remove action", hasTag("remove-device-$targetId"))
        marker("ANDROID_TEST15_REMOVAL_APPROVER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("Test #15 removal dialog must name the exact target", 1, exactTargetInDialog.size)
        marker("ANDROID_TEST15_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "test15-remove-approve", cycle)
        composeRule.onNodeWithTag("remove-device-dialog-accept", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
        composeRule.waitUntil(180_000) { deviceRowCount() == 2 }
        assertDeviceRowAbsent(targetDeviceName)

        openSecretsAndWaitFor(secretNames)
        secretNames.forEach { secretName ->
            assertTagAbsent("recovery-request-badge-$secretName")
            assertTagAbsent("open-recovery-request-$secretName")
        }
        marker("ANDROID_TEST15_REMOVAL_APPROVED_RECOVERY_CLEARED")
        waitForApproval(coordinator, "test15-cli-member-accepted", cycle)
        openDevices()
        openSecretsAndWaitFor(secretNames)
        marker("ANDROID_TEST15_CLI_STATE_REFRESHED")
        waitForApproval(coordinator, "test15-cli-redistribution-observed", cycle)
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST42_REMOVAL_RECEIVER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Removal dialog must expose one exact localized Web title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST42_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-web-approve-test42", "1")
        composeRule.onNodeWithTag("remove-device-dialog-accept", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("remove-device-${threeMemberIds.getValue("web")}")

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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST43_REMOVAL_RECEIVER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Removal dialog must expose one exact localized Web title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST43_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-remove-web-approve-test43", "1")
        composeRule.onNodeWithTag("remove-device-dialog-accept", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
        waitForExactDeviceMembership(
            listOf(
                DeviceRole("android", deviceNames.android, "Current", expectsRemoveAction = false),
                DeviceRole("ios", deviceNames.ios, "Member", expectsRemoveAction = true),
            ),
        )
        assertDeviceRowAbsent(deviceNames.web)
        assertTagAbsent("remove-device-${threeMemberIds.getValue("web")}")

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
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("revealed-secret-value", 30_000)
        composeRule.onNodeWithTag("revealed-secret-value", useUnmergedTree = true)
            .assertTextEquals(secretValue)
        marker("${markerPrefix}_LOCAL_SECRET_VALUE_VERIFIED_$cycle")

        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${threeMemberIds.getValue("web")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${threeMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${threeMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${threeMemberIds.getValue("web")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST47_PHASE1_DECLINER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Test #47 decline dialog must expose one exact Web title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST47_PHASE1_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test47", "1")
        composeRule.onNodeWithTag("remove-device-dialog-decline", useUnmergedTree = true)
            .performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST48_PHASE2_DECLINER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Test #48 decline dialog must expose one exact Web title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST48_PHASE2_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test48", "2")
        composeRule.onNodeWithTag("remove-device-dialog-decline", useUnmergedTree = true)
            .performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        marker("ANDROID_TEST49_PHASE3_DECLINER_READY")

        composeRule.waitForTag("pending-removal-device-row", 180_000)
        waitForMemberBadges(expectedMembers = 3)
        composeRule.onNodeWithTag("pending-removal-device-row", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(
            "Test #49 decline dialog must expose one exact Web title",
            1,
            exactTargetInDialog.size,
        )
        marker("ANDROID_TEST49_PHASE3_REMOVAL_REQUEST_VISIBLE")

        waitForApproval(coordinator, "android-decline-web-test49", "3")
        composeRule.onNodeWithTag("remove-device-dialog-decline", useUnmergedTree = true)
            .performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
        composeRule.waitForTagGone("pending-removal-device-row", 180_000)
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${twoMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${twoMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${twoMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
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
        assertTagAbsent("remove-device-${twoMemberIds.getValue("ios")}")
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST37_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                twoMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android member removal actions survived reset",
            twoMemberIds.values.none { hasTag("remove-device-$it") },
        )
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST39_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-device-row") &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                !hasTag("alert-join-request") &&
                !hasTag("alert-join-request-processing") &&
                threeMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android removal actions survived reset",
            threeMemberIds.values.none { hasTag("remove-device-$it") },
        )
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST40_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-device-row") &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                !hasTag("alert-join-request") &&
                !hasTag("alert-join-request-processing") &&
                threeMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android removal actions survived reset",
            threeMemberIds.values.none { hasTag("remove-device-$it") },
        )
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST44_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-device-row") &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                !hasTag("alert-join-request") &&
                !hasTag("alert-join-request-processing") &&
                threeMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android removal actions survived reset",
            threeMemberIds.values.none { hasTag("remove-device-$it") },
        )
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST41_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-device-row") &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                !hasTag("alert-join-request") &&
                !hasTag("alert-join-request-processing") &&
                twoMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android removal actions survived reset",
            twoMemberIds.values.none { hasTag("remove-device-$it") },
        )
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
        assertTagAbsent("pending-removal-device-row")
        assertTagAbsent("remove-device-dialog")
        assertTagAbsent("alert-recovery-request")
        marker("ANDROID_TEST45_REMOVAL_TARGET_READY")

        composeRule.waitUntil(180_000) {
            hasTag("signin-email-manual") &&
                !hasTag("tab-secrets") &&
                !hasTag("tab-devices") &&
                secretNames.none { hasTag("secret-row-$it") } &&
                !hasTag("pending-device-row") &&
                !hasTag("pending-removal-device-row") &&
                !hasTag("remove-device-dialog") &&
                !hasTag("alert-recovery-request") &&
                !hasTag("alert-recovery-request-processing") &&
                !hasTag("alert-join-request") &&
                !hasTag("alert-join-request-processing") &&
                twoMemberIds.values.none { hasTag("remove-device-$it") }
        }
        assertTrue("Android reset did not expose manual sign-in", hasTag("signin-email-manual"))
        assertFalse("Android Vault tabs survived removal", hasTag("tab-secrets") || hasTag("tab-devices"))
        assertTrue("Android Secret rows survived removal", secretNames.none { hasTag("secret-row-$it") })
        assertTrue(
            "Android member removal actions survived reset",
            twoMemberIds.values.none { hasTag("remove-device-$it") },
        )
        marker("ANDROID_TEST45_REMOVED_RESET")
    }

    private fun openSecretsAndWaitFor(secretNames: List<String>) {
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        secretNames.forEach { composeRule.waitForTag("secret-row-$it", 180_000) }
    }

    private fun openDevices() {
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
    }

    private fun revealAndConfirmRemoval(deviceId: String, targetDeviceName: String) {
        composeRule.onNodeWithTag("device-row-$deviceId", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        composeRule.waitForTag("remove-device-$deviceId", 30_000)
        composeRule.onNodeWithTag("remove-device-$deviceId", useUnmergedTree = true).performClick()
        composeRule.waitForTag("remove-device-dialog", 30_000)
        val localizedRemoveDevice = StringProviderAndroid(composeRule.activity)
            .getString(AppString.removeDevice)
        val exactRemovalTitle = "$localizedRemoveDevice $targetDeviceName?"
        val exactTargetInDialog = composeRule.onAllNodes(
            hasText(exactRemovalTitle, substring = false)
                .and(hasAnyAncestor(hasTestTag("remove-device-dialog"))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("Removal dialog must expose one exact target title", 1, exactTargetInDialog.size)
        composeRule.onNodeWithTag("remove-device-dialog-accept", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("remove-device-dialog", 120_000)
    }

    private fun assertNoStaleSecretUi(secretNames: List<String>) {
        secretNames.forEach { secretName ->
            assertTagAbsent("recovery-request-badge-$secretName")
            assertTagAbsent("open-recovery-request-$secretName")
        }
        listOf(
            "alert-recovery-request",
            "alert-recovery-request-processing",
            "alert-join-request",
            "alert-join-request-processing",
            "pending-device-row",
            "pending-removal-device-row",
            "remove-device-dialog",
        ).forEach(::assertTagAbsent)
    }

    private fun waitForMemberBadges(expectedMembers: Int) {
        composeRule.waitUntil(180_000) {
            exactTextCount("Current") == 1 && exactTextCount("Member") == expectedMembers - 1
        }
        assertEquals(1, exactTextCount("Current"))
        assertEquals(expectedMembers - 1, exactTextCount("Member"))
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
        composeRule.waitUntil(180_000) {
            deviceRowCount() == expectedRoles.size && expectedRoles.all(::deviceRoleMatches)
        }
        assertEquals(expectedRoles.size, deviceRowCount())
        return expectedRoles.associate { role ->
            assertTrue(
                "${role.platform} row did not match exact name/status/remove role",
                deviceRoleMatches(role),
            )
            role.platform to exactDeviceId(role.name, role.status)
        }
    }

    private fun waitForExactSettledDeviceMembership(expectedRoles: List<DeviceRole>): Map<String, String> {
        composeRule.waitUntil(180_000) {
            !hasTag("pending-removal-device-row") &&
                deviceRowCount() == expectedRoles.size &&
                expectedRoles.all(::deviceRoleMatches)
        }
        assertTagAbsent("pending-removal-device-row")
        return expectedRoles.associate { role ->
            assertTrue(
                "${role.platform} row did not match the settled exact removal topology",
                deviceRoleMatches(role),
            )
            role.platform to exactDeviceId(role.name, role.status)
        }
    }

    private fun deviceRoleMatches(role: DeviceRole): Boolean {
        val matchingRows = composeRule.onAllNodes(
            deviceRowMatcher()
                .and(hasAnyDescendant(hasText(role.name, substring = false)))
                .and(hasAnyDescendant(hasText(role.status, substring = false))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (matchingRows.size != 1) return false
        val deviceId = semanticsTestTag(matchingRows.single())
            ?.removePrefix("device-row-")
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return hasTag("remove-device-$deviceId") == role.expectsRemoveAction
    }

    private fun exactDeviceId(name: String, status: String): String {
        val rows = composeRule.onAllNodes(
            deviceRowMatcher()
                .and(hasAnyDescendant(hasText(name, substring = false)))
                .and(hasAnyDescendant(hasText(status, substring = false))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("Expected one exact device row for $name/$status", 1, rows.size)
        return semanticsTestTag(rows.single())!!.removePrefix("device-row-")
    }

    private fun assertDeviceRowAbsent(name: String) {
        val rows = composeRule.onAllNodes(
            deviceRowMatcher().and(hasAnyDescendant(hasText(name, substring = false))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("Removed device row remained visible: $name", rows.isEmpty())
    }

    private fun deviceRowCount(): Int =
        composeRule.onAllNodes(deviceRowMatcher(), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun deviceRowMatcher(): SemanticsMatcher =
        SemanticsMatcher("TestTag starts with device-row-") { node ->
            semanticsTestTag(node)?.startsWith("device-row-") == true
        }

    private fun semanticsTestTag(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        runCatching { node.config[SemanticsProperties.TestTag] }.getOrNull()

    private fun waitForExactPrimaryAction(secretName: String, expected: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextEquals(expected)
            }.isSuccess
        }
    }

    private fun exactTextCount(text: String): Int =
        composeRule.onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun assertTagAbsent(tag: String) {
        assertFalse("Unexpected visible UI tag: $tag", hasTag(tag))
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
