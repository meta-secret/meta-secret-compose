package metasecret.project.com

import android.util.Log
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.accessibilityservice.AccessibilityServiceInfo
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import org.koin.core.context.GlobalContext
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name -> composeRule.waitForTag("secret-row-$name", 180_000) }
        marker("ANDROID_JOIN_READY")
        marker("ANDROID_SECRETS_READY")
    }

    /** Test #40 joins the Web-owned Vault with run-specific readiness markers. */
    @Test
    fun joinWebInitiatedVaultForTest40() {
        val vaultName = argument("vaultName", "test40@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST40_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST40_JOIN_READY")
        marker("ANDROID_TEST40_SECRETS_READY")
    }

    /** Test #43 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest43() {
        val vaultName = argument("vaultName", "test43@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST43_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST43_JOIN_READY")
        marker("ANDROID_TEST43_SECRETS_READY")
    }

    /** Test #46 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest46() {
        val vaultName = argument("vaultName", "test46@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST46_INITIAL_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST46_INITIAL_JOIN_READY")
        marker("ANDROID_TEST46_INITIAL_SECRETS_READY")
    }

    /** Test #47 joins the Web-owned Vault before Android creates Secret2. */
    @Test
    fun joinWebInitiatedVaultForTest47() {
        val vaultName = argument("vaultName", "test47@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST47_INITIAL_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST47_INITIAL_JOIN_READY")
        marker("ANDROID_TEST47_INITIAL_SECRETS_READY")
    }

    @Test
    fun joinIosInitiatedVaultForTest39() {
        val vaultName = argument("vaultName", "test39@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST39_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST39_JOIN_READY")
        marker("ANDROID_TEST39_SECRETS_READY")
    }

    /** Test #42 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest42() {
        val vaultName = argument("vaultName", "test42@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST42_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST42_JOIN_READY")
        marker("ANDROID_TEST42_SECRETS_READY")
    }

    /** Test #45 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest45() {
        val vaultName = argument("vaultName", "test45@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST45_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
        }
        marker("ANDROID_TEST45_JOIN_READY")
        marker("ANDROID_TEST45_SECRETS_READY")
    }

    /** Test #49 joins the iOS-owned Vault after Web has created Secret2. */
    @Test
    fun joinIosInitiatedVaultForTest49() {
        val vaultName = argument("vaultName", "test49@test.ru")
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_TEST49_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name ->
            composeRule.waitForTag("secret-row-$name", 180_000)
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

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()
        marker("ANDROID_JOIN_REQUEST_SENT")
        configuredSecretNames().forEach { name -> composeRule.waitForTag("secret-row-$name", 180_000) }
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
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_TEST33_SECRET_VISIBLE_$cycle")
    }

    @Test
    fun rejectFourthDeviceAndRegisterNewVault() {
        val vaultName = argument("vaultName", "test21@test.ru")
        val retryVaultName = argument("retryVaultName", "test21-retry@test.ru")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("email-confirmation-join", 60_000)
        composeRule.onNodeWithTag("email-confirmation-join").performClick()

        composeRule.waitForTag("email-confirmation-error", 120_000)
        composeRule.onNodeWithTag("email-confirmation-error", useUnmergedTree = true)
            .assertTextEquals("This vault already has the maximum of 3 devices.")
        marker("ANDROID_FOURTH_DEVICE_REJECTED")
        waitForApproval(
            argument("approvalCoordinatorUrl", "http://10.0.2.2:5180"),
            "android-rejection-screenshot",
            "1",
        )

        composeRule.onNodeWithTag("email-confirmation-error-reset").performClick()
        composeRule.waitForTag("signin-email-manual", 60_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(retryVaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("tab-secrets", 120_000)
        marker("ANDROID_FOURTH_DEVICE_RETRY_REGISTERED")
    }

    @Test
    fun runNetworkLossCycles() {
        val role = argument("networkRole", "")
        val cycleNumbers = argument("networkCycles", "")
            .split(',')
            .filter { it.isNotBlank() }
        val secretName = argument("secretName", "test-secret")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        check(role in setOf("sender", "offline-receiver", "observer")) {
            "Unsupported network-loss role: $role"
        }
        check(cycleNumbers.isNotEmpty()) { "No network-loss cycles configured" }
        Log.i(
            "MetaSecretE2E",
            "E2E: ANDROID_NETWORK_CONFIG role=$role cycles=${cycleNumbers.joinToString(",")}",
        )
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        // Let the orchestrator release the first request only after Compose
        // has rendered the secret row on this emulator.
        marker("ANDROID_NETWORK_READY")

        cycleNumbers.forEach { cycle ->
            when (role) {
                "sender" -> {
                    waitForApproval(coordinator, "android-sender-1", cycle)
                    showAcceptedSecretIfNeeded(secretName, cycle, "1")
                    waitForPrimaryAction(secretName, "Recover")
                    composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
                    composeRule.waitForTag("show-secret-dialog", 30_000)
                    marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_1")
                    // Do not leave the request dialog open while waiting for
                    // the explicit Show phase. An accepted socket event can
                    // otherwise reveal the secret through that same dialog.
                    composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                    composeRule.waitForTagGone("show-secret-dialog", 30_000)
                    waitForApproval(coordinator, "android-show-1", cycle)
                    revealAcceptedSecret(secretName, cycle, "1")
                    marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_1")
                    composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                    composeRule.waitForTagGone("revealed-secret-value", 30_000)
                    waitForPrimaryAction(secretName, "Recover")
                    marker("ANDROID_RECOVERY_CLOSED_${cycle}_1")
                }

                "offline-receiver" -> {
                    composeRule.waitForTag("recovery-request-badge-$secretName", 180_000)
                    composeRule.waitForTag("open-recovery-request-$secretName", 180_000)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle}_1")
                    marker("ANDROID_NETWORK_LOSS_READY_${cycle}")
                    waitForApproval(coordinator, "android-offline-attempt-1", cycle)
                    composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
                    composeRule.waitForTag("alert-recovery-request", 30_000)
                    composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
                    marker("ANDROID_OFFLINE_APPROVE_CLICKED_${cycle}")
                    waitForApproval(coordinator, "android-offline-online-1", cycle)
                    // The failed request may be retried automatically as soon
                    // as the transport comes back. In that case the alert is
                    // already gone and there must not be a second click. If
                    // the action is still exposed, click it exactly once.
                    val approveStillVisible = waitForRecoveryResolutionOrApprove(120_000)
                    if (approveStillVisible) {
                        composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
                    }
                    composeRule.waitUntil(120_000) {
                        composeRule.onAllNodes(
                            hasTestTag("alert-recovery-request"),
                            useUnmergedTree = true,
                        ).fetchSemanticsNodes().isEmpty() && composeRule.onAllNodes(
                            hasTestTag("alert-recovery-request-processing"),
                            useUnmergedTree = true,
                        ).fetchSemanticsNodes().isEmpty()
                    }
                    marker("ANDROID_APPROVED_AFTER_RECONNECT_${cycle}_1")
                }

                "observer" -> {
                    composeRule.waitForTag("recovery-request-badge-$secretName", 180_000)
                    composeRule.waitForTag("open-recovery-request-$secretName", 180_000)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle}_1")
                    waitForApproval(coordinator, "android-observer-finish-1", cycle)
                    composeRule.waitForTagGone("recovery-request-badge-$secretName", 120_000)
                    composeRule.waitForTagGone("open-recovery-request-$secretName", 120_000)
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
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        waitForApproval(coordinator, "android-sender-1", cycle)
        waitForPrimaryAction(secretName, "Recover")
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("show-secret-dialog", 30_000)
        marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_1")
        // Keep the instrumentation alive until the native call has flushed
        // the claim. The orchestrator releases this gate after observing the
        // cycle-specific native log marker.
        waitForApproval(coordinator, "android-request-persisted", cycle)
        Log.i("MetaSecretE2E", "E2E: ANDROID_SENDER_OFFLINE_BOUNDARY_$cycle")
    }

    @Test
    fun showAcceptedRecoveryAfterOffline() {
        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        waitForPrimaryAction(secretName, "Show")
        waitForApproval(coordinator, "android-show-1", cycle)
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("revealed-secret-value", 180_000)
        marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_1")
        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
        waitForPrimaryAction(secretName, "Recover")
        marker("ANDROID_RECOVERY_CLOSED_${cycle}_1")
    }

    @Test
    fun showLocalSecret() {
        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        // A fresh instrumentation process can relaunch the app on its
        // onboarding screen even though the vault credentials are persisted.
        // Follow the same UI path as the join test before touching the native
        // API, so the app manager is initialized by the normal splash flow.
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        val stateResult = runCatching { MetaSecretNative.getState() }
        val localShowResult = runCatching { MetaSecretNative.showLocalSecret(secretName) }
        val localShowSucceeded = localShowResult.getOrNull()?.contains("\"success\":true") == true
        Log.i(
            "MetaSecretE2E",
            "E2E: ANDROID_LOCAL_SHOW_NATIVE_STATE cycle=$cycle "
                + "stateBytes=${stateResult.getOrNull()?.length ?: -1} "
                + "stateHasSecretName=${stateResult.getOrNull()?.contains(secretName) == true} "
                + "showBytes=${localShowResult.getOrNull()?.length ?: -1} "
                + "showSuccess=$localShowSucceeded",
        )
        // A completed JOIN leaves the app on the Devices tab. Each direct
        // Show invocation is a fresh instrumentation process, so select the
        // Secrets tab explicitly before waiting for the row.
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        waitForPrimaryAction(secretName, "Show")
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("revealed-secret-value", 30_000)
        marker("ANDROID_LOCAL_SECRET_VISIBLE_$cycle")
        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
        waitForPrimaryAction(secretName, "Show")
        marker("ANDROID_LOCAL_SECRET_CLOSED_$cycle")
    }

    private fun showLocalSecretBatch(secretName: String, cycles: List<String>) {
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        cycles.forEach { cycle ->
            composeRule.waitForTag("secret-row-$secretName", 180_000)
            waitForPrimaryAction(secretName, "Show")
            val stateResult = runCatching { MetaSecretNative.getState() }
            val localShowResult = runCatching { MetaSecretNative.showLocalSecret(secretName) }
            Log.i(
                "MetaSecretE2E",
                "E2E: ANDROID_LOCAL_SHOW_NATIVE_STATE cycle=$cycle "
                    + "stateBytes=${stateResult.getOrNull()?.length ?: -1} "
                    + "stateHasSecretName=${stateResult.getOrNull()?.contains(secretName) == true} "
                    + "showBytes=${localShowResult.getOrNull()?.length ?: -1} "
                    + "showSuccess=${localShowResult.getOrNull()?.contains("\"success\":true") == true}",
            )
            composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
            composeRule.waitForTag("revealed-secret-value", 30_000)
            marker("ANDROID_LOCAL_SECRET_VISIBLE_$cycle")
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            waitForPrimaryAction(secretName, "Show")
            marker("ANDROID_LOCAL_SECRET_CLOSED_$cycle")
        }
    }

    @Test
    fun handleRecoveryStep() {
        val secretName = argument("secretName", "test-secret")
        val secretValue = argument("secretValue", "")
        val role = argument("role", "receiver")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val approvalPlatform = argument("approvalPlatform", "")
        val expectedOutcome = argument("expectedOutcome", "")
        val verifyReceiverAlert = argument("verifyReceiverAlert", "false") == "true"
        val receiverRequestAlreadySent = argument("receiverRequestAlreadySent", "false") == "true"
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        val recoveryUiProbe = if (role == "sender") null else RecoveryUiDiagnosticProbe(secretName)

        if (role == "sender") {
            // An earlier receiver approval can leave this sender with an
            // accepted claim and therefore a Show action. Reveal and close it
            // before waiting for Recover and creating the next request.
            showAcceptedSecretIfNeeded(secretName, cycle, step)
            waitForPrimaryActionExact(secretName, "Recover")
            composeRule.waitUntil(30_000) {
                !hasTagNow("show-secret-dialog") &&
                    !hasTagNow("revealed-secret-value") &&
                    !hasTagNow("recovery-request-badge-$secretName") &&
                    !hasTagNow("open-recovery-request-$secretName") &&
                    !hasTagNow("alert-recovery-request") &&
                    !hasTagNow("alert-recovery-request-processing")
            }
            marker("ANDROID_SENDER_UI_READY_${cycle}_$step")
            marker("ANDROID_STEP_CONFIG role=$role cycle=$cycle step=$step approval=$approvalPlatform")
            waitForApproval(coordinator, "android-sender-$step", cycle)
            composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
            composeRule.waitForTag("show-secret-dialog", 30_000)
            marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_$step")
            if (approvalPlatform == "cli") {
                if (expectedOutcome == "membership-removal") {
                    waitForApproval(coordinator, "android-request-committed-$step", cycle)
                }
                composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("show-secret-dialog", 30_000)
                if (expectedOutcome == "membership-removal") {
                    marker("ANDROID_RECOVERY_REQUEST_READY_FOR_REMOVAL_${cycle}_$step")
                    return
                }
                waitForApproval(coordinator, "android-invalidated-$step", cycle)
                waitForPrimaryAction(secretName, "Recover")
                marker("ANDROID_RECOVERY_INVALIDATED_${cycle}_$step")
                return
            }
            waitForApproval(coordinator, "android-show-$step", cycle)
            revealAcceptedSecret(secretName, cycle, step, secretValue.ifBlank { null })
            marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            waitForPrimaryAction(secretName, "Recover")
            marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            return
        }

        if (!receiverRequestAlreadySent) {
            composeRule.waitUntil(30_000) {
                val badgeVisible = hasTagNow("recovery-request-badge-$secretName")
                val openVisible = hasTagNow("open-recovery-request-$secretName")
                val alertVisible = hasTagNow("alert-recovery-request")
                val processingVisible = hasTagNow("alert-recovery-request-processing")
                !badgeVisible && !openVisible && !alertVisible && !processingVisible
            }
        }
        recoveryUiProbe?.capture(composeRule, "receiver-ready", cycle, step)
        marker("ANDROID_RECEIVER_UI_READY_${cycle}_$step")
        marker("ANDROID_STEP_CONFIG role=$role cycle=$cycle step=$step approval=$approvalPlatform")

        composeRule.waitForRecoveryBadgeWithDiagnostics(secretName, 180_000, cycle, step, requireNotNull(recoveryUiProbe))
        // The first responder can close this receiver's request before the
        // second runner reaches the open button. Wait for either the button
        // or the terminal badge disappearance instead of asserting the
        // button at a fixed instant.
        composeRule.waitUntil(30_000) {
            hasTagNow("open-recovery-request-$secretName") || !hasTagNow("recovery-request-badge-$secretName")
        }
        if (!hasTagNow("open-recovery-request-$secretName")) {
            if (!hasTagNow("recovery-request-badge-$secretName")) {
                marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
                return
            }
            throw AssertionError("Android recovery request button did not appear while the request remained pending")
        }
        marker("ANDROID_INCOMING_VISIBLE_${cycle}_$step")
        if (expectedOutcome == "membership-removal") {
            marker("ANDROID_RECOVERY_READY_FOR_REMOVAL_${cycle}_$step")
            return
        }
        if (approvalPlatform == "android" || verifyReceiverAlert) {
            openRecoveryRequestAlert(secretName, cycle, step)
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(
                    hasTestTag("alert-recovery-request").and(
                        hasAnyDescendant(hasText(secretName, substring = true)),
                    ),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }
            marker("ANDROID_INCOMING_ALERT_VISIBLE_${cycle}_$step")
            if (approvalPlatform == "android") {
                waitForApproval(coordinator, "android-approve-$step", cycle)
                composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
                composeRule.waitUntil(120_000) {
                    composeRule.onAllNodes(hasTestTag("alert-recovery-request"), useUnmergedTree = true)
                        .fetchSemanticsNodes().isEmpty() && composeRule.onAllNodes(
                            hasTestTag("alert-recovery-request-processing"), useUnmergedTree = true,
                        ).fetchSemanticsNodes().isEmpty()
                }
                awaitNativeAcceptCompletion(cycle, step)
                marker("ANDROID_APPROVED_INCOMING_${cycle}_$step")
            } else {
                composeRule.onNodeWithTag("alert-dismiss", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("alert-recovery-request", 30_000)
                composeRule.waitForTagGone("alert-recovery-request-processing", 30_000)
                if (!hasTagNow("recovery-request-badge-$secretName") ||
                    !hasTagNow("open-recovery-request-$secretName")
                ) {
                    throw AssertionError(
                        "Android observer request disappeared after local alert dismissal",
                    )
                }
                marker("ANDROID_INCOMING_ALERT_DISMISSED_WITHOUT_DECISION_${cycle}_$step")
                waitForApproval(coordinator, "android-dismiss-$step", cycle)
                composeRule.waitForTagGone("recovery-request-badge-$secretName", 120_000)
                composeRule.waitForTagGone("open-recovery-request-$secretName", 120_000)
                marker("ANDROID_DISMISSED_INCOMING_${cycle}_$step")
            }
        } else {
            waitForApproval(coordinator, "android-dismiss-$step", cycle)
            composeRule.waitForTagGone("recovery-request-badge-$secretName", 120_000)
            composeRule.waitForTagGone("open-recovery-request-$secretName", 120_000)
            marker("ANDROID_DISMISSED_INCOMING_${cycle}_$step")
        }
    }

    /**
     * The badge/button can remain composed for one state frame while the
     * authoritative pending-claim list is refreshed. In that frame
     * MainScreenViewModel correctly refuses to open an obsolete claim, so a
     * single click would be a no-op. Retry the UI event while the control is
     * still present; this is event-driven and deliberately contains no sleep.
     */
    private fun openRecoveryRequestAlert(secretName: String, cycle: String, step: String) {
        val buttonTag = "open-recovery-request-$secretName"
        var attempts = 0
        var lastState: String? = null
        composeRule.waitUntil(60_000) {
            val alertVisible = hasTagNow("alert-recovery-request")
            val approveVisible = hasTagNow("alert-recovery-request-accept")
            val processingVisible = hasTagNow("alert-recovery-request-processing")
            val buttonVisible = hasTagNow(buttonTag)
            val badgeVisible = hasTagNow("recovery-request-badge-$secretName")
            val state = "badge=$badgeVisible button=$buttonVisible alert=$alertVisible " +
                "approve=$approveVisible processing=$processingVisible"
            if (state != lastState) {
                Log.i("MetaSecretE2E", "E2E: ANDROID_OPEN_REQUEST_STATE cycle=$cycle step=$step $state")
                lastState = state
            }
            // Processing is not actionable: the Approve button is intentionally
            // absent while Core handles an earlier decision. Wait for it to
            // finish, then retry against the current pending claim.
            if (approveVisible) return@waitUntil true
            if (buttonVisible && attempts < 8) {
                attempts += 1
                Log.i("MetaSecretE2E", "E2E: ANDROID_OPEN_REQUEST_CLICK cycle=$cycle step=$step attempt=$attempts")
                runCatching { composeRule.onNodeWithTag(buttonTag).performClick() }
            }
            false
        }
        if (hasTagNow("alert-recovery-request-processing")) {
            composeRule.waitForTag("alert-recovery-request", 60_000)
        }
    }

    @Test
    fun handleApproveDeclineStep() {
        val secretName = argument("secretName", "test-secret")
        val role = argument("role", "receiver")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val decision = argument("decision", "approve")
        val expectedOutcome = argument("expectedOutcome", "approved")
        val repeatApprove = argument("repeatApprove", "false") == "true"
        val duplicateRecovery = argument("duplicateRecovery", "false") == "true"
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)

        if (role == "sender") {
            waitForApproval(coordinator, "android-sender-$step", cycle)
            showAcceptedSecretIfNeeded(secretName, cycle, step)
            waitForPrimaryAction(secretName, "Recover")
            composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
            composeRule.waitForTag("show-secret-dialog", 30_000)
            marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_$step")
            if (duplicateRecovery) {
                val duplicateSent = runCatching {
                    composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
                    true
                }.getOrDefault(false)
                marker(
                    if (duplicateSent) {
                        "ANDROID_DUPLICATE_RECOVERY_SENT_${cycle}_$step"
                    } else {
                        "ANDROID_DUPLICATE_RECOVERY_SKIPPED_AFTER_GUARD_${cycle}_$step"
                    },
                )
                if (hasTagNow("show-secret-dialog") && hasTagNow("show-secret-close")) {
                    composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                    composeRule.waitForTagGone("show-secret-dialog", 30_000)
                }
                waitForApproval(coordinator, "android-duplicate-finish-$step", cycle)
                revealAcceptedSecret(secretName, cycle, step)
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
                composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("revealed-secret-value", 30_000)
                composeRule.waitForTagGone("show-secret-dialog", 30_000)
                waitForPrimaryAction(secretName, "Recover")
                marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
                return
            }
            // Close the waiting dialog so the sender cannot auto-complete the
            // claim on the first approval. The race test releases Show only
            // after both receiver decisions have been observed.
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            if (expectedOutcome == "approved") {
                waitForApproval(coordinator, "android-show-$step", cycle)
                revealAcceptedSecret(secretName, cycle, step)
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
                composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("revealed-secret-value", 30_000)
                composeRule.waitForTagGone("show-secret-dialog", 30_000)
                waitForPrimaryAction(secretName, "Recover")
                marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            } else {
                waitForApproval(coordinator, "android-declined-$step", cycle)
                if (hasTagNow("show-secret-close")) {
                    composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                }
                composeRule.waitForTagGone("show-secret-dialog", 30_000)
                composeRule.waitForTagGone("revealed-secret-value", 30_000)
                waitForPrimaryAction(secretName, "Recover")
                marker("ANDROID_RECOVERY_NOT_VISIBLE_${cycle}_$step")
                marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            }
            return
        }

        composeRule.waitForTag("recovery-request-badge-$secretName", 180_000)
        if (duplicateRecovery) {
            composeRule.waitUntil(180_000) {
                runCatching {
                    composeRule.onNodeWithTag("recovery-request-badge-$secretName", useUnmergedTree = true)
                        .assertTextContains("1", substring = true)
                }.isSuccess
            }
            marker("ANDROID_SINGLE_ACTIVE_CLAIM_${cycle}_$step")
        }
        composeRule.waitForTag("open-recovery-request-$secretName", 30_000)
        marker("ANDROID_INCOMING_VISIBLE_${cycle}_$step")
        waitForApproval(coordinator, "android-$decision-$step", cycle)
        if (!hasTagNow("open-recovery-request-$secretName")) {
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        marker("ANDROID_OPEN_REQUEST_CLICK_START_${cycle}_$step")
        composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
        marker("ANDROID_OPEN_REQUEST_CLICK_RETURNED_${cycle}_$step")
        // A first terminal response can remove this receiver's alert before
        // the second runner opens it. Wait for either the dialog or the
        // terminal badge disappearance and record a skipped late response.
        try {
            composeRule.waitUntil(30_000) {
                hasTagNow("alert-recovery-request") || !hasTagNow("recovery-request-badge-$secretName")
            }
        } catch (error: Throwable) {
            marker(
                "ANDROID_OPEN_REQUEST_TIMEOUT_${cycle}_$step " +
                    "badge=${hasTagNow("recovery-request-badge-$secretName")} " +
                    "open=${hasTagNow("open-recovery-request-$secretName")} " +
                    "alert=${hasTagNow("alert-recovery-request")} " +
                    "processing=${hasTagNow("alert-recovery-request-processing")}",
            )
            throw error
        }
        if (!hasTagNow("alert-recovery-request")) {
            marker("ANDROID_ACTION_SKIPPED_AFTER_TERMINAL_${cycle}_$step")
            return
        }
        marker("ANDROID_ACTION_STARTED_${cycle}_$step")
        if (decision == "decline") {
            composeRule.onNodeWithTag("alert-recovery-request-decline").performClick()
            composeRule.waitUntil(120_000) {
                composeRule.onAllNodes(hasTestTag("alert-recovery-request"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                    && composeRule.onAllNodes(hasTestTag("alert-recovery-request-processing"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            marker("ANDROID_DECLINED_INCOMING_${cycle}_$step")
        } else {
            composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
            if (repeatApprove && hasTagNow("alert-recovery-request-accept")) {
                marker("ANDROID_REPEAT_APPROVE_SECOND_TAP_SENT_${cycle}_$step")
                composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
            } else if (repeatApprove) {
                marker("ANDROID_REPEAT_APPROVE_SECOND_TAP_SKIPPED_AFTER_DISMISS_${cycle}_$step")
            }
            composeRule.waitUntil(120_000) {
                composeRule.onAllNodes(hasTestTag("alert-recovery-request"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                    && composeRule.onAllNodes(hasTestTag("alert-recovery-request-processing"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
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
        require(name.isNotBlank()) { "Test50 requires a Secret selector" }
        require(role in setOf("requester", "approve", "decline", "observe", "cancel", "show", "sync-observe", "startup-gone"))
        require(outcome in setOf("accepted", "declined", "pending"))
        fun emit(action: String) = marker("ANDROID_DELETION_${action}_${cycle}_$step")
        fun barrier(action: String) = waitForApproval(coordinator, "android-deletion-$action-$step", cycle)
        val readinessStarted = SystemClock.elapsedRealtime()
        var readinessCheckpoint = 0
        fun readiness(stage: String) {
            val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
            val sync = cache.synchronization.value
            val member = deletionCachedMember()
            val selectors = member?.member?.vault?.secrets.orEmpty()
            val checkpoint = ++readinessCheckpoint
            val elapsed = SystemClock.elapsedRealtime() - readinessStarted
            // Log before semantics lookup: a blocked Compose idle query must retain the stage boundary.
            marker("ANDROID_TEST50_READINESS_DIAG cycle=$cycle step=$step role=$role " +
                "checkpoint=$checkpoint stage=$stage elapsedMs=$elapsed phase=enter " +
                "activeRefreshes=${sync.activeRefreshes} loading=${sync.loading} failed=${sync.failed} " +
                "canonicalCount=${selectors.size} canonicalTargetPresent=${selectors.any { it.name == name }}")
            val tabVisible = hasTagNow("tab-secrets")
            val blockerVisible = hasTagNow("vault-sync-blocker")
            val targetRowComposed = hasTagNow("secret-row-$name")
            marker("ANDROID_TEST50_READINESS_DIAG cycle=$cycle step=$step role=$role " +
                "checkpoint=$checkpoint stage=$stage elapsedMs=${SystemClock.elapsedRealtime() - readinessStarted} phase=ui " +
                "tabVisible=$tabVisible blockerVisible=$blockerVisible targetRowComposed=$targetRowComposed")
        }
        if (role == "startup-gone") {
            // Canonical auth can hold on Splash before the main synchronization dialog exists.
            // Pair this UI boundary with the runner's independently observed held HTTP request.
            composeRule.waitUntil(180_000) {
                val sync = GlobalContext.get().get<AppStateCacheProviderInterface>().synchronization.value
                sync.activeRefreshes > 0 && (hasTagNow("vault-sync-blocker") ||
                    (!hasTagNow("tab-secrets") && !hasTagNow("secret-row-$name") &&
                        !hasTagNow("show-secret-dialog") && !hasTagNow("revealed-secret-value")))
            }
            check(!hasTagNow("show-secret-dialog") && !hasTagNow("revealed-secret-value")) {
                "Cold startup retained ephemeral reveal UI"
            }
            emit("READY")
            barrier("action")
            barrier("terminal")
            composeRule.waitForTag("tab-secrets", 180_000)
            composeRule.onNodeWithTag("tab-secrets").performClick()
            composeRule.waitForTagGone("vault-sync-blocker", 180_000)
            assertDeletionTagAbsent("secret-row-$name")
            check(deletionCachedMember()?.member?.vault?.secrets?.none { it.name == name } == true) { "Deleted ID remains in the current cached canonical Secret list" }
            check(!hasTagNow("show-secret-dialog") && !hasTagNow("revealed-secret-value")) {
                "Deleted Secret retained reveal UI after restart"
            }
            emit("TERMINAL")
            return
        }
        readiness("before-tab-visible")
        composeRule.waitForTag("tab-secrets", 60_000)
        readiness("after-tab-visible")
        readiness("before-tab-click")
        composeRule.onNodeWithTag("tab-secrets").performClick()
        readiness("after-tab-click")
        readiness("before-loader-gone")
        composeRule.waitForTagGone("vault-sync-blocker", 180_000)
        readiness("after-loader-gone")
        readiness("before-target-row-ready")
        waitForDeletionRow(name, 180_000)
        readiness("after-target-row-ready")
        readiness("before-canonical-target-present")
        val secretId = deletionCachedMember()?.member?.vault?.secrets?.singleOrNull { it.name == name }?.id
        check(!secretId.isNullOrBlank()) { "Secret selector lacks an unambiguous opaque ID" }
        readiness("after-canonical-target-present")
        readiness("before-id-marker")
        marker("ANDROID_DELETION_ID_${cycle}_$step:$secretId")
        readiness("after-id-marker")
        composeRule.waitForTagGone("secret-deletion-dialog", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
        assertDeletionTagAbsent("deletion-pending-$name")
        assertDeletionTagAbsent("open-deletion-request-$name")
        if (role == "sync-observe") {
            prepareDeletionAccessibility()
            deletionAccessibilityRoot(copyBaseline = false, blockerRequired = false, anchorText = name)
        }
        var baselineCopyBounds: Rect? = null
        if (role == "show" || role == "sync-observe") {
            require(value.isNotBlank()) { "Test50 Show requires an expected value" }
            waitForPrimaryAction(name, "Show")
            composeRule.onNodeWithTag("secret-primary-action-$name").performClick()
            verifyDeletionReveal(value)
            if (role == "sync-observe") baselineCopyBounds = captureDeletionCopyBounds()
        }
        emit("READY")
        if (role == "approve" || role == "decline" ||
            (role == "observe" && argument("expectRequest", "true") == "true")) {
            ensureDeletionTag("open-deletion-request-$name", 180_000)
            emit("REQUEST_VISIBLE")
        }
        if (role != "observe") barrier("action")
        when (role) {
            "requester", "cancel" -> {
                ensureDeletionTag("delete-secret-$name", 30_000)
                composeRule.onNodeWithTag("delete-secret-$name", useUnmergedTree = true).performClick()
                composeRule.waitForTag("secret-deletion-dialog", 30_000)
                if (role == "cancel") {
                    // Cancel currently has no testTag; resolve its shared AppString within this dialog.
                    composeRule.onNode(hasText(GlobalContext.get().get<StringProviderInterface>().getString(AppString.cancel)), useUnmergedTree = true).performClick()
                    composeRule.waitForTagGone("secret-deletion-dialog", 30_000)
                    waitForDeletionRow(name, 30_000)
                    check(!hasTagNow("deletion-pending-$name") && !hasTagNow("open-deletion-request-$name")) {
                        "Cancelled deletion changed visible request state"
                    }
                    emit("CANCELLED")
                    return
                }
                composeRule.onNodeWithTag("confirm-secret-deletion", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("secret-deletion-dialog", 180_000)
                // Local policy removes immediately; remote policy persists Pending before dismissing.
                composeRule.waitUntil(180_000) {
                    !hasTagNow("secret-row-$name") || hasTagNow("deletion-pending-$name") ||
                        deletionCachedMember()?.deletionStates?.any {
                            it.secretId == secretId && it.clientStatus == SecretDeletionStatus.Declined
                        } == true
                }
                emit("SUBMITTED")
            }
            "approve", "decline" -> {
                composeRule.onNodeWithTag("open-deletion-request-$name", useUnmergedTree = true).performClick()
                composeRule.waitForTag("secret-deletion-dialog", 30_000)
                composeRule.onNodeWithTag(
                    if (role == "approve") "confirm-secret-deletion" else "decline-secret-deletion",
                    useUnmergedTree = true,
                ).performClick()
                composeRule.waitForTagGone("secret-deletion-dialog", 180_000)
                composeRule.waitUntil(180_000) {
                    deletionCachedMember()?.deletionStates?.any {
                        it.secretId == secretId && it.clientStatus ==
                            (if (role == "approve") SecretDeletionStatus.Done else SecretDeletionStatus.Declined)
                    } == true || (role == "approve" && !hasTagNow("secret-row-$name"))
                }
                emit("SUBMITTED")
            }
            "observe" -> Unit
            "sync-observe" -> {
                // Runner drives Home/activate; actual onStop/onStart synchronization owns this transition.
                composeRule.waitForTag("vault-sync-blocker", 180_000)
                val overlay = composeRule.onNodeWithTag("vault-sync-blocker", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val reveal = composeRule.onNodeWithTag("revealed-secret-value", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                check(overlay.width >= reveal.width && overlay.height >= reveal.height) {
                    "Synchronization dialog does not cover the reveal"
                }
                check(hasTagNow("vault-sync-loader") || hasTagNow("retry-vault-sync")) {
                    "Synchronization blocker has neither loader nor retry"
                }
                verifyDeletionOverlayBlocksCopy(value, checkNotNull(baselineCopyBounds) { "Verified Copy baseline was not captured before synchronization" })
                emit("SYNC_BLOCKED")
                barrier("sync-release")
                if (hasTagNow("retry-vault-sync")) {
                    composeRule.onNodeWithTag("retry-vault-sync", useUnmergedTree = true).performClick()
                }
                composeRule.waitForTagGone("vault-sync-blocker", 180_000)
            }
            "show" -> {
                // Separate own-refresh probe; passive lifecycle/deletion actors never inject refresh.
                composeRule.runOnIdle { GlobalContext.get().get<MetaSecretSocketHandlerInterface>().refreshAppState() }
                composeRule.waitUntil(180_000) {
                    val sync = GlobalContext.get().get<AppStateCacheProviderInterface>().synchronization.value
                    !sync.loading && sync.activeRefreshes == 0
                }
                check(!GlobalContext.get().get<AppStateCacheProviderInterface>().synchronization.value.failed) {
                    "Own-refresh failed during valid Show"
                }
                composeRule.waitForTagGone("vault-sync-blocker", 180_000)
                verifyDeletionReveal(value)
                composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                composeRule.waitForTagGone("show-secret-dialog", 30_000)
                composeRule.waitForTagGone("revealed-secret-value", 30_000)
                emit("SHOW_VALID")
                return
            }
        }
        barrier("terminal")
        if (outcome == "accepted") {
            assertDeletionTagAbsent("secret-row-$name")
            check(deletionCachedMember()?.member?.vault?.secrets?.none { it.name == name } == true) { "Deleted ID remains in the current cached canonical Secret list" }
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
        } else {
            waitForDeletionRow(name, 30_000)
            if (role == "show" || role == "sync-observe") verifyDeletionReveal(value)
        }
        if (outcome != "pending") {
            assertDeletionTagAbsent("deletion-pending-$name")
            assertDeletionTagAbsent("open-deletion-request-$name")
            composeRule.waitForTagGone("secret-deletion-dialog", 30_000)
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
        composeRule.waitForTagGone("vault-sync-blocker", timeoutMillis)
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
        composeRule.waitUntil(timeoutMillis) { deletionCachedMember()?.member?.vault?.secrets?.any { it.name == name } == true }
        ensureDeletionTag("secret-row-$name", timeoutMillis)
    }

    private fun assertDeletionTagAbsent(tag: String) {
        composeRule.waitForTagGone("vault-sync-blocker", 180_000)
        val found = try { deletionScrollContainer().performScrollToNode(hasTestTag(tag)); true }
        catch (missing: AssertionError) {
            if (missing.message?.startsWith("No node found that matches") == true) false else throw missing
        }
        check(!found) { "Test50 stale Secret/request control remains in the complete semantic list" }
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
            val tag = listOf("onboarding-skip", "signin-email-manual").firstOrNull { hasTagNow(it) }
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
        val copyLabel = strings.getString(AppString.copySecret)
        val blockerLabels = listOf(strings.getString(AppString.vaultSynchronizing), strings.getString(AppString.vaultSyncFailed))
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
                        check(argument("role", "") == "accessibility-probe" && !hasTagNow("tab-secrets") && deletionCachedMember() == null) { "Compatibility handling is restricted to unauthenticated F3" }
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
                    hasTagNow("vault-sync-blocker") && blockerLabels.any { deletionAccessibilityMatches(ownVisible, it).isNotEmpty() }
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
                (hasTagNow("onboarding-skip") || hasTagNow("signin-email-manual")) &&
                !hasTagNow("tab-secrets") && deletionCachedMember() == null) {
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
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val foreground = deletionAccessibilityRoot(copyBaseline = true)
        val copyLabel = GlobalContext.get().get<StringProviderInterface>().getString(AppString.copySecret)
        val targetPackage = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val labels = deletionAccessibilityMatches(deletionAccessibilityNodes(foreground), copyLabel).filter {
            it.isVisibleToUser && it.isEnabled && it.packageName?.toString() == targetPackage
        }
        val depths = mutableListOf<Int>()
        val actionable = labels.mapNotNull { label ->
            val labelBounds = Rect().also { label.getBoundsInScreen(it) }
            check(!labelBounds.isEmpty) { "Visible Copy label has empty screen bounds" }
            var candidate: android.view.accessibility.AccessibilityNodeInfo? = label
            var found: android.view.accessibility.AccessibilityNodeInfo? = null
            for (depth in 0..8) {
                val node = candidate ?: break
                if (node.windowId != foreground.windowId || node.packageName?.toString() != targetPackage) break
                val actionBounds = Rect().also { node.getBoundsInScreen(it) }
                if (node.isVisibleToUser && node.isEnabled && !actionBounds.isEmpty && actionBounds.contains(labelBounds) &&
                    (node.isClickable || node.actionList.any { action -> action.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK })) {
                    found = node
                    depths.add(depth)
                    break
                }
                candidate = node.parent
            }
            found
        }.distinct()
        if (actionable.size != 1) marker("ANDROID_TEST50_COPY_TARGET_FAILED labelCount=${labels.size} actionableCount=${actionable.size} actionDepths=${depths.joinToString(",")}")
        check(actionable.size == 1) { "Visible Show must expose exactly one actionable Copy baseline; count=${actionable.size}" }
        val bounds = Rect()
        actionable.single().getBoundsInScreen(bounds)
        check(!bounds.isEmpty) { "Visible Copy baseline has empty screen bounds" }
        return Rect(bounds)
    }

    /** Physical touch uses the verified baseline selector bounds, never a hidden semantics action. */
    private fun verifyDeletionOverlayBlocksCopy(secretValue: String, bounds: Rect) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val copyLabel = GlobalContext.get().get<StringProviderInterface>().getString(AppString.copySecret)
        val foreground = deletionAccessibilityRoot(copyBaseline = false)
        val foregroundBounds = Rect()
        foreground.getBoundsInScreen(foregroundBounds)
        check(foregroundBounds.contains(bounds.centerX(), bounds.centerY())) { "Verified synchronization foreground does not cover the captured Copy point" }
        val foregroundNodes = deletionAccessibilityNodes(foreground)
        check(foregroundNodes.none { it.text?.toString()?.contains(secretValue) == true || it.contentDescription?.toString()?.contains(secretValue) == true }) {
            "Synchronization foreground exposes plaintext (redacted)"
        }
        check(deletionAccessibilityMatches(foregroundNodes, copyLabel).isEmpty()) {
            "Synchronization foreground exposes Copy control"
        }
        val clipboard = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val sentinel = "test50-blocked-copy-sentinel"
        instrumentation.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("Test50", sentinel)) }
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        try {
            check(automation.injectInputEvent(down, true) && automation.injectInputEvent(up, true)) {
                "Could not inject Copy-blocking window touch"
            }
        } finally { down.recycle(); up.recycle() }
        instrumentation.waitForIdleSync()
        var unchanged = false
        instrumentation.runOnMainSync { unchanged = clipboard.primaryClip?.getItemAt(0)?.text?.toString() == sentinel }
        check(unchanged) { "Synchronization blocker allowed Copy (redacted)" }
        check(hasTagNow("vault-sync-blocker")) { "Synchronization blocker disappeared during Copy probe" }
    }

    private fun verifyDeletionReveal(expected: String) {
        composeRule.waitForTag("revealed-secret-value", 30_000)
        val actual = composeRule.onNodeWithTag("revealed-secret-value", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        check(actual == expected) { "Test50 revealed value differs from expected (redacted)" }
    }

    @Test
    fun createVaultForTest50() {
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(argument("vaultName", ""))
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)
        createDeletionSecret()
        marker("ANDROID_TEST50_SECRET1_READY")
        marker("ANDROID_TEST50_INITIATOR_SETUP_READY")
    }

    @Test
    fun createSecretForTest50() {
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        createDeletionSecret()
        marker("ANDROID_TEST50_SECRET_CREATED_${argument("cycle", "0")}_${argument("step", "create")}")
    }

    private fun createDeletionSecret() {
        val name = argument("secretName", "")
        val value = argument("secretValue", "")
        require(name.isNotBlank() && value.isNotBlank()) { "Test50 requires Secret inputs" }
        composeRule.waitForTag("add-secret-fab", 120_000)
        composeRule.onNodeWithTag("add-secret-fab").performClick()
        composeRule.waitForTag("secret-name-input", 30_000)
        composeRule.onNodeWithTag("secret-name-input").performTextInput(name)
        composeRule.onNodeWithTag("secret-value-input").performTextInput(value)
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTagGone("secret-name-input", 30_000)
        waitForDeletionRow(name, 180_000)
    }

    @Test
    fun approveJoinForTest50() {
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)
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
        var showClicked = false
        composeRule.waitUntil(180_000) {
            if (runCatching {
                    composeRule.onAllNodes(
                        hasTestTag("revealed-secret-value"), useUnmergedTree = true,
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            ) return@waitUntil true

            if (!showClicked && runCatching {
                    composeRule.onNodeWithTag("secret-primary-action-$secretName")
                        .assertTextContains("Show", substring = true)
                }.isSuccess
            ) {
                marker("ANDROID_SHOW_ACTION_AFTER_APPROVAL_${cycle}_$step")
                composeRule.onAllNodes(hasTestTag("show-secret-dialog"), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                    }
                composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
                showClicked = true
            }
            false
        }
        composeRule.waitForTag("revealed-secret-value", 180_000)
        if (expectedSecretValue != null) {
            composeRule.onNodeWithTag("revealed-secret-value", useUnmergedTree = true)
                .assertTextEquals(expectedSecretValue)
            marker("ANDROID_RECOVERY_SECRET_VALUE_VERIFIED_${cycle}_$step")
        }
    }

    private fun waitForPrimaryAction(secretName: String, expected: String) {
        composeRule.waitUntil(180_000) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextContains(expected, substring = true)
            }.isSuccess
        }
    }

    private fun waitForPrimaryActionExact(secretName: String, expected: String) {
        composeRule.waitUntil(180_000) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextEquals(expected)
            }.isSuccess
        }
    }

    private fun showAcceptedSecretIfNeeded(secretName: String, cycle: String, step: String) {
        composeRule.waitUntil(180_000) {
            val action = composeRule.onNodeWithTag("secret-primary-action-$secretName")
            val isRecover = runCatching {
                action.assertTextContains("Recover", substring = true)
            }.isSuccess
            if (isRecover) return@waitUntil true

            val isShow = runCatching {
                action.assertTextContains("Show", substring = true)
            }.isSuccess
            if (!isShow) return@waitUntil false

            marker("ANDROID_STALE_SHOW_ACTION_${cycle}_$step consuming previously accepted claim")
            action.performClick()
            composeRule.waitForTag("revealed-secret-value", 180_000)
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            true
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
        var approveVisible = false
        composeRule.waitUntil(timeoutMillis) {
            val approveNodes = composeRule.onAllNodes(
                hasTestTag("alert-recovery-request-accept"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            val alertNodes = composeRule.onAllNodes(
                hasTestTag("alert-recovery-request"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            val processingNodes = composeRule.onAllNodes(
                hasTestTag("alert-recovery-request-processing"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            approveVisible = approveNodes.isNotEmpty()
            approveVisible || (alertNodes.isEmpty() && processingNodes.isEmpty())
        }
        return approveVisible
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
