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

/** UI-only Secret creation steps for Android's secondary-creator role in Test #25. */
@RunWith(AndroidJUnit4::class)
class Case25AndroidSecondaryCreatorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createSecretForTest25() {
        val secretName = argument("secretName", "test25-secret2")
        val secretValue = argument("secretValue", "test25-secret2-value")
        val cycle = argument("cycle", "0")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST25_SECRET_CREATED_$cycle")
    }

    /** Test #34 uses the same visible Android creator flow with a run-specific marker. */
    @Test
    fun createSecretForTest34() {
        val secretName = argument("secretName", "test34-secret2")
        val secretValue = argument("secretValue", "test34-secret2-value")
        val cycle = argument("cycle", "0")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST34_SECRET_CREATED_$cycle")
    }

    /** Test #40 uses Android as creator B on both sides of the iOS Join. */
    @Test
    fun createSecretForTest40() {
        val secretName = argument("secretName", "test40-secret2")
        val secretValue = argument("secretValue", "test40-secret2-value")
        val cycle = argument("cycle", "0")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST40_SECRET_CREATED_$cycle")
    }

    /** Test #43 creates Secret2 on Android before iOS joins. */
    @Test
    fun createSecretForTest43() {
        val secretName = argument("secretName", "test43-secret2")
        val secretValue = argument("secretValue", "test43-secret2-value")
        val cycle = argument("cycle", "2")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST43_SECRET_CREATED_$cycle")
    }

    /** Test #46 creates Secret2 on Android before iOS joins. */
    @Test
    fun createSecretForTest46() {
        val secretName = argument("secretName", "test46-secret2")
        val secretValue = argument("secretValue", "test46-secret2-value")
        val cycle = argument("cycle", "2")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST46_SECRET_CREATED_$cycle")
    }

    /** Test #47 creates Secret2 on Android before iOS joins. */
    @Test
    fun createSecretForTest47() {
        val secretName = argument("secretName", "test47-secret2")
        val secretValue = argument("secretValue", "test47-secret2-value")
        val cycle = argument("cycle", "2")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        composeRule.lbWaitMain(120_000)
        composeRule.lbCreatePlain(secretName, secretValue)

        marker("ANDROID_TEST47_SECRET_CREATED_$cycle")
    }

    @Test
    fun showLocalSecretForTest25() {
        val secretName = argument("secretName", "test25-secret1")
        val secretValue = argument("secretValue", "test25-secret1-value")
        val cycle = argument("cycle", "1")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.lbWaitMain(60_000)
        composeRule.lbSecrets()
        val shownId = composeRule.lbShow(secretName, secretValue)
        marker("ANDROID_TEST25_LOCAL_SECRET_VALUE_VERIFIED_$cycle")
        marker("ANDROID_LOCAL_SECRET_VISIBLE_$cycle")

        composeRule.lbHide(shownId)
        composeRule.lbWaitPrimary(secretName, lbLabel(core.AppString.lbShow))
        marker("ANDROID_LOCAL_SECRET_CLOSED_$cycle")
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
