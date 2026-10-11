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

/** Visible Android owner actions for UI E2E Test #32. */
@RunWith(AndroidJUnit4::class)
class Case32AndroidOwnerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultForTest32() {
        val vaultName = argument("vaultName", "test32@test.ru")
        val secretName = argument("secretName", "test32-secret1")
        val secretValue = argument("secretValue", "test32-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST32_SECRET1_READY")
        marker("ANDROID_TEST32_INITIATOR_SETUP_READY")
    }

    /** Test #35 Android owner setup with a run-specific completion marker. */
    @Test
    fun createVaultForTest35() {
        val vaultName = argument("vaultName", "test35@test.ru")
        val secretName = argument("secretName", "test35-secret1")
        val secretValue = argument("secretValue", "test35-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST35_SECRET1_READY")
        marker("ANDROID_TEST35_INITIATOR_SETUP_READY")
    }

    /** Test #38 rotates the membership-removal owner role to Android. */
    @Test
    fun createVaultForTest38() {
        val vaultName = argument("vaultName", "test38@test.ru")
        val secretName = argument("secretName", "test38-secret1")
        val secretValue = argument("secretValue", "test38-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST38_SECRET1_READY")
        marker("ANDROID_TEST38_INITIATOR_SETUP_READY")
    }

    /** Test #41 rotates secondary Secret creation to iOS while Android owns the Vault. */
    @Test
    fun createVaultForTest41() {
        val vaultName = argument("vaultName", "test41@test.ru")
        val secretName = argument("secretName", "test41-secret1")
        val secretValue = argument("secretValue", "test41-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST41_SECRET1_READY")
        marker("ANDROID_TEST41_INITIATOR_SETUP_READY")
    }

    /** Test #44 starts the distributed-creator removal lifecycle on Android. */
    @Test
    fun createVaultForTest44() {
        val vaultName = argument("vaultName", "test44@test.ru")
        val secretName = argument("secretName", "test44-secret1")
        val secretValue = argument("secretValue", "test44-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST44_SECRET1_READY")
        marker("ANDROID_TEST44_INITIATOR_SETUP_READY")
    }

    /** Test #48 starts the role-rotated declined-removal lifecycle on Android. */
    @Test
    fun createVaultForTest48() {
        val vaultName = argument("vaultName", "test48@test.ru")
        val secretName = argument("secretName", "test48-secret1")
        val secretValue = argument("secretValue", "test48-secret1-value")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("signin-email-manual", 30_000)
        composeRule.onNodeWithTag("signin-email-manual").performClick()
        composeRule.waitForTag("email-input", 30_000)
        composeRule.onNodeWithTag("email-input").performTextInput(vaultName)
        composeRule.onNodeWithTag("manual-signin-continue").performClick()
        composeRule.waitForTag("email-confirmation-continue", 30_000)
        composeRule.onNodeWithTag("email-confirmation-continue").performClick()
        composeRule.waitForTag("add-secret-fab", 180_000)

        createSecret(secretName, secretValue)
        marker("ANDROID_TEST48_SECRET1_READY")
        marker("ANDROID_TEST48_INITIATOR_SETUP_READY")
    }

    @Test
    fun approvePendingJoinForTest32() {
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

        marker("ANDROID_TEST32_JOIN_APPROVED_$target")
    }

    /** Test #35 Android is the sole approver of iOS's first Join. */
    @Test
    fun approvePendingJoinForTest35() {
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

        marker("ANDROID_TEST35_JOIN_APPROVED_$target")
    }

    @Test
    fun approvePendingJoinForTest38() {
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

        marker("ANDROID_TEST38_JOIN_APPROVED_$target")
    }

    /** Android is the sole visible Join approver for both Test #41 members. */
    @Test
    fun approvePendingJoinForTest41() {
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

        marker("ANDROID_TEST41_JOIN_APPROVED_$target")
    }

    /** Android is the sole visible Join approver for both Test #44 members. */
    @Test
    fun approvePendingJoinForTest44() {
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

        marker("ANDROID_TEST44_JOIN_APPROVED_$target")
    }

    /** Android A is the sole visible Join approver for both Test #48 members. */
    @Test
    fun approvePendingJoinForTest48() {
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

        marker("ANDROID_TEST48_JOIN_APPROVED_$target")
    }

    /** Test #35 boundary assertion for iOS-created Secret2/ Web-created Secret3. */
    @Test
    fun assertSecretVisibleForTest35() {
        val secretName = argument("secretName", "test35-secret2")
        val cycle = argument("cycle", "2")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        marker("ANDROID_TEST35_SECRET_VISIBLE_$cycle")
    }

    @Test
    fun createSecretForTest32() {
        val secretName = argument("secretName", "test32-secret2")
        val secretValue = argument("secretValue", "test32-secret2-value")
        val cycle = argument("cycle", "0")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        createSecret(secretName, secretValue)
        marker("ANDROID_TEST32_SECRET_CREATED_$cycle")
    }

    @Test
    fun createSecretForTest38() {
        val secretName = argument("secretName", "test38-secret2")
        val secretValue = argument("secretValue", "test38-secret2-value")
        val cycle = argument("cycle", "0")

        clickIfPresent("onboarding-skip", 10_000)
        composeRule.waitForTag("tab-secrets", 60_000)
        composeRule.onNodeWithTag("tab-secrets").performClick()
        createSecret(secretName, secretValue)
        marker("ANDROID_TEST38_SECRET_CREATED_$cycle")
    }

    private fun createSecret(secretName: String, secretValue: String) {
        composeRule.waitForTag("add-secret-fab", 120_000)
        composeRule.onNodeWithTag("add-secret-fab").performClick()
        composeRule.waitForTag("secret-name-input", 30_000)
        composeRule.onNodeWithTag("secret-name-input").performTextInput(secretName)
        composeRule.onNodeWithTag("secret-value-input").performTextInput(secretValue)
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitForTagGone("secret-name-input", 30_000)
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
