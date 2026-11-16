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

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(180_000)

        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST26_SECRET1_READY")
        marker("ANDROID_TEST26_INITIATOR_SETUP_READY")
    }

    @Test
    fun approvePendingJoinForTest26() {
        val target = argument("joinTarget", "device").uppercase()

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbDecide(composeRule.lbIncoming("Join"), true)

        marker("ANDROID_TEST26_JOIN_APPROVED_$target")
    }

    @Test
    fun showLocalSecretForTest26() {
        val secretName = argument("secretName", "test26-secret1")
        val secretValue = argument("secretValue", "test26-secret1-value")
        val cycle = argument("cycle", "1")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        val shownId = composeRule.lbShow(secretName, secretValue)
        marker("ANDROID_TEST26_LOCAL_SECRET_VALUE_VERIFIED_$cycle")
        marker("ANDROID_TEST26_LOCAL_SECRET_VISIBLE_$cycle")

        composeRule.lbHide(shownId)
        composeRule.lbWaitPrimary(secretName, lbLabel(core.AppString.lbShow))
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
