package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visible Android initiator actions used only by UI E2E Test #26. */
@RunWith(AndroidJUnit4::class)
class Case26AndroidInitiatorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultForTest26() {
        val vaultName = argument("vaultName", "test26@test.ru")
        val secretName = argument("secretName", "test26-secret1")
        val secretValue = argument("secretValue", "test26-secret1-value")

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

        marker("ANDROID_TEST26_SECRET1_READY")
        marker("ANDROID_TEST26_INITIATOR_SETUP_READY")
    }

    @Test
    fun approvePendingJoinForTest26() {
        val target = argument("joinTarget", "device").uppercase()

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-devices", 60_000)
        composeRule.onNodeWithTag("tab-devices").performClick()
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitForTagGone("alert-join-request", 120_000)
        composeRule.waitForTagGone("pending-device-row", 120_000)

        marker("ANDROID_TEST26_JOIN_APPROVED_$target")
    }

    @Test
    fun showLocalSecretForTest26() {
        val secretName = argument("secretName", "test26-secret1")
        val secretValue = argument("secretValue", "test26-secret1-value")
        val cycle = argument("cycle", "1")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitUntil(180_000) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextContains("Show", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("revealed-secret-value", 60_000)
        composeRule.onNodeWithTag("revealed-secret-value", useUnmergedTree = true)
            .assertTextEquals(secretValue)
        marker("ANDROID_TEST26_LOCAL_SECRET_VALUE_VERIFIED_$cycle")
        marker("ANDROID_TEST26_LOCAL_SECRET_VISIBLE_$cycle")

        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
        composeRule.waitUntil(30_000) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextContains("Show", substring = true)
            }.isSuccess
        }
        marker("ANDROID_TEST26_LOCAL_SECRET_CLOSED_$cycle")
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
