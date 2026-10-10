package metasecret.project.com

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import core.AppStateCacheProviderInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import models.apiModels.DistributionType
import models.apiModels.VaultFullInfo
import org.koin.core.context.GlobalContext
import java.security.MessageDigest

// Test-only, bounded evidence. Reads cached StateFlows, never calls native sync,
// logs names/values, dumps UI trees, or changes the badge assertion/deadline.
internal class RecoveryUiDiagnosticProbe(private val secretName: String) {
    private fun hashName(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private val nameHash = hashName(secretName)
    private val baselineIds = cachedClaimIds()
    private var reportedPendingIds: Set<String>? = null

    private fun cachedClaimIds(): Set<String> = runCatching {
        val state = GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value
        val member = state?.getVaultFullInfo() as? VaultFullInfo.Member
        member?.member?.ssClaims?.claims?.values.orEmpty()
            .filter { it.distributionType == DistributionType.RECOVER && it.distClaimId.passId.name == secretName }
            .map { it.id }.toSet()
    }.getOrDefault(emptySet())

    private fun pendingIds(): Set<String> = runCatching {
        GlobalContext.get().get<MetaSecretSocketHandlerInterface>().pendingRecoveryRequests.value
            .filter { it.secretId == secretName }.map { it.claimId }.toSet()
    }.getOrDefault(emptySet())

    fun hasNewPendingEvidence(): Boolean {
        val current = pendingIds()
        if (current.isEmpty() || current == reportedPendingIds) return false
        reportedPendingIds = current
        return true
    }

    fun capture(rule: AndroidComposeTestRule<*, *>, phase: String, cycle: String, step: String) {
        val prefix = "E2E: ANDROID_RECOVERY_UI_DIAGNOSTIC cycle=$cycle step=$step phase=$phase selectorHash=$nameHash"
        runCatching {
            val requests = GlobalContext.get().get<MetaSecretSocketHandlerInterface>().pendingRecoveryRequests.value
            val matching = requests.filter { it.secretId == secretName }
            val state = GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value
            val member = state?.getVaultFullInfo() as? VaultFullInfo.Member
            val claims = member?.member?.ssClaims?.claims?.values.orEmpty()
                .filter { it.distributionType == DistributionType.RECOVER && it.distClaimId.passId.name == secretName }
            val newClaims = claims.filter { it.id !in baselineIds }
            Log.i("MetaSecretE2E", "$prefix pendingTotal=${requests.size} nameEqualityMatches=${matching.size} " +
                "pendingSelectorHashes=${requests.take(12).map { hashName(it.secretId) }} " +
                "exactPendingClaimIds=${matching.map { it.claimId }.take(12)} " +
                "newClaimIds=${newClaims.map { it.id }.take(12)} " +
                "matchingCoreStatuses=${claims.take(12).map { "${it.id}:${it.clientStatus}" }}")
        }.onFailure {
            Log.i("MetaSecretE2E", "$prefix cachedStateUnavailable=${it.javaClass.simpleName}")
        }
        runCatching {
            val rows = rule.onAllNodes(SemanticsMatcher("secret rows only") { node ->
                runCatching { node.config[SemanticsProperties.TestTag].startsWith("secret-row-") }.getOrDefault(false)
            }, useUnmergedTree = true).fetchSemanticsNodes()
            val rowHashes = rows.take(12).map { hashName(it.config[SemanticsProperties.TestTag].removePrefix("secret-row-")) }
            Log.i("MetaSecretE2E", "$prefix composedRows=${rows.size} rowSelectorHashes=$rowHashes")
        }.onFailure {
            Log.i("MetaSecretE2E", "$prefix rowMappingUnavailable=${it.javaClass.simpleName}")
        }
        val selectors = listOf(
            "tab-secrets" to "tab-secrets", "tab-devices" to "tab-devices",
            "selected-row" to "secret-row-$secretName",
            "badge" to "recovery-request-badge-$secretName",
            "open" to "open-recovery-request-$secretName",
            "alert" to "alert-recovery-request",
            "processing" to "alert-recovery-request-processing",
        )
        for ((label, tag) in selectors) {
            runCatching {
                val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
                val displayed = nodes.size == 1 && runCatching {
                    rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
                }.isSuccess
                val bounds = nodes.take(2).map { it.boundsInRoot }
                val selected = nodes.take(2).map { runCatching { it.config[SemanticsProperties.Selected] }.getOrNull() }
                Log.i("MetaSecretE2E", "$prefix selector=$label exists=${nodes.size} displayed=$displayed bounds=$bounds selected=$selected")
            }.onFailure {
                Log.i("MetaSecretE2E", "$prefix selector=$label unavailable=${it.javaClass.simpleName}")
            }
        }
    }
}

internal fun AndroidComposeTestRule<*, *>.waitForRecoveryBadgeWithDiagnostics(
    secretName: String, timeoutMillis: Long, cycle: String, step: String, probe: RecoveryUiDiagnosticProbe,
) {
    try {
        waitUntil(timeoutMillis) {
            if (probe.hasNewPendingEvidence()) probe.capture(this, "pending-flow-observed", cycle, step)
            onAllNodes(hasTestTag("recovery-request-badge-$secretName"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    } catch (error: Throwable) {
        probe.capture(this, "badge-gate-failed", cycle, step)
        throw error
    }
}
