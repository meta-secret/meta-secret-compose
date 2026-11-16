package metasecret.project.com

import android.util.Log
import models.apiModels.ClientStatus
import org.json.JSONObject
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

@RunWith(AndroidJUnit4::class)
class CaseOneJoinFromAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun joinApprovedVaultAndWaitForRecoveryCompletion() {
        val vaultName = instrumentationArgument("vaultName", "test@test.ru")
        val secretName = instrumentationArgument("secretName", "test-secret")
        composeRule.orbitEnterVaultName(vaultName)
        composeRule.orbitRequestJoin()
        marker("ANDROID_JOIN_REQUEST_SENT")
        val passId = composeRule.lbSecret(secretName)
        val canonicalSecret = composeRule.lbWaitCanonicalSecret(passId, secretName)
        marker("ANDROID_MAIN_AFTER_APPROVE")
        marker("ANDROID_SECRET_VISIBLE")
        val recoveryCycles = instrumentationArgument("recoveryCycles", "18").toInt()
        val approvalCycles = instrumentationArgument("androidRecoveryApprovals", "7,8,9,10,11,12,14,16,18")
            .split(',').mapNotNull(String::toIntOrNull).toSet()
        val coordinator = instrumentationArgument("approvalCoordinatorUrl", "http://10.0.2.2:5180")
        val stateDriven = instrumentationArgument("stateDrivenRecoveryWait", "false") == "true"
        val seenClaimIds = mutableSetOf<String>()
        for (cycle in 1..recoveryCycles) {
            if (stateDriven) {
                waitForApproval(coordinator, "android-state", cycle)
                marker("ANDROID_RECOVERY_STATE_PENDING_$cycle")
            }
            val incoming = try {
                composeRule.lbIncoming("Recover", secretName)
            } catch (failure: Throwable) {
                marker("ANDROID_RECOVERY_BADGE_DIAGNOSTIC_$cycle actualHeadPresent=${composeRule.lbVisibleIncoming() != null} actualCount=${composeRule.lbIncomingCount()}")
                throw failure
            }
            val observed = composeRule.lbObserveCanonicalRecovery(canonicalSecret, incoming.requestId, seenClaimIds)
            seenClaimIds.add(incoming.requestId)
            marker("ANDROID_RECOVERY_REQUEST_ALERT_$cycle")
            if (cycle in approvalCycles) {
                waitForApproval(coordinator, "android", cycle)
                composeRule.lbDecide(incoming, true)
                marker("ANDROID_RECOVERY_APPROVE_SUCCESS_$cycle")
            }
            val completedRecoveryClaims = waitForCoreRecoveryCompletion(observed, cycle)
            marker("ANDROID_RECOVERY_CORE_DONE_$cycle completedRecoveryClaims=$completedRecoveryClaims")
            composeRule.waitUntil(180_000) {
                !composeRule.lbHas("lb-inbox-head-Recover-${incoming.requestId}") &&
                    composeRule.lbIncomingCount() == incoming.count - 1
            }
            marker("ANDROID_RECOVERY_REQUEST_CLOSED_$cycle")
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

    /** Observe actual current successful cache publications; this passive barrier never calls Core. */
    private fun waitForCoreRecoveryCompletion(observed: LiveboardCanonicalRecovery, cycle: Int): Int {
        var completed: Int? = null
        var sample = recoveryCompletionSample(cycle)
        try {
            composeRule.waitUntil(180_000) {
                sample = recoveryCompletionSample(cycle)
                val view = lbReadCanonical(observed.secret.scope)
                completed = view?.let { lbCompletedReceiverRecoveries(it, observed, cycle) }
                runCatching {
                    sample["VIEW_AVAILABLE"] = if (view == null) 0 else 1
                    if (view != null) {
                        sample["NEWER"] = if (view.frame.attempt > observed.afterAttempt) 1 else 0
                        val claim = view.member.ssClaims?.claims?.get(observed.claimId)
                        sample["CLAIM_PRESENT"] = if (claim == null) 0 else 1
                        sample["CLAIM_STATUS"] = when (claim?.clientStatus) {
                            ClientStatus.PENDING -> "PENDING"
                            ClientStatus.NEED_APPROVE -> "NEED_APPROVE"
                            ClientStatus.ACCEPTED -> "ACCEPTED"
                            ClientStatus.DECLINED -> "DECLINED"
                            ClientStatus.DONE -> "DONE"
                            null -> "UNKNOWN"
                        }
                    }
                    sample["COMPLETED"] = completed ?: "UNKNOWN"
                }
                completed != null
            }
        } catch (failure: Throwable) {
            runCatching { recordRecoveryCompletion(sample) }
            throw failure
        }
        return checkNotNull(completed)
    }

    private fun recoveryCompletionSample(cycle: Int): MutableMap<String, Any> = mutableMapOf(
        "VERSION" to 1, "PHASE" to "RECOVERY_COMPLETION_WAIT_FAILURE", "REQUIRED_CYCLE" to cycle,
        "VIEW_AVAILABLE" to "UNKNOWN", "NEWER" to "UNKNOWN", "CLAIM_PRESENT" to "UNKNOWN",
        "CLAIM_STATUS" to "UNKNOWN", "COMPLETED" to "UNKNOWN", "INNER_GUARDS" to "UNOBSERVED"
    )

    private fun recordRecoveryCompletion(sample: Map<String, Any>) {
        val prefix = "LIVEBOARD_RECOVERY_OBSERVATION "
        val record = prefix + JSONObject(sample).toString()
        val safe = if (record.toByteArray(Charsets.UTF_8).size + 1 <= 512) record else
            prefix + "{\"VERSION\":1,\"STATUS\":\"INCONCLUSIVE\",\"REASON\":\"STDOUT_LIMIT\"}"
        Log.i("MetaSecretE2E", safe)
    }

    private fun marker(message: String) = Log.i("MetaSecretE2E", "E2E: $message")

    private fun waitForApproval(coordinatorUrl: String, platform: String, cycle: Int) {
        val deadline = System.currentTimeMillis() + 120_000
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
        error("Timed out waiting for orchestrator approval for Android recovery cycle $cycle")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMillis: Long) {
        var layoutRaceRetries = 0
        while (true) {
            try {
                waitUntil(timeoutMillis) {
                    onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
                }
                return
            } catch (failure: Throwable) {
                if (layoutRaceRetries >= 2 || !failure.hasComposeMeasureLayoutRace()) throw failure
                layoutRaceRetries += 1
            }
        }
    }

    private fun Throwable.hasComposeMeasureLayoutRace(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is IllegalArgumentException &&
                it.message?.contains("performMeasureAndLayout called during measure layout") == true
        }
}
