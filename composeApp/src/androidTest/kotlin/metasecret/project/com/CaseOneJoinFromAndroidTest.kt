package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        for (cycle in 1..recoveryCycles) {
            composeRule.waitForTag("alert-recovery-request", 45_000)
            marker("ANDROID_RECOVERY_REQUEST_ALERT_$cycle")
            if (cycle in approvalCycles) {
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
            composeRule.waitUntil(180_000) {
                composeRule.onAllNodes(hasTestTag("alert-recovery-request")).fetchSemanticsNodes().isEmpty()
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

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMillis: Long) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
