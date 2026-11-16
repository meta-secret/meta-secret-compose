package metasecret.project.com

import android.util.Log
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import org.koin.core.context.GlobalContext
import androidx.compose.ui.test.*
import core.metaSecretCore.liveboard.LiveboardKind
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import core.AppString
import core.StringProviderInterface
import core.AppStateCacheProviderInterface
import models.apiModels.VaultFullInfo
import models.apiModels.SecretDeletionStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metasecret.core.MetaSecretNative
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CaseEightAndroidBothOfflineTest {
    private val launchIntent = android.content.Intent.makeMainActivity(android.content.ComponentName(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java))
    val composeRule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(launchIntent)) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
    @get:Rule
    val activityAndAuthentication: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(LiveboardRealAuthenticationRule(launchIntent)).around(composeRule)

    @Before
    fun requireTest50CoreRoutingBeforeBusiness() {
        val required = argument("requiredE2eServerUrl", "")
        if (required.isNotBlank()) {
            check(BuildConfig.META_SECRET_E2E_SERVER_URL == required) { "Test50 APK lost its required Core HTTP proxy configuration" }
            check(android.system.Os.getenv("METASECRET_E2E_SERVER_URL") == required) { "Test50 Core HTTP override was not configured before business startup" }
        }
    }

    @Test
    fun joinWebInitiatedVault() {
        val vaultName = argument("vaultName", "test8@test.ru")
        val secretName = argument("secretName", "test-secret")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name -> composeRule.lbSecret(name) }
        marker("ANDROID_JOIN_READY")
        marker("ANDROID_SECRETS_READY")
    }

    /** Test #40 joins the Web-owned Vault with run-specific readiness markers. */
    @Test
    fun joinWebInitiatedVaultForTest40() {
        val vaultName = argument("vaultName", "test40@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST40_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST40_JOIN_READY")
        marker("ANDROID_TEST40_SECRETS_READY")
    }

    /** Test #43 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest43() {
        val vaultName = argument("vaultName", "test43@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST43_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST43_JOIN_READY")
        marker("ANDROID_TEST43_SECRETS_READY")
    }

    /** Test #46 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest46() {
        val vaultName = argument("vaultName", "test46@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST46_INITIAL_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST46_INITIAL_JOIN_READY")
        marker("ANDROID_TEST46_INITIAL_SECRETS_READY")
    }

    /** Test #47 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest47() {
        val vaultName = argument("vaultName", "test47@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST47_INITIAL_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST47_INITIAL_JOIN_READY")
        marker("ANDROID_TEST47_INITIAL_SECRETS_READY")
    }

    @Test
    fun joinIosInitiatedVaultForTest39() {
        val vaultName = argument("vaultName", "test39@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST39_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST39_JOIN_READY")
        marker("ANDROID_TEST39_SECRETS_READY")
    }

    /** Test #42 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest42() {
        val vaultName = argument("vaultName", "test42@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST42_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST42_JOIN_READY")
        marker("ANDROID_TEST42_SECRETS_READY")
    }

    /** Test #45 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest45() {
        val vaultName = argument("vaultName", "test45@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST45_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST45_JOIN_READY")
        marker("ANDROID_TEST45_SECRETS_READY")
    }

    /** Test #49 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest49() {
        val vaultName = argument("vaultName", "test49@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_TEST49_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.lbSecret(name)
        }
        marker("ANDROID_TEST49_JOIN_READY")
        marker("ANDROID_TEST49_SECRETS_READY")
    }

    @Test
    fun joinAndShowLocalSecrets() {
        val vaultName = argument("vaultName", "test22@test.ru")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val directShowSecretNames = argument("directShowSecretNames", "")
            .split(',')
            .filter { it.isNotBlank() }
        val firstSecret = directShowSecretNames.getOrNull(0)
            ?: argument("secretName", "test-secret")
        val secondSecret = directShowSecretNames.getOrNull(1) ?: firstSecret

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name -> composeRule.lbSecret(name) }
        marker("ANDROID_JOIN_READY")
        marker("ANDROID_SECRETS_READY")

        waitForApproval(coordinator, "android-direct-show-start", "4")
        showLocalSecretBatch(firstSecret, listOf("4", "5", "6"))
        waitForApproval(coordinator, "android-direct-show-start", "10")
        showLocalSecretBatch(secondSecret, listOf("10", "11", "12"))
        marker("ANDROID_DIRECT_SHOW_DONE")
    }

    /** Test #33 redistribution boundary: Android must render Secret3 before cycle 19. */
    @Test
    fun assertSecretVisibleForTest33() {
        val secretName = argument("secretName", "test33-secret3")
        val cycle = argument("cycle", "18")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbSecrets()
        composeRule.lbSecret(secretName)
        marker("ANDROID_TEST33_SECRET_VISIBLE_$cycle")
    }

    @Test
    fun rejectFourthDeviceAndRegisterNewVault() {
        val vaultName = argument("vaultName", "test21@test.ru")
        val retryVaultName = argument("retryVaultName", "test21-retry@test.ru")

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin(awaitPending = false)

        composeRule.orbitWaitForTag("orbit-phase-error", 120_000)
        composeRule.onNodeWithTag("orbit-status", useUnmergedTree = true)
            .assertTextEquals("This vault already has the maximum of 3 devices.")
        marker("ANDROID_FOURTH_DEVICE_REJECTED")
        waitForApproval(
            argument("approvalCoordinatorUrl", "http://10.0.2.2:5180"),
            "android-rejection-screenshot",
            "1",
        )

        composeRule.onNodeWithTag("orbit-back").performClick()
        composeRule.orbitEnterVaultName(retryVaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(120_000)
        marker("ANDROID_FOURTH_DEVICE_RETRY_REGISTERED")
    }

    @Test
    fun runNetworkLossCycles() {

        val role = argument("networkRole", "")
        val cycles = argument("networkCycles", "").split(',').filter { it.isNotBlank() }
        val name = argument("secretName", "test-secret")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        check(role in setOf("sender", "offline-receiver", "observer") && cycles.isNotEmpty())
        marker("ANDROID_NETWORK_CONFIG role=$role cycles=${cycles.joinToString(",")}")
        composeRule.lbSecret(name)
        marker("ANDROID_NETWORK_READY")
        cycles.forEach { cycle ->
            when (role) {
                "sender" -> {
                    waitForApproval(coordinator, "android-sender-1", cycle)
                    showAcceptedSecretIfNeeded(name, cycle, "1")
                    composeRule.lbRequestRecovery(name)
                    marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_1")
                    composeRule.lbCloseCurrentRevealOrPending()
                    waitForApproval(coordinator, "android-show-1", cycle)
                    revealAcceptedSecret(name, cycle, "1")
                    marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_1")
                    composeRule.lbHide(checkNotNull(composeRule.lbScope().selectedPass))
                    waitForPrimaryAction(name, "Recover")
                    marker("ANDROID_RECOVERY_CLOSED_${cycle}_1")
                }
                "offline-receiver" -> {
                    val head = composeRule.lbIncoming("Recover", name)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle}_1")
                    marker("ANDROID_NETWORK_LOSS_READY_${cycle}")
                    waitForApproval(coordinator, "android-offline-attempt-1", cycle)
                    composeRule.lbTap("lb-approve-${head.requestId}")
                    composeRule.lbApproveSystemIfPresent()
                    marker("ANDROID_OFFLINE_APPROVE_CLICKED_${cycle}")
                    waitForApproval(coordinator, "android-offline-online-1", cycle)
                    // Owner-approved Test16 manual error acknowledgement/new action, exact SAME head.
                    composeRule.lbTest16ReconnectApprove(head)
                    marker("ANDROID_APPROVED_AFTER_RECONNECT_${cycle}_1")
                }
                "observer" -> {
                    val head = composeRule.lbIncoming("Recover", name)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle}_1")
                    waitForApproval(coordinator, "android-observer-finish-1", cycle)
                    composeRule.lbIncomingTerminal(head)
                    marker("ANDROID_OBSERVER_CLOSED_${cycle}_1")
                }
            }
        }
        marker("ANDROID_NETWORK_LOSS_DONE")
    }

    @Test
    fun sendRecoveryRequestAndExit() {

        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.lbSecret(secretName)
        waitForApproval(coordinator, "android-sender-1", cycle)
        composeRule.lbRequestRecovery(secretName)
        marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_1")
        waitForApproval(coordinator, "android-request-persisted", cycle)
        marker("ANDROID_SENDER_OFFLINE_BOUNDARY_$cycle")
    }

    @Test
    fun showAcceptedRecoveryAfterOffline() {

        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        waitForPrimaryAction(secretName, "Show")
        waitForApproval(coordinator, "android-show-1", cycle)
        val id = composeRule.lbShow(secretName)
        marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_1")
        composeRule.lbHide(id)
        waitForPrimaryAction(secretName, "Recover")
        marker("ANDROID_RECOVERY_CLOSED_${cycle}_1")
    }

    @Test
    fun showLocalSecret() {

        val name = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        composeRule.lbSecrets()
        val stateResult = runCatching { MetaSecretNative.getState() }
        marker("ANDROID_LOCAL_SHOW_NATIVE_STATE cycle=$cycle stateBytes=${stateResult.getOrNull()?.length ?: -1} stateHasSecretName=${stateResult.getOrNull()?.contains(name) == true}")
        val id = composeRule.lbShow(name)
        marker("ANDROID_LOCAL_SECRET_VISIBLE_$cycle")
        composeRule.lbHide(id)
        waitForPrimaryAction(name, "Show")
        marker("ANDROID_LOCAL_SECRET_CLOSED_$cycle")
    }

    private fun showLocalSecretBatch(secretName: String, cycles: List<String>) {

        composeRule.lbSecrets()
        cycles.forEach { cycle ->
            waitForPrimaryAction(secretName, "Show")
            val stateResult = runCatching { MetaSecretNative.getState() }
            marker("ANDROID_LOCAL_SHOW_NATIVE_STATE cycle=$cycle stateBytes=${stateResult.getOrNull()?.length ?: -1} stateHasSecretName=${stateResult.getOrNull()?.contains(secretName) == true}")
            val id = composeRule.lbShow(secretName)
            marker("ANDROID_LOCAL_SECRET_VISIBLE_$cycle")
            composeRule.lbHide(id)
            waitForPrimaryAction(secretName, "Show")
            marker("ANDROID_LOCAL_SECRET_CLOSED_$cycle")
        }
    }

    @Test
    fun handleRecoveryStep() {

        val name = argument("secretName", "test-secret")
        val value = argument("secretValue", "")
        val role = argument("role", "receiver")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val approvalPlatform = argument("approvalPlatform", "")
        val expectedOutcome = argument("expectedOutcome", "")
        val verifyReceiverAlert = argument("verifyReceiverAlert", "false") == "true"
        val alreadySent = argument("receiverRequestAlreadySent", "false") == "true"
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val phaseArguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        check(!phaseArguments.containsKey("recoveryShowPhase") && !phaseArguments.containsKey("test25ShowPhase")
            && !phaseArguments.containsKey("test22ShowPhase")) {
            "iOS Show procedure metadata is not an Android test entry"
        }
        fun phaseString(key: String, allowEmpty: Boolean = false): String? {
            if (!phaseArguments.containsKey(key)) return null
            val value = phaseArguments.get(key) as? String ?: error("Invalid recovery phase launch metadata")
            check(allowEmpty || value.isNotEmpty()) { "Empty recovery phase launch metadata" }
            return value
        }
        val phaseRequired = when (if (phaseArguments.containsKey("recoveryPhaseRequired")) phaseArguments.get("recoveryPhaseRequired") else "false") {
            "true" -> true
            "false" -> false
            else -> error("Invalid recovery phase requirement")
        }
        val phaseContext = Test24RecoveryPhaseContext.readEntry(Test24PhaseEntry(
            raw = phaseString("recoveryPhase", allowEmpty = true), required = phaseRequired,
            selectedProfile = phaseString("recoveryPhaseSelectedProfile"), expected = phaseString("recoveryPhaseExpected"),
        ))
        phaseContext?.requireCurrent(role, cycle, step)
        val phase = phaseContext?.let { makeTest24RecoveryPhaseClient(it, coordinator) }
        composeRule.lbSecret(name)
        if (role == "sender") {
            showAcceptedSecretIfNeeded(name, cycle, step)
            val id = composeRule.lbWaitPrimary(name, "Recover")
            composeRule.lbValueAbsent(id)
            composeRule.lbHome()
            check(composeRule.lbVisibleIncoming() == null)
            marker("ANDROID_SENDER_UI_READY_${cycle}_$step")
            marker("ANDROID_STEP_CONFIG role=$role cycle=$cycle step=$step approval=$approvalPlatform")
            if (phase != null) {
                marker(phase.context.marker("READY"))
                phase.awaitDispatch()
            } else waitForApproval(coordinator, "android-sender-$step", cycle)
            composeRule.lbRequestRecovery(name)
            marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_$step")
            phase?.let { marker(it.context.marker("REQUEST_SENT")) }
            if (approvalPlatform == "cli") {
                if (expectedOutcome == "membership-removal") waitForApproval(coordinator, "android-request-committed-$step", cycle)
                composeRule.lbCloseCurrentRevealOrPending()
                if (expectedOutcome == "membership-removal") {
                    marker("ANDROID_RECOVERY_REQUEST_READY_FOR_REMOVAL_${cycle}_$step")
                    return
                }
                waitForApproval(coordinator, "android-invalidated-$step", cycle)
                waitForPrimaryAction(name, "Recover")
                marker("ANDROID_RECOVERY_INVALIDATED_${cycle}_$step")
                return
            }
            waitForApproval(coordinator, "android-show-$step", cycle)
            revealAcceptedSecret(name, cycle, step, value.ifBlank { null })
            marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
            composeRule.lbHide(id)
            waitForPrimaryAction(name, "Recover")
            marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            return
        }
        composeRule.lbHome()
        if (!alreadySent) check(composeRule.lbVisibleIncoming() == null && composeRule.lbIncomingCount() == 0)
        val probe = RecoveryUiDiagnosticProbe(name)
        probe.capture(composeRule, "receiver-ready", cycle, step)
        marker("ANDROID_RECEIVER_UI_READY_${cycle}_$step")
        marker("ANDROID_STEP_CONFIG role=$role cycle=$cycle step=$step approval=$approvalPlatform")
        val badgeTimeout = if (phase != null) {
            marker(phase.context.marker("READY"))
            phase.awaitRequestSent()
        } else 180_000L
        composeRule.waitForRecoveryBadgeWithDiagnostics(name, badgeTimeout, cycle, step, probe)
        val head = checkNotNull(composeRule.lbVisibleIncoming())
        check(head.kind == "Recover" && head.subject == name)
        marker("ANDROID_INCOMING_VISIBLE_${cycle}_$step")
        phase?.let { marker(it.context.marker("INCOMING_VISIBLE")) }
        if (composeRule.lbVisibleIncoming()?.requestId != head.requestId) {
            composeRule.lbIncomingTerminal(head)
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        if (expectedOutcome == "membership-removal") {
            marker("ANDROID_RECOVERY_READY_FOR_REMOVAL_${cycle}_$step")
            return
        }
        if (approvalPlatform == "android" || verifyReceiverAlert) {
            openRecoveryRequestAlert(name, cycle, step)
            check(composeRule.lbVisibleIncoming() == head)
            val observerDeadline = if (approvalPlatform == "android") null else System.nanoTime() + 60_000_000_000L
            marker("ANDROID_INCOMING_ALERT_VISIBLE_${cycle}_$step")
            if (approvalPlatform == "android") {
                waitForApproval(coordinator, "android-approve-$step", cycle)
                composeRule.lbDecide(head, true)
                awaitNativeAcceptCompletion(cycle, step)
                marker("ANDROID_APPROVED_INCOMING_${cycle}_$step")
            } else {
                composeRule.lbDismissIncomingObserver(head, checkNotNull(observerDeadline))
                marker("ANDROID_INCOMING_ALERT_DISMISSED_WITHOUT_DECISION_${cycle}_$step")
                waitForApproval(coordinator, "android-dismiss-$step", cycle)
                composeRule.lbIncomingTerminal(head)
                marker("ANDROID_DISMISSED_INCOMING_${cycle}_$step")
            }
        } else {
            waitForApproval(coordinator, "android-dismiss-$step", cycle)
            composeRule.lbIncomingTerminal(head)
            marker("ANDROID_DISMISSED_INCOMING_${cycle}_$step")
        }
    }

    private fun makeTest24RecoveryPhaseClient(context: Test24RecoveryPhaseContext, base: String) =
        Test24RecoveryPhaseClient(context, base)

    /**
     * The badge/button can remain composed for one state frame while the
     * authoritative pending-claim list is refreshed. In that frame
     * MainScreenViewModel correctly refuses to open an obsolete claim, so a
     * single click would be a no-op. Retry the UI event while the control is
     * still present; this is event-driven and deliberately contains no sleep.
     */
    private fun openRecoveryRequestAlert(secretName: String, cycle: String, step: String) {

        marker("ANDROID_OPEN_REQUEST_CLICK cycle=$cycle step=$step")
        val head = try { composeRule.lbIncoming("Recover", secretName) }
        catch (failure: Throwable) {
            marker("ANDROID_OPEN_REQUEST_TIMEOUT_${cycle}_$step headPresent=${composeRule.lbVisibleIncoming() != null} count=${composeRule.lbIncomingCount()}")
            throw failure
        }
        marker("ANDROID_OPEN_REQUEST_STATE cycle=$cycle step=$step head=${head.requestId} count=${head.count}")
        check(composeRule.lbHas("lb-approve-${head.requestId}"))
    }

    @Test
    fun handleApproveDeclineStep() {

        val name = argument("secretName", "test-secret")
        val role = argument("role", "receiver")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val decision = argument("decision", "approve")
        val outcome = argument("expectedOutcome", "approved")
        val repeat = argument("repeatApprove", "false") == "true"
        val duplicate = argument("duplicateRecovery", "false") == "true"
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.lbSecret(name)
        if (role == "sender") {
            waitForApproval(coordinator, "android-sender-$step", cycle)
            showAcceptedSecretIfNeeded(name, cycle, step)
            val id = composeRule.lbRequestRecovery(name)
            marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_$step")
            if (duplicate) {
                val duplicateSent = runCatching { composeRule.onNodeWithTag("lb-primary-$id", useUnmergedTree = true).assertIsEnabled().performClick(); true }.getOrDefault(false)
                marker(if (duplicateSent) "ANDROID_DUPLICATE_RECOVERY_SENT_${cycle}_$step" else "ANDROID_DUPLICATE_RECOVERY_SKIPPED_AFTER_GUARD_${cycle}_$step")
                composeRule.lbCloseCurrentRevealOrPending()
                marker("ANDROID_DUPLICATE_RECOVERY_PENDING_CLOSED_${cycle}_$step")
                waitForApproval(coordinator, "android-duplicate-finish-$step", cycle)
                revealAcceptedSecret(name, cycle, step)
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
                composeRule.lbHide(id)
                waitForPrimaryAction(name, "Recover")
                marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
                return
            }
            composeRule.lbCloseCurrentRevealOrPending()
            if (outcome == "approved") {
                waitForApproval(coordinator, "android-show-$step", cycle)
                revealAcceptedSecret(name, cycle, step)
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
                composeRule.lbHide(id)
            } else {
                waitForApproval(coordinator, "android-declined-$step", cycle)
                composeRule.lbSecret(name)
                composeRule.lbValueAbsent(id)
                marker("ANDROID_RECOVERY_NOT_VISIBLE_${cycle}_$step")
            }
            waitForPrimaryAction(name, "Recover")
            marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            return
        }
        val head = composeRule.lbObserveIncoming("Recover", name)
        if (duplicate) {
            check(head.count == 1)
            marker("ANDROID_SINGLE_ACTIVE_CLAIM_${cycle}_$step")
        }
        marker("ANDROID_INCOMING_VISIBLE_${cycle}_$step")
        waitForApproval(coordinator, "android-$decision-$step", cycle)
        if (composeRule.lbVisibleIncoming()?.requestId != head.requestId) {
            composeRule.lbIncomingTerminal(head)
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        marker("ANDROID_OPEN_REQUEST_CLICK_START_${cycle}_$step")
        if (!composeRule.lbOpenIncomingOrTerminal(head)) {
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        marker("ANDROID_OPEN_REQUEST_CLICK_RETURNED_${cycle}_$step")
        if (composeRule.lbVisibleIncoming()?.requestId != head.requestId) {
            composeRule.lbIncomingTerminal(head)
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        check(composeRule.lbVisibleIncoming() == head)
        marker("ANDROID_ACTION_STARTED_${cycle}_$step")
        if (decision == "decline") {
            composeRule.lbDecide(head, false)
            marker("ANDROID_DECLINED_INCOMING_${cycle}_$step")
        } else {
            composeRule.lbTap("lb-approve-${head.requestId}")
            if (repeat && runCatching { composeRule.onNodeWithTag("lb-approve-${head.requestId}", useUnmergedTree = true).assertIsEnabled() }.isSuccess) {
                marker("ANDROID_REPEAT_APPROVE_SECOND_TAP_SENT_${cycle}_$step")
                composeRule.lbTap("lb-approve-${head.requestId}")
            } else if (repeat) marker("ANDROID_REPEAT_APPROVE_SECOND_TAP_SKIPPED_AFTER_DISMISS_${cycle}_$step")
            composeRule.lbApproveSystemIfPresent()
            composeRule.lbIncomingTerminal(head)
            marker("ANDROID_APPROVED_INCOMING_${cycle}_$step")
        }
    }

    /** Test50 actors share the proven instrumentation lifecycle and coordinator barrier. */
    @Test
    fun handleSecretDeletionStep() {

        val name = argument("secretName", "")
        val value = argument("secretValue", "")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val role = argument("role", "observe")
        val outcome = argument("expectedOutcome", "accepted")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        require(name.isNotBlank())
        require(role in setOf("requester", "approve", "decline", "observe", "cancel", "show", "sync-observe", "startup-gone"))
        require(outcome in setOf("accepted", "declined", "pending"))
        fun emit(action: String) = marker("ANDROID_DELETION_${action}_${cycle}_$step")
        fun barrier(action: String) = waitForApproval(coordinator, "android-deletion-$action-$step", cycle)
        val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
        fun readiness(stage: String) {
            val sync = cache.synchronization.value
            marker("ANDROID_TEST50_READINESS_DIAG cycle=$cycle step=$step role=$role stage=$stage activeRefreshes=${sync.activeRefreshes} loading=${sync.loading} failed=${sync.failed} currentFrame=${cache.canonicalFrames.value != null} home=${composeRule.lbHas("lb-home")} secrets=${composeRule.lbHas("lb-secrets")}")
        }
        readiness("actor-enter")
        fun held() = cache.synchronization.value.let { it.loading || it.activeRefreshes > 0 }
        fun idle() {
            composeRule.waitUntil(180_000) { cache.synchronization.value.let { !it.loading && it.activeRefreshes == 0 } }
            check(!cache.synchronization.value.failed) { "Current canonical read failed" }
        }
        if (role == "startup-gone") {
            composeRule.waitUntil(180_000) { held() }
            check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
            emit("READY")
            barrier("action"); barrier("terminal")
            idle(); composeRule.lbSecrets()
            check(deletionCachedMember()?.member?.vault?.secrets?.none { it.name == name } == true)
            composeRule.onNodeWithTag("lb-search", useUnmergedTree = true).performTextReplacement(name)
            check(composeRule.lbTagged("lb-secret-name-").none { lbNodeTexts(it) == listOf(name) })
            check(composeRule.lbTagged("lb-value-").isEmpty() && composeRule.lbTagged("lb-copy-").isEmpty())
            emit("TERMINAL")
            return
        }
        composeRule.lbWaitMain(180_000)
        idle(); readiness("before-target-row-ready"); waitForDeletionRow(name, 180_000)
        readiness("after-target-row-ready")
        val id = checkNotNull(deletionCachedMember()?.member?.vault?.secrets?.singleOrNull { it.name == name }?.id)
        check(composeRule.lbFindSecret(name) == id)
        composeRule.lbScope().selectedPass = id
        marker("ANDROID_DELETION_ID_${cycle}_$step:$id")
        composeRule.lbValueAbsent(id)
        val initialRequests = checkNotNull(lbSnapshot()).operations.filter { it.identity.kind == LiveboardKind.DeleteSecret }.map { it.identity.requestId }.toSet()
        var copyBounds: Rect? = null
        if (role == "sync-observe") {
            prepareDeletionAccessibility()
            deletionAccessibilityRoot(false, false, name)
        }
        if (role == "show" || role == "sync-observe") {
            require(value.isNotBlank())
            composeRule.lbShow(name, value)
            if (role == "sync-observe") copyBounds = captureDeletionCopyBounds()
        }
        emit("READY")
        var incoming: LiveboardTestIncoming? = null
        if (role == "approve" || role == "decline" || (role == "observe" && argument("expectRequest", "true") == "true")) {
            incoming = composeRule.lbIncoming("DeleteSecret", name)
            val operation = checkNotNull(lbSnapshot()).operations.single { it.identity.requestId == incoming!!.requestId && it.identity.kind == LiveboardKind.DeleteSecret }
            check(operation.identity.secretId == id)
            emit("REQUEST_VISIBLE")
        }
        if (role != "observe") barrier("action")
        when (role) {
            "requester", "cancel" -> {
                composeRule.lbSecrets()
                ensureDeletionTag("lb-delete-$id", 30_000)
                if (role == "cancel" && argument("authProfile", "real-os") == "controlled-business") {
                    check(checkNotNull(lbSnapshot()).operations.filter { it.identity.kind == LiveboardKind.DeleteSecret }.map { it.identity.requestId }.toSet() == initialRequests)
                    check(deletionCachedMember()?.member?.vault?.secrets?.singleOrNull { it.name == name }?.id == id)
                    composeRule.onNodeWithTag("lb-delete-$id", useUnmergedTree = true).assertIsEnabled()
                    composeRule.lbValueAbsent(id)
                    emit("AUTH_MANUAL_PENDING")
                    return
                }
                composeRule.lbTap("lb-delete-$id")
                if (role == "cancel") {
                    composeRule.lbCancelSystemPrompt()
                    idle(); waitForDeletionRow(name, 30_000)
                    check(checkNotNull(lbSnapshot()).operations.filter { it.identity.kind == LiveboardKind.DeleteSecret }.map { it.identity.requestId }.toSet() == initialRequests)
                    check(deletionCachedMember()?.member?.vault?.secrets?.singleOrNull { it.name == name }?.id == id)
                    composeRule.onNodeWithTag("lb-delete-$id", useUnmergedTree = true).assertIsEnabled()
                    composeRule.lbValueAbsent(id)
                    emit("CANCELLED")
                    return
                }
                composeRule.lbApproveSystemIfPresent()
                composeRule.waitUntil(180_000) {
                    val snapshot = lbSnapshot() ?: return@waitUntil false
                    snapshot.secrets.none { it.identity.passId == id } || snapshot.operations.any {
                        it.identity.kind == LiveboardKind.DeleteSecret && it.identity.secretId == id &&
                            it.identity.requesterId == snapshot.identity.currentDeviceId && it.identity.requestId !in initialRequests
                    }
                }
                emit("SUBMITTED")
            }
            "approve", "decline" -> {
                composeRule.lbDecide(checkNotNull(incoming), role == "approve")
                composeRule.waitUntil(180_000) {
                    deletionCachedMember()?.deletionStates?.any {
                        it.secretId == id && it.clientStatus == (if (role == "approve") SecretDeletionStatus.Done else SecretDeletionStatus.Declined)
                    } == true || (role == "approve" && lbSnapshot()?.secrets?.none { it.identity.passId == id } == true)
                }
                emit("SUBMITTED")
            }
            "observe" -> Unit
            "sync-observe" -> {
                composeRule.lbAssertHeldReadPrivacy(id, value, checkNotNull(copyBounds))
                emit("SYNC_BLOCKED")
                barrier("sync-release")
                idle()
            }
            "show" -> {
                // Preserve the SAME original disclosure across a harmless own refresh. Never Show again.
                composeRule.runOnIdle { GlobalContext.get().get<MetaSecretSocketHandlerInterface>().refreshAppState() }
                idle()
                verifyDeletionReveal(value)
                composeRule.lbHide(id)
                composeRule.lbValueAbsent(id)
                emit("SHOW_VALID")
                return
            }
        }
        barrier("terminal"); idle()
        if (outcome == "accepted") {
            composeRule.lbSecrets()
            composeRule.onNodeWithTag("lb-search", useUnmergedTree = true).performTextReplacement("")
            assertDeletionTagAbsent("lb-secret-$id")
            composeRule.lbSecretAbsent(id, name)
            check(deletionCachedMember()?.member?.vault?.secrets?.none { it.name == name } == true)
            composeRule.lbValueAbsent(id)
        } else {
            waitForDeletionRow(name, 30_000)
            if (role == "show" || role == "sync-observe") verifyDeletionReveal(value)
        }
        if (outcome != "pending") {
            check(lbSnapshot()?.operations?.none { it.identity.kind == LiveboardKind.DeleteSecret && it.identity.secretId == id && it.status.outcome == null } == true)
            composeRule.lbHome()
            check(composeRule.lbVisibleIncoming()?.let { it.kind == "DeleteSecret" && it.subject == name } != true)
        }
        emit("TERMINAL")
    }

    /** Test50 growing-list navigation; do not change legacy fixed-list helpers. */
    private fun deletionScrollContainer(): androidx.compose.ui.test.SemanticsNodeInteraction {
        val containers = composeRule.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true).fetchSemanticsNodes()
        check(containers.size == 1) { "Test50 expects one semantic Secret list; containers=${containers.size}" }
        return composeRule.onNode(hasScrollToNodeAction(), useUnmergedTree = true)
    }

    private fun ensureDeletionTag(tag: String, timeoutMillis: Long) {

        composeRule.waitUntil(timeoutMillis) {
            val sync = GlobalContext.get().get<AppStateCacheProviderInterface>().synchronization.value
            !sync.loading && sync.activeRefreshes == 0 && !sync.failed
        }
        try {
            composeRule.waitUntil(timeoutMillis) {
                try { deletionScrollContainer().performScrollToNode(hasTestTag(tag)); hasTagNow(tag) }
                catch (missing: AssertionError) {
                    if (missing.message?.startsWith("No node found that matches") == true) false else throw missing
                }
            }
        } catch (failure: Throwable) {
            marker("ANDROID_TEST50_LIST_DIAGNOSTIC containers=" + composeRule.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true).fetchSemanticsNodes().size + " targetComposed=" + hasTagNow(tag))
            throw failure
        }
    }

    private fun waitForDeletionRow(name: String, timeoutMillis: Long) {

        composeRule.waitUntil(timeoutMillis) { deletionCachedMember()?.member?.vault?.secrets?.count { it.name == name } == 1 }
        val id = checkNotNull(deletionCachedMember()?.member?.vault?.secrets?.single { it.name == name }?.id)
        composeRule.lbSecrets()
        // Clear the actual filter and traverse the real LazyColumn; no composed-only absence oracle.
        composeRule.onNodeWithTag("lb-search", useUnmergedTree = true).performTextReplacement("")
        ensureDeletionTag("lb-secret-$id", timeoutMillis)
        check(composeRule.lbFindSecret(name) == id)
    }

    private fun assertDeletionTagAbsent(tag: String) {

        val found = try { deletionScrollContainer().performScrollToNode(hasTestTag(tag)); true }
        catch (missing: AssertionError) {
            if (missing.message?.startsWith("No node found that matches") == true) false else throw missing
        }
        check(!found) { "Stale Secret/control remains in the complete actual semantic list" }
    }

    private fun deletionCachedMember() =
        (GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value?.getVaultFullInfo()
            as? VaultFullInfo.Member)?.member

    /** F3 OS/UI capability probe: no Vault action or explicit Core request. */
    @Test
    fun verifyTest50AccessibilityReady() {
        prepareDeletionAccessibility()
        var anchor = ""
        composeRule.waitUntil(30_000) {
            val tag = when {
                hasTagNow("onboarding-skip") -> "onboarding-skip"
                hasTagNow("orbit-enter") && hasTagNow("orbit-heading") -> "orbit-heading"
                else -> null
            }
            if (tag != null) {
                val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                anchor = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                    ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
            }
            anchor.isNotBlank()
        }
        deletionAccessibilityRoot(copyBaseline = false, blockerRequired = false, anchorText = anchor, allowKnownCompatibility = true)
        marker("ANDROID_TEST50_ACCESSIBILITY_READY")
    }

    private fun deletionAccessibilityNodes(root: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val nodes = mutableListOf(root)
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index++]
            for (child in 0 until node.childCount) node.getChild(child)?.let {
                check(nodes.size < 512) { "Test50 accessibility traversal exceeded its complete-tree bound" }
                nodes.add(it)
            }
        }
        return nodes
    }

    private fun deletionAccessibilityMatches(nodes: List<android.view.accessibility.AccessibilityNodeInfo>, expected: String) =
        nodes.filter { it.text?.toString() == expected || it.contentDescription?.toString() == expected || it.viewIdResourceName == expected }

    /** Enable the test accessibility service before opening Show so its window events are observed. */
    private fun prepareDeletionAccessibility(): android.app.UiAutomation {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        automation.serviceInfo = info
        return automation
    }

    private fun deletionAccessibilityRoot(copyBaseline: Boolean, blockerRequired: Boolean = true, anchorText: String = "", allowKnownCompatibility: Boolean = false): android.view.accessibility.AccessibilityNodeInfo {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val automation = prepareDeletionAccessibility()
        val strings = GlobalContext.get().get<StringProviderInterface>()
        val copyLabel = strings.getString(AppString.lbCopy)
        val blockerLabels = listOf(strings.getString(AppString.lbPending), strings.getString(AppString.lbError))
        var root: android.view.accessibility.AccessibilityNodeInfo? = null
        var compatibilityDismissed = false
        try {
            composeRule.waitUntil(15_000) {
                val candidate = automation.rootInActiveWindow
                val nodes = deletionAccessibilityNodes(candidate)
                val ownVisible = nodes.filter { it.packageName?.toString() == instrumentation.targetContext.packageName && it.isVisibleToUser }
                val ownWindow = automation.windows.any { it.id == candidate?.windowId && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused } && ownVisible.isNotEmpty()
                if (allowKnownCompatibility && !compatibilityDismissed && candidate?.packageName?.toString() == "android" &&
                    automation.windows.any { it.id == candidate.windowId && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM && it.isActive && it.isFocused }) {
                    val knownBody = nodes.any { it.isVisibleToUser && it.text?.toString()?.startsWith("This app isn’t 16 KB compatible. ELF alignment check failed.") == true }
                    val knownTitle = deletionAccessibilityMatches(nodes, "MetaSecret").any { it.isVisibleToUser }
                    if (knownBody && knownTitle) {
                        check(argument("role", "") == "accessibility-probe" && !hasTagNow("lb-secrets") && deletionCachedMember() == null) { "Compatibility handling is restricted to unauthenticated F3" }
                        val ok = deletionAccessibilityMatches(nodes, "OK").filter { it.isVisibleToUser && it.isEnabled &&
                            (it.isClickable || it.actionList.any { action -> action.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK }) }
                        check(ok.size == 1) { "Known compatibility dialog must expose exactly one actionable OK" }
                        check(ok.single().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { "Known compatibility dialog OK action failed" }
                        compatibilityDismissed = true
                        return@waitUntil false
                    }
                }
                val meaningful = candidate != null && if (copyBaseline) {
                    deletionAccessibilityMatches(nodes, copyLabel).any { it.isVisibleToUser && it.isEnabled }
                } else if (!blockerRequired) {
                    anchorText.isNotBlank() && deletionAccessibilityMatches(nodes, anchorText).any {
                        it.packageName?.toString() == instrumentation.targetContext.packageName && it.isVisibleToUser
                    }
                } else {
                    GlobalContext.get().get<AppStateCacheProviderInterface>().synchronization.value.let { it.loading || it.activeRefreshes > 0 } && ownVisible.any { it.text?.isNotBlank() == true || it.contentDescription?.isNotBlank() == true }
                }
                if (ownWindow && meaningful) root = candidate
                ownWindow && meaningful
            }
        } catch (failure: Throwable) {
            val windows = automation.windows
            val diagnosticRoot = automation.rootInActiveWindow
            val diagnosticNodes = deletionAccessibilityNodes(diagnosticRoot)
            val ownNodes = diagnosticNodes.filter { it.packageName?.toString() == instrumentation.targetContext.packageName }
            val rootPackage = diagnosticRoot?.packageName?.toString()
            val rootOwner = when (rootPackage) {
                instrumentation.targetContext.packageName -> "own"
                "android" -> "android"
                "com.android.systemui" -> "systemui"
                "com.android.permissioncontroller", "com.google.android.permissioncontroller" -> "permissioncontroller"
                "com.android.launcher3", "com.google.android.apps.nexuslauncher" -> "launcher"
                "com.android.settings" -> "settings"
                null -> "missing"
                else -> "other"
            }
            fun knownLabel(expected: String) = deletionAccessibilityMatches(diagnosticNodes, expected).isNotEmpty()
            val knownPermission = listOf("Allow", "Don't allow", "While using the app", "Only this time").any { knownLabel(it) }
            val knownAnr = listOf("Wait", "Close app").all { knownLabel(it) }
            val knownCrash = listOf("App info", "Close app").all { knownLabel(it) }
            val keyguard = instrumentation.targetContext.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            val power = instrumentation.targetContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            fun classKind(node: android.view.accessibility.AccessibilityNodeInfo?) = when (node?.className?.toString()) {
                "android.widget.FrameLayout" -> "frame"
                "android.widget.LinearLayout" -> "linear"
                "android.widget.TextView" -> "text"
                "android.widget.Button" -> "button"
                "android.widget.EditText" -> "edit"
                "android.view.View" -> "view"
                null -> "missing"
                else -> "other"
            }
            val rootBounds = Rect()
            diagnosticRoot?.getBoundsInScreen(rootBounds)
            val metrics = instrumentation.targetContext.resources.displayMetrics
            val fullScreen = rootBounds.left <= 0 && rootBounds.top <= 0 && rootBounds.right >= metrics.widthPixels && rootBounds.bottom >= metrics.heightPixels
            val knownCompatibility = listOf("This app was built for an older version of Android", "Check for update", "OK").any { knownLabel(it) }
            if (argument("role", "") == "accessibility-probe" &&
                (hasTagNow("onboarding-skip") || hasTagNow("orbit-enter")) &&
                !hasTagNow("lb-secrets") && deletionCachedMember() == null) {
                try {
                    val screenshot = checkNotNull(automation.takeScreenshot()) { "F3 screenshot unavailable" }
                    val file = java.io.File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "test50-f3-window.png")
                    java.io.FileOutputStream(file).use { check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                    screenshot.recycle()
                    marker("ANDROID_TEST50_F3_FAILURE_SCREENSHOT_READY")
                } catch (_: Throwable) { marker("ANDROID_TEST50_F3_FAILURE_SCREENSHOT_UNAVAILABLE") }
            }
            val info = automation.serviceInfo
            marker("ANDROID_TEST50_A11Y_READINESS_FAILED rootOwner=$rootOwner rootClass=${classKind(diagnosticRoot)} descendantClasses=${diagnosticNodes.groupingBy { classKind(it) }.eachCount()} fullScreen=$fullScreen knownCompatibility=$knownCompatibility screenInteractive=${power.isInteractive} keyguardLocked=${keyguard.isKeyguardLocked} deviceLocked=${keyguard.isDeviceLocked} knownPermission=$knownPermission knownAnr=$knownAnr knownCrash=$knownCrash capabilities=${info.capabilities} flags=${info.flags} rootPresent=${diagnosticRoot != null} rootPackageMatch=${diagnosticRoot?.packageName?.toString() == instrumentation.targetContext.packageName} rootVisible=${diagnosticRoot?.isVisibleToUser == true} childCount=${diagnosticRoot?.childCount ?: 0} ownDescendants=${ownNodes.size} visibleOwnDescendants=${ownNodes.count { it.isVisibleToUser }} copyVisibleCount=${deletionAccessibilityMatches(diagnosticNodes, copyLabel).count { it.isVisibleToUser && it.isEnabled }} anchorNonblank=${anchorText.isNotBlank()} anchorManualText=${diagnosticNodes.count { anchorText.isNotBlank() && it.text?.toString() == anchorText }} anchorManualDescription=${diagnosticNodes.count { anchorText.isNotBlank() && it.contentDescription?.toString() == anchorText }} anchorManualResource=${diagnosticNodes.count { anchorText.isNotBlank() && it.viewIdResourceName == anchorText }} anchorProviderCount=${if (anchorText.isNotBlank()) diagnosticRoot?.findAccessibilityNodeInfosByText(anchorText)?.size ?: 0 else 0} windows=${windows.size} state=${windows.take(16).map { 'i' + it.id.toString() + 't' + it.type + 'a' + it.isActive + 'f' + it.isFocused + 'r' + (it.root != null) }.joinToString(",")}")
            throw failure
        }
        return checkNotNull(root) { "Meaningful native accessibility window did not become ready" }
    }

    /** Capture from this actor's visible Show before a modal hides background accessibility. */
    private fun captureDeletionCopyBounds(): Rect {

        try {
            return composeRule.lbCaptureCopyBounds(checkNotNull(composeRule.lbScope().selectedPass))
        } catch (failure: Throwable) {
            marker("ANDROID_TEST50_COPY_TARGET_FAILED type=${failure.javaClass.simpleName}")
            throw failure
        }
    }

    /** Physical touch uses the verified baseline selector bounds, never a hidden semantics action. */
    private fun verifyDeletionOverlayBlocksCopy(secretValue: String, bounds: Rect) {

        composeRule.lbAssertHeldReadPrivacy(checkNotNull(composeRule.lbScope().selectedPass), secretValue, bounds)
    }

    private fun verifyDeletionReveal(expected: String) {

        composeRule.lbAssertValue(checkNotNull(composeRule.lbScope().selectedPass), expected)
    }

    @Test
    fun createVaultForTest50() {

        composeRule.orbitEnterVaultName(argument("vaultName", ""))
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(180_000)
        createDeletionSecret()
        marker("ANDROID_TEST50_SECRET1_READY")
        marker("ANDROID_TEST50_INITIATOR_SETUP_READY")
    }

    @Test
    fun createSecretForTest50() {

        composeRule.lbWaitMain()
        createDeletionSecret()
        marker("ANDROID_TEST50_SECRET_CREATED_${argument("cycle", "0")}_${argument("step", "create")}")
    }

    private fun createDeletionSecret() {

        val name = argument("secretName", "")
        val value = argument("secretValue", "")
        require(name.isNotBlank() && value.isNotBlank())
        composeRule.lbCreatePlain(name, value)
        waitForDeletionRow(name, 180_000)
    }

    @Test
    fun approveJoinForTest50() {

        composeRule.lbWaitMain()
        val head = composeRule.lbIncoming("Join")
        composeRule.lbDecide(head, true)
        marker("ANDROID_TEST50_JOIN_APPROVED_${argument("joinTarget", "ios").uppercase()}")
    }

    private fun hasTagNow(tag: String): Boolean = runCatching {
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    /**
     * The recovery alert can disappear as soon as the refreshed claim is no
     * longer pending, while the native accept call is still flushing its
     * signed approval and sync event.  FFI calls are serialized by Core, so a
     * state read here is an event-driven barrier: it cannot complete until the
     * preceding acceptRecover call has released the FFI lock.  This keeps the
     * instrumentation process alive without adding an arbitrary delay.
     */
    private fun awaitNativeAcceptCompletion(cycle: String, step: String) {
        val stateResult = runCatching { MetaSecretNative.getState() }
        Log.i(
            "MetaSecretE2E",
            "E2E: ANDROID_NATIVE_ACCEPT_RECOVER_FLUSHED cycle=$cycle step=$step " +
                "stateBytes=${stateResult.getOrNull()?.length ?: -1} " +
                "stateSuccess=${stateResult.getOrNull()?.contains("\"success\":true") == true}",
        )
        stateResult.getOrElse { throw it }
    }

    private fun revealAcceptedSecret(
        secretName: String,
        cycle: String,
        step: String,
        expectedSecretValue: String? = null,
    ) {

        marker("ANDROID_SHOW_ACTION_AFTER_APPROVAL_${cycle}_$step")
        composeRule.lbRevealAccepted(secretName, expectedSecretValue)
        if (expectedSecretValue != null) marker("ANDROID_RECOVERY_SECRET_VALUE_VERIFIED_${cycle}_$step")
    }

    private fun waitForPrimaryAction(secretName: String, expected: String) {

        composeRule.lbWaitPrimary(secretName, expected)
    }

    private fun waitForPrimaryActionExact(secretName: String, expected: String) {

        composeRule.lbWaitPrimary(secretName, expected)
    }

    private fun showAcceptedSecretIfNeeded(secretName: String, cycle: String, step: String) {

        val id = composeRule.lbSecret(secretName)
        composeRule.waitUntil(180_000) { composeRule.lbActionText(id) in setOf("Show", "Recover") }
        if (composeRule.lbActionText(id) == "Show") {
            marker("ANDROID_STALE_SHOW_ACTION_${cycle}_$step consuming previously accepted claim")
            composeRule.lbShow(secretName)
            composeRule.lbHide(id)
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

    private fun clickIfPresent(tag: String, timeout: Long) {
        runCatching {
            composeRule.waitForTag(tag, timeout)
            composeRule.onNodeWithTag(tag).performClick()
        }
    }

    private fun waitForRecoveryResolutionOrApprove(timeoutMillis: Long): Boolean {

        val head = checkNotNull(composeRule.lbScope().incoming)
        composeRule.lbIncomingTerminal(head)
        return false
    }

    private fun argument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun configuredSecretNames(): List<String> {
        val expectedNames = argument("expectedSecretNames", "")
            .split(',')
            .filter { it.isNotBlank() }
        if (expectedNames.isNotEmpty()) return expectedNames
        val raw = argument("secretConfig", "")
        if (raw.isBlank()) {
            val scalarNames = argument("secretNames", "").split(',').filter { it.isNotBlank() }
            return scalarNames.ifEmpty { listOf(argument("secretName", "test-secret")) }
        }
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return listOf(argument("secretName", "test-secret"))
        val names = mutableListOf<String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            names += json.getJSONObject(key).getString("name")
        }
        return names.ifEmpty { listOf(argument("secretName", "test-secret")) }
    }

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String,
        timeoutMillis: Long,
    ) {
        // Tags inside merged semantic containers (for example the revealed
        // secret value inside ShowSecretDialog) are only visible in the
        // unmerged tree. Keep this helper consistent with the successful
        // Android recovery tests and avoid waiting forever for a tag that is
        // already present but hidden by semantics merging.
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
