package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metasecret.core.MetaSecretNative
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CaseOneJoinFromAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun joinApprovedVaultAndWaitForRecoveryCompletion() {
        val vaultName = instrumentationArgument("vaultName", "test@test.ru")
        val secretName = instrumentationArgument("secretName", "test-secret")

        clickIfPresent("onboarding-skip", timeoutMillis = 10_000)
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

        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_MAIN_AFTER_APPROVE")
        marker("ANDROID_SECRET_VISIBLE")

        val recoveryCycles = instrumentationArgument("recoveryCycles", "18").toInt()
        val approvalCycles = instrumentationArgument("androidRecoveryApprovals", "7,8,9,10,11,12,14,16,18")
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .toSet()
        val approvalCoordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )
        val stateDrivenRecoveryWait = instrumentationArgument("stateDrivenRecoveryWait", "false") == "true"

        for (cycle in 1..recoveryCycles) {
            if (stateDrivenRecoveryWait) {
                waitForApproval(approvalCoordinatorUrl, "android-state", cycle)
                marker("ANDROID_RECOVERY_STATE_PENDING_$cycle")
            }
            try {
                composeRule.waitForTag("recovery-request-badge-$secretName", 45_000)
            } catch (failure: Throwable) {
                val secretRowCount = composeRule.onAllNodes(hasTestTag("secret-row-$secretName"), useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                val badgeCount = composeRule.onAllNodes(hasTestTag("recovery-request-badge-$secretName"), useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                val openActionCount = composeRule.onAllNodes(hasTestTag("open-recovery-request-$secretName"), useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                marker(
                    "ANDROID_RECOVERY_BADGE_DIAGNOSTIC_$cycle " +
                        "secretRowCount=$secretRowCount badgeCount=$badgeCount openActionCount=$openActionCount"
                )
                throw failure
            }
            marker("ANDROID_RECOVERY_REQUEST_ALERT_$cycle")
            if (cycle in approvalCycles) {
                waitForApproval(approvalCoordinatorUrl, "android", cycle)
                composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
                composeRule.waitForTag("alert-recovery-request", 30_000)
                composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
                // The dialog is hidden as soon as the acceptance starts.  Do not let the
                // instrumentation process finish until the async core/server operation did.
                composeRule.waitForTag("alert-recovery-request-processing", 30_000)
                composeRule.waitUntil(180_000) {
                    composeRule.onAllNodes(hasTestTag("alert-recovery-request-processing"))
                        .fetchSemanticsNodes()
                        .isEmpty()
                }
                marker("ANDROID_RECOVERY_APPROVE_SUCCESS_$cycle")
            }
            val completedRecoveryClaims = waitForCoreRecoveryCompletion(secretName, cycle)
            marker("ANDROID_RECOVERY_CORE_DONE_$cycle completedRecoveryClaims=$completedRecoveryClaims")
            composeRule.waitUntil(180_000) {
                composeRule.onAllNodes(hasTestTag("recovery-request-badge-$secretName"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
            }
            marker("ANDROID_RECOVERY_REQUEST_CLOSED_$cycle")
        }
    }

    private fun clickIfPresent(tag: String, timeoutMillis: Long) {
        try {
            composeRule.waitForTag(tag, timeoutMillis)
            composeRule.onNodeWithTag(tag).performClick()
        } catch (_: AssertionError) {
            // The shared simulator can retain onboarding state between runs.
        }
    }

    private fun instrumentationArgument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    /**
     * Wait for an authoritative Core state transition before inspecting Compose
     * semantics. A Core read is serialized with the preceding FFI accept call;
     * the cycle-indexed count also ensures an older completed claim cannot
     * satisfy this cycle's barrier.
     */
    private fun waitForCoreRecoveryCompletion(secretName: String, cycle: Int): Int {
        var completedRecoveryClaims = 0
        composeRule.waitUntil(180_000) {
            val response = MetaSecretNative.getState()
            val state = JSONObject(response)
            check(state.getBoolean("success")) {
                "Core getState failed while waiting for Android recovery cycle $cycle"
            }
            completedRecoveryClaims = completedReceiverRecoveryCount(state, secretName)
            completedRecoveryClaims >= cycle
        }
        return completedRecoveryClaims
    }

    private fun completedReceiverRecoveryCount(state: JSONObject, secretName: String): Int {
        val message = state.getJSONObject("message")
        val appState = message.getJSONObject("state")
        val userMemberInfo = appState.getJSONObject("vault")
            .getJSONObject("member")
        val deviceId = userMemberInfo.getJSONObject("member")
            .getJSONObject("member")
            .getJSONObject("userData")
            .getJSONObject("device")
            .getString("deviceId")
        val claims = userMemberInfo.optJSONObject("ssClaims")?.optJSONObject("claims")
            ?: return 0

        var completed = 0
        val claimIds = claims.keys()
        while (claimIds.hasNext()) {
            val claim = claims.getJSONObject(claimIds.next())
            if (claim.optString("distributionType") != "recover" ||
                claim.optString("clientStatus") != "done" ||
                claim.optJSONObject("distClaimId")?.optJSONObject("passId")?.optString("name") != secretName
            ) continue

            val receivers = claim.optJSONArray("receivers") ?: continue
            if ((0 until receivers.length()).any { receivers.optString(it) == deviceId }) {
                completed += 1
            }
        }
        return completed
    }

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: Int) {
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            val result = runCatching {
                val connection = URL("$coordinatorUrl/approval?platform=$platform&cycle=$cycle")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
            }.getOrNull()
            if (result == "allowed") return
            Thread.sleep(200)
        }
        error("Timed out waiting for orchestrator approval for Android recovery cycle $cycle")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMillis: Long) {
        var layoutRaceRetries = 0
        while (true) {
            try {
                waitUntil(timeoutMillis) {
                    onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
                }
                return
            } catch (failure: Throwable) {
                if (layoutRaceRetries >= 2 || !failure.hasComposeMeasureLayoutRace()) throw failure
                layoutRaceRetries += 1
            }
        }
    }

    private fun Throwable.hasComposeMeasureLayoutRace(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is IllegalArgumentException &&
                it.message?.contains("performMeasureAndLayout called during measure layout") == true
        }
}
