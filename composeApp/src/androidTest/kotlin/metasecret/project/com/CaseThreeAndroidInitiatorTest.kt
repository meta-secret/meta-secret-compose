package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.printToLog
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
class CaseThreeAndroidInitiatorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultThenApproveWebAndIos() {
        val vaultName = instrumentationArgument("vaultName", "test3@test.ru")
        val secretName = instrumentationArgument("secretName", "test-secret")

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(30_000)
        composeRule.lbCreatePlain(secretName, "test-secret-value")
        // Match Test #2's one-secret owner setup: reveal and close once so the
        // owner's claim is initialized before Android sends recovery cycles.
        showCreatedSecret(secretName)
        marker("ANDROID_INITIATOR_READY")

        approvePendingJoin("ANDROID_WEB_JOIN_APPROVED")
        approvePendingJoin("ANDROID_IOS_JOIN_APPROVED")

        val recoveryCycles = instrumentationArgument("recoveryCycles", "18").toInt()
        val approvalCoordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )
        // Android is the recovery sender in Test #3. Web and iOS receive the
        // requests and the orchestrator chooses which one approves each cycle.
        composeRule.lbSecrets()
        for (cycle in 1..recoveryCycles) {
            waitForApproval(approvalCoordinatorUrl, "android-sender", cycle)
            composeRule.lbWaitPrimary(secretName, lbLabel(core.AppString.recover), 45_000)
            Log.i("MetaSecretE2E", "E2E_PRIMARY_ACTION_CYCLE_$cycle actualRecoverReady=true")
            composeRule.lbRequestRecovery(secretName)
            marker("ANDROID_RECOVERY_REQUEST_SENT_$cycle")
            val shown = composeRule.lbRevealAccepted(secretName, "test-secret-value")
            marker("ANDROID_RECOVERY_SECRET_VISIBLE_$cycle")
            composeRule.lbHide(shown)
            marker("ANDROID_RECOVERY_CLOSED_$cycle")
        }
    }

    @Test
    fun createVaultAndWaitForJoins() {
        val vaultName = instrumentationArgument("vaultName", "test3@test.ru")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(180_000)

        val configuredNames = instrumentationArgument("secretNames", "").split(',').filter { it.isNotBlank() }
        val configuredValues = instrumentationArgument("secretValues", "").split(',')
        val secretConfigRaw = instrumentationArgument("secretConfig", "")
        val configuredSecrets = if (configuredNames.isNotEmpty()) {
            configuredNames.zip(configuredValues + List(configuredNames.size) { "test-secret-value" })
        } else {
            val secretConfig = runCatching { JSONObject(secretConfigRaw) }
                .getOrElse { error("Invalid secretConfig: ${it.message}") }
            val keys = mutableListOf<String>()
            val iterator = secretConfig.keys()
            while (iterator.hasNext()) keys += iterator.next()
            keys.sorted().map { key ->
                val secret = secretConfig.getJSONObject(key)
                secret.getString("name") to secret.getString("value")
            }
        }
        configuredSecrets.forEach { (name, value) ->
            createSecret(name, value)
            // Initialize the creator's claim exactly as the iOS initiator
            // setup does. Without this first Show, the Android owner can
            // retain a PENDING claim; the recovery step then only reports
            // "request already sent" and emits no new recovery request.
            showCreatedSecret(name)
        }
        marker("ANDROID_INITIATOR_READY")
        approvePendingJoin("ANDROID_WEB_JOIN_APPROVED")
        val joinPlatforms = instrumentationArgument("joinPlatforms", "web,ios").split(',').toSet()
        if (joinPlatforms.contains("ios")) {
            approvePendingJoin("ANDROID_IOS_JOIN_APPROVED")
        }
        // Join approval leaves the owner on Devices. Switch back to Secrets
        // before asserting that all configured secret rows are visible.
        composeRule.lbSecrets()
        configuredSecrets.forEach { (name, _) ->
            composeRule.lbSecret(name, 180_000)
            composeRule.lbWait("lb-primary-${composeRule.lbSecret(name)}")
        }
        marker("ANDROID_SECRETS_READY")
    }

    private fun createSecret(name: String, value: String) {
        composeRule.lbCreatePlain(name, value)
    
    }

    private fun showCreatedSecret(name: String) {
        val id = composeRule.lbShow(name)
        composeRule.lbHide(id)
    
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

    private fun approvePendingJoin(marker: String) {
        marker("ANDROID_OPENING_DEVICES_FOR_JOIN_$marker")
        composeRule.lbHome()
        marker("ANDROID_DEVICES_TAB_OPENED_$marker")
        val incoming = composeRule.lbIncoming("Join")
        composeRule.lbDecide(incoming, true)
        marker("ANDROID_JOIN_APPROVAL_DISPATCHED_$marker")
        marker(marker)
    
    }

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: Int) {
        // The iOS join test may need to wait for the owner approval and then
        // unlock with the simulator PIN. Do not abort it while it is still
        // producing the primary diagnostic.
        val deadline = System.currentTimeMillis() + 240_000
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
        error("Timed out waiting for orchestrator permission to start Android recovery cycle $cycle")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMillis: Long) {
        waitUntil(timeoutMillis) {
            // The revealed value is nested under a semantic container which
            // merges its descendants. Test tags attached to nested content are
            // only visible in the unmerged tree.
            onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(tag: String, timeoutMillis: Long) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }
}
