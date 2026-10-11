package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visible Android Join approver and redistribution checks for UI E2E Test #31. */
@RunWith(AndroidJUnit4::class)
class Case31AndroidJoinApproverTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun approvePendingJoinForTest31() {
        val target = argument("joinTarget", "ios").uppercase()

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)

        marker("ANDROID_TEST31_JOIN_APPROVED_$target")
    }

    /** Test #34 keeps Android as the sole approver of the iOS second Join. */
    @Test
    fun approvePendingJoinForTest34() {
        val target = argument("joinTarget", "ios").uppercase()

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)

        marker("ANDROID_TEST34_JOIN_APPROVED_$target")
    }

    /**
     * Test #46 keeps Android as the visible approver for iOS's initial Join
     * and Web's later re-Join. The phase makes both persisted transitions
     * unambiguous in one long scenario.
     */
    @Test
    fun approvePendingJoinForTest46() {
        val target = argument("joinTarget", "ios").uppercase()
        val phase = argument("cycle", "1")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)

        marker("ANDROID_TEST46_JOIN_APPROVED_${target}_$phase")
    }

    /** Test #34 boundary: Android must expose iOS-created Secret3 before recovery. */
    @Test
    fun assertSecretVisibleForTest34() {
        val secretName = argument("secretName", "test34-secret3")
        val cycle = argument("cycle", "18")

        openSecrets()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_TEST34_SECRET_VISIBLE_$cycle")
    }

    @Test
    fun assertSecretVisibleForTest31() {
        val secretName = argument("secretName", "test31-secret3")
        val cycle = argument("cycle", "18")

        openSecrets()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_TEST31_SECRET_VISIBLE_$cycle")
    }

    private fun openSecrets() {
        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
    }

    private fun argument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun clickIfPresent(tag: String, timeoutMillis: Long) {
        try {
            composeRule.waitForTag(tag, timeoutMillis)
            composeRule.onNodeWithTag(tag).performClick()
        } catch (_: Throwable) {
            // The persisted app may already be past onboarding.
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
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
