package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
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

/**
 * Android-initiator fixture for Test #23. The orchestrator keeps the lifecycle
 * gates on the host and invokes these small, stateful UI actions in separate
 * instrumentation processes. The app is installed with `-r`, so its vault
 * remains between invocations while each action still has an explicit marker.
 */
@RunWith(AndroidJUnit4::class)
class Case23AndroidInitiatorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultForTest23() {
        val vaultName = argument("vaultName", "test23@test.ru")
        val secretName = argument("secretName", "test23-secret1")
        val secretValue = argument("secretValue", "test23-secret1-value")

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
        composeRule.onNodeWithTag("secret-value-input").performTextInput(secretValue)
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitForTagGone("secret-name-input", 30_000)

        composeRule.onNodeWithTag("secret-primary-action-$secretName")
            .assertTextContains("Show", substring = true)
            .performClick()
        composeRule.waitForTag("revealed-secret-value", 180_000)
        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)

        marker("ANDROID_TEST23_SECRET1_READY")
        marker("ANDROID_TEST23_INITIATOR_SETUP_READY")
    }

    @Test
    fun approvePendingJoinForTest23() {
        val target = argument("joinTarget", "ios")
        val cycle = argument("cycle", "0")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        clickIfPresent("onboarding-skip", 10_000)
        waitForApproval(coordinator, "android-approve-$target-join", cycle)

        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)
        marker("ANDROID_JOIN_APPROVED_${target.uppercase()}")
    }

    @Test
    fun createSecretForTest23() {
        val secretName = argument("secretName", "test23-secret2")
        val secretValue = argument("secretValue", "test23-secret2-value")
        val cycle = argument("cycle", "0")
        val initializeShow = argument("initializeShow", "false") == "true"

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        composeRule.waitForTag("add-secret-fab", 120_000)
        composeRule.onNodeWithTag("add-secret-fab").performClick()
        composeRule.waitForTag("secret-name-input", 30_000)
        composeRule.onNodeWithTag("secret-name-input").performTextInput(secretName)
        composeRule.onNodeWithTag("secret-value-input").performTextInput(secretValue)
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitForTagGone("secret-name-input", 30_000)

        if (initializeShow) {
            composeRule.onNodeWithTag("secret-primary-action-$secretName")
                .assertTextContains("Show", substring = true)
                .performClick()
            composeRule.waitForTag("revealed-secret-value", 60_000)
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
        }
        marker("ANDROID_TEST23_SECRET_CREATED_${cycle}")
    }

    private fun argument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun clickIfPresent(tag: String, timeoutMillis: Long) {
        try {
            composeRule.waitForTag(tag, timeoutMillis)
            composeRule.onNodeWithTag(tag).performClick()
        } catch (_: Throwable) {
            // The app may already be past onboarding on a reused emulator.
        }
    }

    private fun marker(message: String) {
        Log.i("MetaSecretE2E", "E2E: $message")
    }

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: String) {
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            val allowed = runCatching {
                val connection = URL("$coordinatorUrl/approval?platform=$platform&cycle=$cycle")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
            }.getOrNull() == "allowed"
            if (allowed) return
            Thread.sleep(200)
        }
        error("Timed out waiting for orchestrator approval platform=$platform cycle=$cycle")
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
