package metasecret.project.com

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CaseFourAndroidConcurrentRecoveryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createVaultThenHandleConcurrentRecovery() {
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
        composeRule.waitForTag("add-secret-fab", 180_000)
        composeRule.onNodeWithTag("add-secret-fab").performClick()
        composeRule.waitForTag("secret-name-input", 30_000)
        composeRule.onNodeWithTag("secret-name-input").performTextInput(secretName)
        composeRule.onNodeWithTag("secret-value-input").performTextInput("test-secret-value")
        composeRule.onNodeWithTag("add-secret-submit").performClick()
        composeRule.waitForTag("secret-row-$secretName", 180_000)
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

        composeRule.onNodeWithTag("tab-secrets").performClick()
        // The device-count caption is informational and can lag the state
        // refresh after the third device joins. The secret row and its primary
        // action are the state this recovery test actually needs, so use those
        // semantic nodes as the readiness gate instead of waiting for a
        // localized text string such as "3 devices".
        composeRule.waitForTag("secret-row-$secretName", 180_000)
        composeRule.waitForTag("secret-primary-action-$secretName", 180_000)
        Log.i("MetaSecretE2E", "E2E: ANDROID_SECRET_READY_$secretName")
        for (cycle in recoveryCyclePlan()) {
            if ("android" in cycle.senders) {
                waitForApproval(approvalCoordinatorUrl, "android-sender", cycle.number)
                requestRecovery(secretName)
                marker("ANDROID_RECOVERY_REQUEST_SENT_${cycle.number}")
                // Keep the sender's waiting dialog open until both approvals
                // complete. Reopening it in a receiver-only cycle would start
                // a second recovery request instead of revealing this claim.
                // Only a sender that must approve the peer's claim needs to
                // leave its dialog before the reveal phase.
                if ("android" in setOf(cycle.firstApprover, cycle.secondApprover)) {
                    closeShowSecretDialog()
                }
            }

            listOf(cycle.firstApprover, cycle.secondApprover).forEachIndexed { index, approver ->
                if (approver == "android") {
                    val step = index + 1
                    waitForApproval(approvalCoordinatorUrl, "android-approve-$step", cycle.number)
                    approveIncomingRecovery(
                        secretName,
                        awaitNextRequest = step == 1 && cycle.secondApprover == "android",
                    )
                    marker("ANDROID_APPROVED_INCOMING_${cycle.number}_$step")
                }
            }

            if ("android" in cycle.senders) {
                waitForApproval(approvalCoordinatorUrl, "android-show", cycle.number)
                revealAndClose(
                    secretName,
                    reopenClaim = "android" in setOf(cycle.firstApprover, cycle.secondApprover),
                )
                marker("ANDROID_RECOVERY_SECRET_VISIBLE_${cycle.number}")
                marker("ANDROID_RECOVERY_CLOSED_${cycle.number}")
            }
        }
    }

    private data class RecoveryCycle(
        val number: Int,
        val senders: Set<String>,
        val firstApprover: String,
        val secondApprover: String,
    )

    private fun recoveryCyclePlan(): List<RecoveryCycle> {
        val rawPlan = instrumentationArgument("cyclePlan", "[]")
        val json = runCatching { JSONArray(rawPlan) }.getOrElse { error("Invalid cyclePlan: ${it.message}") }
        return (0 until json.length()).map { index ->
            val cycle = json.getJSONObject(index)
            RecoveryCycle(
                number = cycle.getInt("number"),
                senders = (0 until cycle.getJSONArray("senders").length())
                    .map { cycle.getJSONArray("senders").getString(it) }
                    .toSet(),
                firstApprover = cycle.getString("firstApprover"),
                secondApprover = cycle.getString("secondApprover"),
            )
        }
    }

    private fun requestRecovery(secretName: String) {
        ensurePrimaryActionReadyForRecovery(secretName)
        marker("ANDROID_PRIMARY_ACTION_CLICK_${secretName}")
        composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
        // The marker must follow dialog composition. The orchestrator separately
        // waits for the Web badge, which confirms that the native recovery call
        // produced a server claim.
        composeRule.waitForTag("show-secret-dialog", 30_000)
        marker("ANDROID_SHOW_SECRET_DIALOG_OPEN_${secretName}")
    }

    private fun ensurePrimaryActionReadyForRecovery(secretName: String) {
        val primaryAction = composeRule.onNodeWithTag("secret-primary-action-$secretName")
        val isShowAction = runCatching {
            primaryAction.assertTextContains("Show", substring = true)
        }.isSuccess
        if (isShowAction) {
            Log.i(
                "MetaSecretE2E",
                "E2E: ANDROID_STALE_SHOW_ACTION_$secretName; waiting for Recover before new recovery",
            )
        }
        waitForPrimaryAction(secretName, "Recover")
        Log.i("MetaSecretE2E", "E2E: ANDROID_RECOVER_ACTION_READY_$secretName")
    }

    private fun closeShowSecretDialog() {
        val dialogNodes = composeRule.onAllNodes(
            hasTestTag("show-secret-dialog"),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val closeNodes = composeRule.onAllNodes(
            hasTestTag("show-secret-close"),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (dialogNodes.isNotEmpty() && closeNodes.isNotEmpty()) {
            composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        } else {
            // A concurrent state refresh may dismiss the sender dialog before
            // this device starts approving the peer's request. That is already
            // the desired post-close state.
            Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_SECRET_DIALOG_ALREADY_CLOSED")
        }
        // AnimatedVisibility keeps the dialog in the tree during its exit
        // animation. Wait for disposal before a later cycle reopens the same
        // secret, otherwise the LaunchedEffect keyed by secret name is reused
        // and the Show action is not dispatched a second time.
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodes(
                hasTestTag("show-secret-dialog"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun approveIncomingRecovery(secretName: String, awaitNextRequest: Boolean = false) {
        marker("ANDROID_WAITING_INCOMING_RECOVERY_${secretName}")
        waitForIncomingRecoveryUi(secretName)
        val senderBeforeApproval = if (awaitNextRequest) {
            semanticsText("recovery-request-notice-$secretName")
                .also { marker("ANDROID_INCOMING_SENDER_BEFORE_$secretName ${it ?: "missing"}") }
        } else {
            null
        }
        composeRule.onNodeWithTag("open-recovery-request-$secretName").performClick()
        composeRule.waitForTag("alert-recovery-request", 30_000)
        marker("ANDROID_INCOMING_RECOVERY_ALERT_OPEN_${secretName}")
        composeRule.onNodeWithTag("alert-recovery-request-accept").performClick()
        composeRule.waitUntil(120_000) {
            val alertClosed = composeRule.onAllNodes(
                hasTestTag("alert-recovery-request"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
            val processingClosed = composeRule.onAllNodes(
                hasTestTag("alert-recovery-request-processing"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
            alertClosed && processingClosed
        }
        marker("ANDROID_INCOMING_RECOVERY_ACCEPTED_${secretName}")
        if (senderBeforeApproval != null) {
            // The open-request control can remain composed while the accepted
            // claim is being removed from state. Before the second approval,
            // require the pending sender notice to change (or disappear), so a
            // second click cannot accept the same claim again.
            composeRule.waitUntil(120_000) {
                val senderAfterApproval = semanticsText("recovery-request-notice-$secretName")
                if (senderAfterApproval != senderBeforeApproval) {
                    marker(
                        "ANDROID_INCOMING_SENDER_AFTER_$secretName "
                            + (senderAfterApproval ?: "missing"),
                    )
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun semanticsText(tag: String): String? = runCatching {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .toString()
    }.getOrNull()

    private fun waitForIncomingRecoveryUi(secretName: String) {
        val badgeTag = "recovery-request-badge-$secretName"
        val openRequestTag = "open-recovery-request-$secretName"
        var lastSignature: String? = null
        composeRule.waitUntil(180_000) {
            val rowCount = composeRule.onAllNodes(
                hasTestTag("secret-row-$secretName"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size
            val badgeCount = composeRule.onAllNodes(
                hasTestTag(badgeTag),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size
            val openRequestCount = composeRule.onAllNodes(
                hasTestTag(openRequestTag),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size
            val dialogCount = composeRule.onAllNodes(
                hasTestTag("show-secret-dialog"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size
            val signature = "row=$rowCount badge=$badgeCount open=$openRequestCount dialog=$dialogCount"
            if (signature != lastSignature) {
                Log.i("MetaSecretE2E", "E2E: ANDROID_INCOMING_UI_STATE_$secretName $signature")
                lastSignature = signature
            }
            badgeCount > 0 && openRequestCount > 0
        }
    }

    private fun revealAndClose(secretName: String, reopenClaim: Boolean) {
        if (reopenClaim) {
            // A receiver approval can turn the sender's card from Recover into
            // Show before the next step is opened. Wait for that semantic UI
            // state instead of clicking a stale action and starting another
            // recovery request.
            marker("ANDROID_WAITING_SHOW_ACTION_${secretName}")
            waitForPrimaryAction(secretName, "Show")
            marker("ANDROID_SHOW_ACTION_READY_${secretName}")
            composeRule.onNodeWithTag("secret-primary-action-$secretName").performClick()
            marker("ANDROID_SHOW_ACTION_CLICKED_${secretName}")
            composeRule.waitForTag("show-secret-dialog", 30_000)
        }
        composeRule.waitForTag("revealed-secret-value", 180_000)
        composeRule.onNodeWithTag("show-secret-close", useUnmergedTree = true).performClick()
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodes(hasTestTag("revealed-secret-value"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodes(hasTestTag("show-secret-dialog"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        // The card can retain the Show label for a short state-refresh window
        // after the accepted claim has been consumed. Do not let the next cycle
        // click that stale action and mistake it for a new recovery request.
        waitForPrimaryAction(secretName, "Recover")
        Log.i("MetaSecretE2E", "E2E: ANDROID_RECOVER_ACTION_READY_$secretName")
    }

    private fun waitForPrimaryAction(secretName: String, expectedText: String) {
        composeRule.waitUntil(180_000) {
            runCatching {
                composeRule.onNodeWithTag(
                    "secret-primary-action-$secretName",
                ).assertTextContains(expectedText, substring = true)
            }.isSuccess
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

    private fun approvePendingJoin(marker: String) {
        // The owner may currently be on Secrets. Open Devices proactively: this
        // screen refreshes its list from state and exposes the pending request.
        marker("ANDROID_OPENING_DEVICES_FOR_JOIN_$marker")
        composeRule.onNodeWithTag("tab-devices").performClick()
        marker("ANDROID_DEVICES_TAB_OPENED_$marker")
        composeRule.waitForTag("pending-device-row", 180_000)
        composeRule.onNodeWithTag("pending-device-row").performClick()
        composeRule.waitForTag("alert-join-request", 30_000)
        composeRule.onNodeWithTag("alert-join-request-accept").performClick()
        composeRule.waitUntil(120_000) {
            composeRule.onAllNodes(hasTestTag("alert-join-request")).fetchSemanticsNodes().isEmpty()
        }
        // Closing the dialog only means that the UI dispatched the request.
        // Do not tell the orchestrator that approval succeeded until Android
        // has received the server state where this candidate is no longer
        // pending. This distinguishes a real membership-write failure from a
        // joiner's missed update.
        marker("ANDROID_JOIN_APPROVAL_DISPATCHED_$marker")
        composeRule.waitUntil(120_000) {
            composeRule.onAllNodes(hasTestTag("pending-device-row")).fetchSemanticsNodes().isEmpty()
        }
        marker(marker)
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
}
