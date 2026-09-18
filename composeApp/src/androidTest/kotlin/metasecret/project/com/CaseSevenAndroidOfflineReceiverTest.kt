package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metasecret.core.MetaSecretNative
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
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultThenWaitForOfflineRecovery() {
        val vaultName = instrumentationArgument("vaultName", "test@test.ru")
        val secretName = instrumentationArgument("secretName", "test-secret")
        val secretValue = instrumentationArgument("secretValue", "$secretName-value")
        val approvalCoordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        composeRule.onNodeWithTag("add-secret-fab").performClick()
        composeRule.waitForTag("secret-name-input", 30_000)
        composeRule.onNodeWithTag("secret-name-input").performTextInput(secretName)
        composeRule.waitForTag("secret-value-input", 30_000)
        composeRule.onNodeWithTag("secret-value-input").performTextInput(secretValue)
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_INITIATOR_READY")

        // Android approves Web and then iOS joins before this device goes
        // offline. This leaves the final vault in the required 2-of-3 state.
        approvePendingJoin("ANDROID_WEB_JOIN_APPROVED")
        waitForApproval(approvalCoordinatorUrl, "android-ios-join", 0)
        approvePendingJoin("ANDROID_IOS_JOIN_APPROVED")

        composeRule.onNodeWithTag("tab-secrets").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitForTag("secret-primary-action-$secretName", 180_000)
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
        composeRule.waitForTag("secret-row-$secretName", 180_000)

        // find_claim performs the first post-relaunch sync. Waiting for the
        // explicit "claim not found" response gives the UI a meaningful
        // synchronization point instead of guessing with a timeout/sleep.
        var lastClaimResponse = ""
        composeRule.waitUntil(180_000) {
            lastClaimResponse = runCatching {
                MetaSecretNative.find_claim_by_(secretName)
            }.getOrDefault("")
            Log.i(
                "MetaSecretE2E",
                "E2E: ANDROID_STALE_ALERT_CLAIM_STATE secret=$secretName "
                    + "responseBytes=${lastClaimResponse.length}",
            )
            lastClaimResponse.contains("\"success\":false")
                && lastClaimResponse.contains("Claim has not been found")
        }

        check(lastClaimResponse.contains("Claim has not been found")) {
            "Expected no active recovery claim after restart, got: $lastClaimResponse"
        }
        composeRule.waitForTagGone("recovery-request-badge-$secretName", 30_000)
        composeRule.waitForTagGone("open-recovery-request-$secretName", 30_000)
        composeRule.waitForTagGone("alert-recovery-request", 30_000)
        composeRule.waitForTagGone("alert-recovery-request-processing", 30_000)
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
        composeRule.waitForTag("secret-row-$secretName", 180_000)

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
        val action = composeRule.onNodeWithTag("secret-primary-action-$secretName")
        composeRule.waitUntil(120_000) {
            runCatching {
                action.assertTextContains("Recover", substring = true)
                true
            }.getOrDefault(false)
        }
        marker("ANDROID_PRIMARY_ACTION_CLICK_$secretName")
        action.performClick()
        composeRule.waitForTag("show-secret-dialog", 30_000)
    }

    private fun approveIncomingRecovery(secretName: String) {
        composeRule.waitForTag("recovery-request-badge-$secretName", 180_000)
        composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
        composeRule.waitForTag("alert-recovery-request", 30_000)
        composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
        composeRule.waitUntil(120_000) {
            composeRule.onAllNodes(
                hasTestTag("alert-recovery-request"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty() && composeRule.onAllNodes(
                hasTestTag("alert-recovery-request-processing"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun closeShowSecretDialog() {
        runCatching {
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        }
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
    }

    private fun revealAndClose(secretName: String, reopenClaim: Boolean) {
        if (reopenClaim) {
            val action = composeRule.onNodeWithTag("secret-primary-action-$secretName")
            composeRule.waitUntil(120_000) {
                runCatching {
                    action.assertTextContains("Show", substring = true)
                    true
                }.getOrDefault(false)
            }
            action.performClick()
            composeRule.waitForTag("show-secret-dialog", 30_000)
        }
        composeRule.waitForTag("revealed-secret-value", 120_000)
        closeShowSecretDialog()
    }

    private fun approvePendingJoin(resultMarker: String) {
        marker("ANDROID_OPENING_DEVICES_FOR_JOIN_$resultMarker")
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitUntil(120_000) {
            composeRule.onAllNodes(
                hasTestTag("alert-join-request"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
        marker("ANDROID_JOIN_APPROVAL_DISPATCHED_$resultMarker")
        composeRule.waitUntil(120_000) {
            composeRule.onAllNodes(
                hasTestTag("pending-device-row"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
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
