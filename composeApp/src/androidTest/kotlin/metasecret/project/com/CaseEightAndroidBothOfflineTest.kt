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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CaseEightAndroidBothOfflineTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun joinWebInitiatedVault() {
        val vaultName = argument("vaultName", "test8@test.ru")
        val secretName = argument("secretName", "test-secret")
        clickIfPresent("onboarding-skip", 10_000)
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
        configuredSecretNames().forEach { name -> composeRule.waitForTag("secret-row-$name", 180_000) }
        marker("ANDROID_JOIN_READY")
        marker("ANDROID_SECRETS_READY")
    }

    @Test
    fun sendRecoveryRequestAndExit() {
        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        waitForApproval(coordinator, "android-sender-1", cycle)
        waitForPrimaryAction(secretName, "Recover")
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("show-secret-dialog", 30_000)
        marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_1")
        // Keep the instrumentation alive until the native call has flushed
        // the claim. The orchestrator releases this gate after observing the
        // cycle-specific native log marker.
        waitForApproval(coordinator, "android-request-persisted", cycle)
        Log.i("MetaSecretE2E", "E2E: ANDROID_SENDER_OFFLINE_BOUNDARY_$cycle")
    }

    @Test
    fun showAcceptedRecoveryAfterOffline() {
        val secretName = argument("secretName", "test-secret")
        val cycle = argument("cycle", "1")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        waitForPrimaryAction(secretName, "Show")
        waitForApproval(coordinator, "android-show-1", cycle)
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        composeRule.waitForTag("revealed-secret-value", 180_000)
        marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_1")
        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitForTagGone("revealed-secret-value", 30_000)
        composeRule.waitForTagGone("show-secret-dialog", 30_000)
        waitForPrimaryAction(secretName, "Recover")
        marker("ANDROID_RECOVERY_CLOSED_${cycle}_1")
    }

    @Test
    fun handleRecoveryStep() {
        val secretName = argument("secretName", "test-secret")
        val role = argument("role", "receiver")
        val cycle = argument("cycle", "1")
        val step = argument("step", "1")
        val approvalPlatform = argument("approvalPlatform", "")
        val coordinator = argument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        composeRule.waitForTag("secret-row-$secretName", 180_000)

        if (role == "sender") {
            waitForApproval(coordinator, "android-sender-$step", cycle)
            // An earlier receiver approval can leave this sender with an
            // accepted claim and therefore a Show action. Reveal and close it
            // before waiting for Recover and creating the next request.
            showAcceptedSecretIfNeeded(secretName, cycle, step)
            waitForPrimaryAction(secretName, "Recover")
            composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
            composeRule.waitForTag("show-secret-dialog", 30_000)
            marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle}_$step")
            waitForApproval(coordinator, "android-show-$step", cycle)
            revealAcceptedSecret(secretName, cycle, step)
            marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle}_$step")
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            waitForPrimaryAction(secretName, "Recover")
            marker("ANDROID_RECOVERY_CLOSED_${cycle}_$step")
            return
        }

        composeRule.waitForTag("recovery-request-badge-$secretName", 180_000)
        composeRule.waitForTag("open-recovery-request-$secretName", 30_000)
        marker("ANDROID_INCOMING_VISIBLE_${cycle}_$step")
        if (approvalPlatform == "android") {
            waitForApproval(coordinator, "android-approve-$step", cycle)
            composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
            composeRule.waitForTag("alert-recovery-request", 30_000)
            composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
            composeRule.waitUntil(120_000) {
                composeRule.onAllNodes(hasTestTag("alert-recovery-request"), useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty() && composeRule.onAllNodes(
                        hasTestTag("alert-recovery-request-processing"), useUnmergedTree = true,
                    ).fetchSemanticsNodes().isEmpty()
            }
            marker("ANDROID_APPROVED_INCOMING_${cycle}_$step")
        } else {
            waitForApproval(coordinator, "android-dismiss-$step", cycle)
            composeRule.waitForTagGone("recovery-request-badge-$secretName", 120_000)
            composeRule.waitForTagGone("open-recovery-request-$secretName", 120_000)
            marker("ANDROID_DISMISSED_INCOMING_${cycle}_$step")
        }
    }

    private fun revealAcceptedSecret(secretName: String, cycle: String, step: String) {
        var showClicked = false
        composeRule.waitUntil(180_000) {
            if (runCatching {
                    composeRule.onAllNodes(
                        hasTestTag("revealed-secret-value"), useUnmergedTree = true,
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            ) return@waitUntil true

            if (!showClicked && runCatching {
                    composeRule.onNodeWithTag("secret-primary-action-$secretName")
                        .assertTextContains("Show", substring = true)
                }.isSuccess
            ) {
                marker("ANDROID_SHOW_ACTION_AFTER_APPROVAL_${cycle}_$step")
                composeRule.onAllNodes(hasTestTag("show-secret-dialog"), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
                    }
                composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
                showClicked = true
            }
            false
        }
        composeRule.waitForTag("revealed-secret-value", 180_000)
    }

    private fun waitForPrimaryAction(secretName: String, expected: String) {
        composeRule.waitUntil(180_000) {
            runCatching {
                composeRule.onNodeWithTag("secret-primary-action-$secretName")
                    .assertTextContains(expected, substring = true)
            }.isSuccess
        }
    }

    private fun showAcceptedSecretIfNeeded(secretName: String, cycle: String, step: String) {
        composeRule.waitUntil(180_000) {
            val action = composeRule.onNodeWithTag("secret-primary-action-$secretName")
            val isRecover = runCatching {
                action.assertTextContains("Recover", substring = true)
            }.isSuccess
            if (isRecover) return@waitUntil true

            val isShow = runCatching {
                action.assertTextContains("Show", substring = true)
            }.isSuccess
            if (!isShow) return@waitUntil false

            marker("ANDROID_STALE_SHOW_ACTION_${cycle}_$step consuming previously accepted claim")
            action.performClick()
            composeRule.waitForTag("revealed-secret-value", 180_000)
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
            composeRule.waitForTagGone("revealed-secret-value", 30_000)
            composeRule.waitForTagGone("show-secret-dialog", 30_000)
            true
        }
    }

    private fun waitForApproval(url: String, platform: String, cycle: String) {
        composeRule.waitUntil(240_000) {
            runCatching {
                val connection = URL("$url/approval?platform=$platform&cycle=$cycle")
                    .openConnection() as HttpURLConnection
                val result = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                result == "allowed"
            }.getOrDefault(false)
        }
    }

    private fun clickIfPresent(tag: String, timeout: Long) {
        runCatching {
            composeRule.waitForTag(tag, timeout)
            composeRule.onNodeWithTag(tag).performClick()
        }
    }

    private fun argument(name: String, fallback: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

    private fun configuredSecretNames(): List<String> {
        val raw = argument("secretConfig", "")
        if (raw.isBlank()) {
            val scalarNames = argument("secretNames", "").split(',').filter { it.isNotBlank() }
            return scalarNames.ifEmpty { listOf(argument("secretName", "test-secret")) }
        }
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return listOf(argument("secretName", "test-secret"))
        val names = mutableListOf<String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            names += json.getJSONObject(key).getString("name")
        }
        return names.ifEmpty { listOf(argument("secretName", "test-secret")) }
    }

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String,
        timeoutMillis: Long,
    ) {
        // Tags inside merged semantic containers (for example the revealed
        // secret value inside ShowSecretDialog) are only visible in the
        // unmerged tree. Keep this helper consistent with the successful
        // Android recovery tests and avoid waiting forever for a tag that is
        // already present but hidden by semantics merging.
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
        waitUntil(timeoutMillis) { onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
