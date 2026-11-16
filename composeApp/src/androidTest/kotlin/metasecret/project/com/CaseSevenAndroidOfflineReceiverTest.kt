package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Test #7 uses Android as the receiver that is offline while a recovery is
 * requested. The setup test creates the shared vault and returns after the
 * offline boundary marker. The orchestrator can then stop the target app
 * without killing an active instrumentation session. A second instrumentation
 * invocation starts a fresh Compose rule after the application is launched
 * again and verifies that the completed request did not become a stale alert.
 */
@RunWith(AndroidJUnit4::class)
class CaseSevenAndroidOfflineReceiverTest {
    private val canonicalLaunch = LiveboardCanonicalLaunchBoundary()
    val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule
    val activityAndCanonicalBoundary: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(canonicalLaunch).around(composeRule)

    @Test
    fun createVaultThenWaitForOfflineRecovery() {
        val vaultName = instrumentationArgument("vaultName", "test@test.ru")
        val secretName = instrumentationArgument("secretName", "test-secret")
        val secretValue = instrumentationArgument("secretValue", "$secretName-value")
        val approvalCoordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(180_000)

        composeRule.lbCreatePlain(secretName, secretValue)
        marker("ANDROID_INITIATOR_READY")

        // Android approves Web and then iOS joins before this device goes
        // offline. This leaves the final vault in the required 2-of-3 state.
        approvePendingJoin("ANDROID_WEB_JOIN_APPROVED")
        waitForApproval(approvalCoordinatorUrl, "android-ios-join", 0)
        approvePendingJoin("ANDROID_IOS_JOIN_APPROVED")

        composeRule.lbSecrets()
        composeRule.lbSecret(secretName, 180_000)
        composeRule.lbWait("lb-primary-${composeRule.lbSecret(secretName)}")
        marker("ANDROID_OFFLINE_READY")

        // Return before the orchestrator force-stops the target application.
        // Stopping the target package also terminates an instrumentation
        // session that is still running inside that package. The post-restart
        // assertion is launched separately after Web → iOS recovery completes.
    }

    @Test
    fun assertNoStaleRecoveryAlertAfterOfflineRestart() {
        val secretName = instrumentationArgument("secretName", "test-secret")
        marker("ANDROID_STALE_ALERT_ASSERTION_STARTED")
        val passId = composeRule.lbSecret(secretName, 180_000)
        val selected = composeRule.lbWaitCanonicalSecret(passId, secretName)
        composeRule.waitUntil(180_000) {
            val current = lbReadCanonical(selected.scope) ?: return@waitUntil false
            val afterLaunch = lbAfterLaunch(current, canonicalLaunch.beforeFrame)
            val noActive = lbNoActiveRecovery(current, selected)
            marker("ANDROID_STALE_ALERT_CLAIM_STATE cachedAttempt=${current.frame.attempt} afterLaunch=$afterLaunch noActiveSelectedPass=$noActive")
            afterLaunch && noActive
        }
        composeRule.lbHome()
        composeRule.waitUntil(30_000) {
            val current = lbReadCanonical(selected.scope) ?: return@waitUntil false
            lbAfterLaunch(current, canonicalLaunch.beforeFrame) && lbNoActiveRecovery(current, selected) &&
                composeRule.lbIncomingCount() == 0 && composeRule.lbVisibleIncoming() == null
        }
        marker("ANDROID_STALE_ALERT_ABSENT")
    }

    @Test
    fun handleRemainingRecoveryCycles() {
        val secretName = instrumentationArgument("secretName", "test-secret")
        val coordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )
        marker("ANDROID_REMAINING_RECOVERY_READY")
        composeRule.lbSecret(secretName, 180_000)

        recoveryCyclePlan(coordinatorUrl)
            .filter { it.number > 1 }
            .forEach { cycle ->
                val ownSecret = cycle.senderSecrets["android"] ?: return@forEach
                waitForApproval(coordinatorUrl, "android-sender", cycle.number)
                requestRecovery(ownSecret)
                marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle.number}")

                if (cycle.approvals.any { it.platform == "android" }) {
                    closeShowSecretDialog()
                }

                cycle.approvals.forEachIndexed { index, approval ->
                    if (approval.platform != "android") return@forEachIndexed
                    val step = index + 1
                    waitForApproval(coordinatorUrl, "android-approve-$step", cycle.number)
                    approveIncomingRecovery(approval.secret)
                    marker("ANDROID_APPROVED_INCOMING_${cycle.number}_$step")
                }

