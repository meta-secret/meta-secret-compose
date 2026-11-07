package metasecret.project.com

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Exact existing scenario parsing, extracted only to keep the changed driver below400 lines. */
internal class CaseFourScenarioPlan(private val instrumentationArgument: (String, String) -> String) {
    data class RecoveryCycle(
        val number: Int,
        val block: String,
        val senderSecrets: Map<String, String>,
        val approvals: List<RecoveryApproval>,
    ) {
        val senders: Set<String> get() = senderSecrets.keys
        val sender: String get() = senderSecrets.keys.firstOrNull() ?: ""
        val secretName: String get() = approvals.firstOrNull()?.secret
            ?: senderSecrets.values.firstOrNull()
            ?: "test-secret"
    }

    data class RecoveryApproval(
        val platform: String,
        val secret: String,
    )

    data class SecretDefinition(
        val name: String,
        val value: String,
    )

    fun recoveryCyclePlan(): List<RecoveryCycle> {
        val rawPlan = instrumentationArgument("cyclePlan", "[]")
        val json = runCatching { JSONArray(rawPlan) }.getOrElse { error("Invalid cyclePlan: ${it.message}") }
        val defaultSecret = secretDefinitions().first().name
        return (0 until json.length()).map { index ->
            val cycle = json.getJSONObject(index)
            val senderSecrets = buildMap {
                val explicitSecrets = cycle.optJSONObject("senderSecrets")
                val senders = cycle.optJSONArray("senders") ?: JSONArray()
                for (senderIndex in 0 until senders.length()) {
                    val sender = senders.get(senderIndex)
                    val platform = if (sender is JSONObject) {
                        sender.getString("platform")
                    } else {
                        sender.toString()
                    }
                    put(platform, explicitSecrets?.optString(platform, defaultSecret)
                        ?: if (sender is JSONObject) sender.optString("secret", defaultSecret)
                        else defaultSecret)
                }
                // Keep compatibility with plans that carry senderSecrets but
                // omit the redundant senders array.
                explicitSecrets?.keys()?.forEach { platform ->
                    put(platform, explicitSecrets.optString(platform, defaultSecret))
                }
            }
            val approvals = if (cycle.has("approvals")) {
                val rawApprovals = cycle.getJSONArray("approvals")
                (0 until rawApprovals.length()).map { approvalIndex ->
                    val approval = rawApprovals.get(approvalIndex)
                    if (approval is JSONObject) {
                        RecoveryApproval(
                            platform = approval.optString("platform", approval.optString("approver")),
                            secret = approval.optString("secret", defaultSecret),
                        )
                    } else {
                        RecoveryApproval(approval.toString(), defaultSecret)
                    }
                }
            } else {
                listOf(
                    RecoveryApproval(cycle.getString("firstApprover"), defaultSecret),
                    RecoveryApproval(cycle.getString("secondApprover"), defaultSecret),
                )
            }
            RecoveryCycle(
                number = cycle.getInt("number"),
                block = cycle.optString("block", ""),
                senderSecrets = senderSecrets,
                approvals = approvals,
            ).also { parsed ->
                Log.i(
                    "MetaSecretE2E",
                    "E2E: ANDROID_RECOVERY_PLAN_${parsed.number} "
                        + "senders=${parsed.senderSecrets} approvals="
                        + parsed.approvals.joinToString { "${it.platform}:${it.secret}" },
                )
            }
        }
    }

    fun secretDefinitions(): List<SecretDefinition> {
        val fallbackName = instrumentationArgument("secretName", "test-secret")
        val fallbackValue = instrumentationArgument("secretValue", "test-secret-value")
        val fallback = "{\"default\":{\"name\":\"$fallbackName\",\"value\":\"$fallbackValue\"}}"
        val json = runCatching {
            JSONObject(instrumentationArgument("secretConfig", fallback))
        }.getOrElse { error("Invalid secretConfig: ${it.message}") }
        val result = mutableListOf<SecretDefinition>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.getJSONObject(key)
            result += SecretDefinition(
                name = value.getString("name"),
                value = value.getString("value"),
            )
        }
        return result.ifEmpty { listOf(SecretDefinition(fallbackName, fallbackValue)) }
    }

    fun initialSecretsToCreate(allSecrets: List<SecretDefinition>): List<SecretDefinition> {
        val rawPlan = instrumentationArgument("secretCreationPlan", "")
        if (rawPlan.isBlank() || rawPlan == "null") return allSecrets

        val plan = runCatching { JSONObject(rawPlan) }
            .getOrElse { error("Invalid secretCreationPlan: ${it.message}") }
        val initial = plan.optJSONObject("initial") ?: return emptyList()
        val names = initial.optJSONArray("android")?.let { rawNames ->
            (0 until rawNames.length()).map { rawNames.getString(it) }
        }.orEmpty()
        val knownNames = allSecrets.map { it.name }.toSet()
        val unknownNames = names.filterNot(knownNames::contains)
        check(unknownNames.isEmpty()) {
            "secretCreationPlan.initial.android contains unknown secrets: ${unknownNames.joinToString(",")}"
        }
        return allSecrets.filter { it.name in names }
    }

}
