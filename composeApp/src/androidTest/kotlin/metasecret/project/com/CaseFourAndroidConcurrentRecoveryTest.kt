package metasecret.project.com

import android.util.Log
import metasecret.project.com.CaseFourScenarioPlan.SecretDefinition
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CaseFourAndroidConcurrentRecoveryTest {
    val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule
    val activityAndShowObservation: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(CaseFourShowObservationRule()).around(composeRule)

    @Test
    fun createVaultThenHandleConcurrentRecovery() {
        val vaultName = instrumentationArgument("vaultName", "test@test.ru")
        val secrets = secretDefinitions()

        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitCreateVault()
        composeRule.lbWaitMain(180_000)
        val initialSecrets = initialSecretsToCreate(secrets)
        Log.i(
            "MetaSecretE2E",
            "E2E: ANDROID_INITIAL_SECRET_PLAN names="
                + initialSecrets.joinToString(",") { it.name },
        )
        initialSecrets.forEach { secret ->
            createSecret(secret)
        }
        marker("ANDROID_INITIATOR_READY")

        val approvalCoordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )
        approvePendingJoin("ANDROID_WEB_JOIN_APPROVED")
        // Do not approve an earlier/stale iOS request while xcodebuild is
        // still building the fresh UI-test runner. The orchestrator opens
        // this gate only after that runner emitted IOS_JOIN_REQUEST_SENT.
        waitForApproval(approvalCoordinatorUrl, "android-ios-join", 0)
        approvePendingJoin("ANDROID_IOS_JOIN_APPROVED")

        composeRule.lbSecrets()
        // The device-count caption is informational and can lag the state
        // refresh after the third device joins. The secret row and its primary
        // action are the state this recovery test actually needs, so use those
        // semantic nodes as the readiness gate instead of waiting for a
        // localized text string such as "3 devices".
        secrets.forEach { secret ->
            composeRule.lbSecret(secret.name)
            composeRule.lbWait("lb-primary-${composeRule.lbSecret(secret.name)}")
            Log.i("MetaSecretE2E", "E2E: ANDROID_SECRET_READY_${secret.name}")
        }
        marker("ANDROID_SECRETS_READY")
        if (instrumentationArgument("setupOnly", "false").toBoolean()) {
            marker("ANDROID_SETUP_ONLY_DONE")
            return
        }
        val fifoIdentity = lbFifoIdentity(approvalCoordinatorUrl)
        for (cycle in recoveryCyclePlan()) {
            val ownSecret = cycle.senderSecrets["android"]
            val assigned = cycle.approvals.withIndex().filter { it.value.platform == "android" }
            var submittedContext: JSONObject? = null
            if (ownSecret != null) {
                waitForApproval(approvalCoordinatorUrl, "android-sender", cycle.number)
                requestConcurrentRecovery(ownSecret)
                marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle.number}")
                // Keep the sender's waiting dialog open until both approvals
                // complete. Reopening it in a receiver-only cycle would start
                // a second recovery request instead of revealing this claim.
                // Only a sender that must approve the peer's claim needs to
                // leave its dialog before the reveal phase.
                if (cycle.approvals.any { it.platform == "android" }) {
                    if (cycle.senderSecrets.size == 2) {
                        // Navigation can revoke queued Recover; wait for both actual submissions first.
                        submittedContext = lbFifoContext(approvalCoordinatorUrl, cycle.number, fifoIdentity)
                    }
                    closeShowSecretDialog()
                }
            }

            if (assigned.isNotEmpty()) {
                composeRule.lbHome()
                val context = submittedContext ?: lbFifoContext(approvalCoordinatorUrl, cycle.number, fifoIdentity)
                val consumed = mutableSetOf<Int>()
                while (consumed.size < assigned.size) {
                    var selected: LiveboardFifoStep? = null
                    composeRule.waitUntil(180_000) {
                        selected = composeRule.lbFifoHead(context, "android", consumed)
                        selected != null
                    }
                    val selectedHead = checkNotNull(selected)
                    val step = selectedHead.logicalStep
                    check(assigned.any { it.index + 1 == step && it.value.secret == selectedHead.incoming.subject })
                    val head = composeRule.lbIncoming("Recover", selectedHead.incoming.subject, selectedHead.incoming.requesterName)
                    check(head == selectedHead.incoming)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle.number}_$step claimId=${head.requestId}")
                    waitForApproval(approvalCoordinatorUrl, "android-approve-$step", cycle.number)
                    composeRule.lbDecide(head, true)
                    consumed.add(step)
                    marker("ANDROID_APPROVED_INCOMING_${cycle.number}_$step")
                }
            }

            if (ownSecret != null) {
                waitForApproval(approvalCoordinatorUrl, "android-show", cycle.number)
                revealAndClose(
                    ownSecret,
                    reopenClaim = cycle.approvals.any { it.platform == "android" },
                )
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle.number}")
                marker("ANDROID_RECOVERY_CLOSED_${cycle.number}")
            }
        }
    }

    @Test
    fun runNetworkLossCycles() {
        val role = instrumentationArgument("networkRole", "")
        val block = instrumentationArgument("networkBlock", "")
        check(role in setOf("sender", "approver", "offline-receiver")) {
            "Unsupported network-loss role: $role"
        }
        val coordinatorUrl = instrumentationArgument(
            "approvalCoordinatorUrl",
            "http://10.0.2.2:5180",
        )
        val cycles = recoveryCyclePlan().filter { it.block == block }
        check(cycles.isNotEmpty()) { "No network-loss cycles found for block=$block" }
        Log.i(
            "MetaSecretE2E",
            "E2E: ANDROID_NETWORK_CONFIG role=$role block=$block cycles="
                + cycles.joinToString(",") { it.number.toString() },
        )

        cycles.forEach { cycle ->
            val senderSecret = cycle.senderSecrets[cycle.sender]
                ?: cycle.senderSecrets.values.firstOrNull()
                ?: instrumentationArgument("secretName", "test-secret")
            when (role) {
                "sender" -> {
                    waitForApproval(coordinatorUrl, "android-sender", cycle.number)
                    requestRecovery(senderSecret)
                    marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle.number}_1")
                    waitForApproval(coordinatorUrl, "android-show", cycle.number)
                    revealAndClose(senderSecret, reopenClaim = false)
                    marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle.number}_1")
                    marker("ANDROID_RECOVERY_CLOSED_${cycle.number}_1")
                }

                "approver" -> {
                    waitForApproval(coordinatorUrl, "android-approve", cycle.number)
                    approveIncomingRecovery(cycle.secretName)
                    marker("ANDROID_APPROVED_INCOMING_${cycle.number}_1")
                }

                "offline-receiver" -> {
                    waitForIncomingRecoveryUi(cycle.secretName)
                    marker("ANDROID_INCOMING_VISIBLE_${cycle.number}_1")
                    marker("ANDROID_NETWORK_LOSS_READY_${cycle.number}")
                    waitForApproval(coordinatorUrl, "android-offline-attempt", cycle.number)
                    clickIncomingApproveWithoutWaiting(cycle.secretName)
                    marker("ANDROID_OFFLINE_APPROVE_CLICKED_${cycle.number}")
                    waitForApproval(coordinatorUrl, "android-offline-online", cycle.number)
                    approveIncomingRecoveryAfterReconnect(cycle.secretName)
                    marker("ANDROID_APPROVED_AFTER_RECONNECT_${cycle.number}_1")
                }
            }
        }
        marker("ANDROID_NETWORK_LOSS_DONE")
    }

    private val scenarioPlan = CaseFourScenarioPlan(::instrumentationArgument)
    private fun recoveryCyclePlan() = scenarioPlan.recoveryCyclePlan()
    private fun secretDefinitions() = scenarioPlan.secretDefinitions()
    private fun initialSecretsToCreate(allSecrets: List<SecretDefinition>) = scenarioPlan.initialSecretsToCreate(allSecrets)

    private fun createSecret(secret: SecretDefinition) {
        composeRule.lbCreatePlain(secret.name, secret.value)

    }

    /**
     * A state refresh can replace a TextInput semantics node between the
     * lookup and the text action. Re-fetch the node on every polling pass and
     * verify the resulting value; this handles an action that was applied just
     * before Compose detached the old node without introducing a fixed delay.
     */
    /** Shared Test4/5/6 concurrent driver uses accessibility; NetworkLoss keeps physical dispatch. */
    private fun requestConcurrentRecovery(secretName: String) {
        ensurePrimaryActionReadyForRecovery(secretName)
        marker("ANDROID_PRIMARY_ACTION_CLICK_${secretName}")
        composeRule.lbRequestRecoverySemantics(secretName)
        marker("ANDROID_SHOW_SECRET_DIALOG_OPEN_${secretName}")
    }

    private fun requestRecovery(secretName: String) {
        ensurePrimaryActionReadyForRecovery(secretName)
        marker("ANDROID_PRIMARY_ACTION_CLICK_${secretName}")
        composeRule.lbRequestRecovery(secretName)
        marker("ANDROID_SHOW_SECRET_DIALOG_OPEN_${secretName}")

    }

    private fun ensurePrimaryActionReadyForRecovery(secretName: String) {
        val id = composeRule.lbSecret(secretName)
        if (composeRule.lbActionText(id) == lbLabel(core.AppString.lbShow)) {
            marker("ANDROID_STALE_SHOW_ACTION_$secretName; waiting for Recover before new recovery")
        }
        waitForPrimaryAction(secretName, "Recover")
        marker("ANDROID_RECOVER_ACTION_READY_$secretName")

    }

    private fun closeShowSecretDialog() {
        if (composeRule.lbTagged("lb-value-").isEmpty() && !composeRule.lbHas("lb-secrets")) marker("ANDROID_SHOW_SECRET_DIALOG_ALREADY_CLOSED actualPendingNavigation=true")
        composeRule.lbCloseCurrentRevealOrPending()
    }

    private fun approveIncomingRecovery(secretName: String, awaitNextRequest: Boolean = false) {
        marker("ANDROID_WAITING_INCOMING_RECOVERY_${secretName}")
        val head = composeRule.lbIncoming("Recover", secretName)
        marker("ANDROID_INCOMING_RECOVERY_ALERT_OPEN_${secretName}")
        if (awaitNextRequest) marker("ANDROID_INCOMING_SENDER_BEFORE_$secretName claimId=${head.requestId}")
        composeRule.lbDecide(head, true)
        marker("ANDROID_INCOMING_RECOVERY_ACCEPTED_${secretName}")
        if (awaitNextRequest) {
            composeRule.waitUntil(120_000) { composeRule.lbVisibleIncoming()?.requestId != head.requestId }
            marker("ANDROID_INCOMING_SENDER_AFTER_$secretName claimChanged=true")
        }

    }

    private fun clickIncomingApproveWithoutWaiting(secretName: String) {
        marker("ANDROID_NETWORK_LOSS_OPENING_ALERT_$secretName")
        val head = composeRule.lbIncoming("Recover", secretName)
        composeRule.lbTap("lb-approve-${head.requestId}")
        composeRule.lbApproveSystemIfPresent()
        marker("ANDROID_NETWORK_LOSS_ACCEPT_DISPATCHED secret=$secretName requestId=${head.requestId}")

    }

    private fun approveIncomingRecoveryAfterReconnect(secretName: String) {
        val head = checkNotNull(composeRule.lbScope().incoming)
        check(head.subject == secretName)
        // Await the SAME already submitted decision; never replay an entered producer after reconnect.
        composeRule.lbIncomingTerminal(head)
        marker("ANDROID_NETWORK_LOSS_RECONNECTED_ACCEPTED secret=$secretName")

    }

    private fun waitForIncomingRecoveryUi(secretName: String) {
        marker("ANDROID_INCOMING_UI_STATE_$secretName headPresent=${composeRule.lbVisibleIncoming() != null} count=${composeRule.lbIncomingCount()}")
        composeRule.lbIncoming("Recover", secretName)

    }

    private fun revealAndClose(secretName: String, reopenClaim: Boolean) {
        if (reopenClaim) marker("ANDROID_WAITING_SHOW_ACTION_${secretName}")
        waitForPrimaryAction(secretName, "Show")
        if (reopenClaim) marker("ANDROID_SHOW_ACTION_READY_${secretName}")
        val expected = secretDefinitions().single { it.name == secretName }.value
        marker("ANDROID_RECOVERY_PHASE_SHOW_ENTER")
        val shown = composeRule.lbRevealAccepted(secretName, expected)
        marker("ANDROID_RECOVERY_PHASE_SHOW_RETURNED_VALUE_CHECKED")
        if (reopenClaim) marker("ANDROID_SHOW_ACTION_CLICKED_${secretName}")
        marker("ANDROID_RECOVERY_PHASE_HIDE_ENTER")
        composeRule.lbHide(shown)
        marker("ANDROID_RECOVERY_PHASE_HIDE_RETURNED")
        marker("ANDROID_RECOVERY_PHASE_RECOVER_WAIT_ENTER")
        waitForPrimaryAction(secretName, "Recover")
        marker("ANDROID_RECOVERY_PHASE_RECOVER_WAIT_COMPLETE")
        marker("ANDROID_RECOVER_ACTION_READY_$secretName")

    }

    private fun waitForPrimaryAction(secretName: String, expectedText: String) {
        val label = if (expectedText == "Recover") lbLabel(core.AppString.recover) else lbLabel(core.AppString.lbShow)
        composeRule.lbWaitPrimary(secretName, label)

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
        val head = composeRule.lbIncoming("Join")
        val joiningDeviceName = head.requesterName
        composeRule.lbDecide(head, true)
        marker("ANDROID_JOIN_APPROVAL_DISPATCHED_$marker")
        composeRule.lbMember(joiningDeviceName, current = false)
        marker("$marker deviceName=$joiningDeviceName status=Member")

    }

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: Int) {
        // The iOS join test may need to wait for the owner approval and then
        // unlock with the simulator PIN. Do not abort it while it is still
        // producing the primary diagnostic.
        composeRule.waitUntil(240_000) {
            runCatching {
                val connection = URL("$coordinatorUrl/approval?platform=$platform&cycle=$cycle")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.inputStream.bufferedReader().use { it.readText() == "allowed" }
                    .also { connection.disconnect() }
            }.getOrDefault(false)
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMillis: Long) {
        waitUntil(timeoutMillis) {
            // The revealed value is nested under a semantic container which
            // merges its descendants. Test tags attached to nested content are
            // only visible in the unmerged tree.
            // Compose can briefly invalidate the semantics tree while a frame
            // is being measured. Treat that frame as not ready and let the
            // existing semantic polling retry instead of failing the test.
            runCatching {
                onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun deviceRowMatcher(): androidx.compose.ui.test.SemanticsMatcher =
        androidx.compose.ui.test.SemanticsMatcher("TestTag starts with device-row-") { node ->
            runCatching { node.config[SemanticsProperties.TestTag] }
                .getOrNull()
                ?.startsWith("device-row-") == true
        }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagGone(
        tag: String,
        timeoutMillis: Long,
    ) {
        waitUntil(timeoutMillis) {
            runCatching {
                onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }.getOrDefault(false)
        }
    }
}