                waitForApproval(coordinatorUrl, "android-show", cycle.number)
                revealAndClose(ownSecret, reopenClaim = cycle.approvals.any { it.platform == "android" })
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle.number}")
                marker("ANDROID_RECOVERY_CLOSED_${cycle.number}")
            }
        marker("ANDROID_REMAINING_RECOVERY_DONE")
    }

    private data class RecoveryCycle(
        val number: Int,
        val senderSecrets: Map<String, String>,
        val approvals: List<RecoveryApproval>,
    )

    private data class RecoveryApproval(val platform: String, val secret: String)

    private fun recoveryCyclePlan(coordinatorUrl: String): List<RecoveryCycle> {
        val raw = readCoordinatorScenario(coordinatorUrl)
        val plan = raw.optJSONArray("recoveryPlan") ?: JSONArray()
        val defaultSecret = instrumentationArgument("secretName", "test-secret")
        return (0 until plan.length()).mapNotNull { index ->
            val cycle = plan.optJSONObject(index) ?: return@mapNotNull null
            val senderSecrets = buildMap {
                cycle.optJSONObject("senderSecrets")?.keys()?.forEach { platform ->
                    put(platform, cycle.optJSONObject("senderSecrets")?.optString(platform, defaultSecret) ?: defaultSecret)
                }
                cycle.optJSONArray("senders")?.let { senders ->
                    for (senderIndex in 0 until senders.length()) {
                        val sender = senders.opt(senderIndex)
                        if (sender is JSONObject) {
                            put(sender.optString("platform"), sender.optString("secret", defaultSecret))
                        } else if (sender != null) {
                            put(sender.toString(), defaultSecret)
                        }
                    }
                }
            }
            val approvals = cycle.optJSONArray("approvals")?.let { values ->
                (0 until values.length()).mapNotNull { approvalIndex ->
                    val approval = values.opt(approvalIndex)
                    if (approval is JSONObject) {
                        RecoveryApproval(
                            approval.optString("platform", approval.optString("approver")),
                            approval.optString("secret", defaultSecret),
                        )
                    } else if (approval != null) {
                        RecoveryApproval(approval.toString(), defaultSecret)
                    } else null
                }
            } ?: emptyList()
            RecoveryCycle(cycle.optInt("number", index + 1), senderSecrets, approvals)
        }
    }

    private fun readCoordinatorScenario(coordinatorUrl: String): JSONObject {
        val deadline = System.currentTimeMillis() + 30_000
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val connection = URL("$coordinatorUrl/scenario").openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return JSONObject(body)
            } catch (error: Throwable) {
                lastError = error
                Thread.yield()
            }
        }
        error("Unable to read coordinator scenario: ${lastError?.message}")
    }

    private fun requestRecovery(secretName: String) {
        marker("ANDROID_PRIMARY_ACTION_CLICK_$secretName")
        composeRule.lbRequestRecovery(secretName)

    }

    private fun approveIncomingRecovery(secretName: String) {
        composeRule.lbDecide(composeRule.lbIncoming("Recover", secretName), true)

    }

    private fun closeShowSecretDialog() {
        composeRule.lbCloseCurrentRevealOrPending()

    }

    private fun revealAndClose(secretName: String, reopenClaim: Boolean) {
        // Both old presentations now use an explicit current canonical Show, never another Recover.
        val shown = composeRule.lbRevealAccepted(secretName)
        composeRule.lbHide(shown)

    }

    private fun approvePendingJoin(resultMarker: String) {
        marker("ANDROID_OPENING_DEVICES_FOR_JOIN_$resultMarker")
        composeRule.lbHome()
        composeRule.lbDecide(composeRule.lbIncoming("Join"), true)
        marker("ANDROID_JOIN_APPROVAL_DISPATCHED_$resultMarker")
        marker(resultMarker)

    }

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: Int) {
        composeRule.waitUntil(240_000) {
            runCatching {
                val connection = URL(
                    "$coordinatorUrl/approval?platform=$platform&cycle=$cycle",
                ).openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.inputStream.bufferedReader().use { it.readText() == "allowed" }
                    .also { connection.disconnect() }
            }.getOrDefault(false)
        }
    }

    private fun clickIfPresent(tag: String, timeoutMillis: Long) {
        try {
            composeRule.waitForTag(tag, timeoutMillis)
            composeRule.onNodeWithTag(tag).performClick()
        } catch (_: Throwable) {
            // The simulator may retain onboarding state between runs.
        }
    }

    private fun instrumentationArgument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            runCatching {
                onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            runCatching {
                onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }.getOrDefault(false)
        }
    }
}
