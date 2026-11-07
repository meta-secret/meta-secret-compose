package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule

import androidx.compose.ui.test.*
import core.AppString
import core.StringProviderInterface
import org.koin.core.context.GlobalContext

internal fun lbLabel(key: AppString): String = GlobalContext.get().get<StringProviderInterface>().getString(key)

internal fun ComposeTestRule.lbCreatePlain(name: String, value: String): String {
    lbHome()
    lbTap("lb-open-create")
    lbWait("lb-create-name")
    onNodeWithTag("lb-create-name", useUnmergedTree = true).performTextReplacement(name)
    onNodeWithTag("lb-create-value", useUnmergedTree = true).performTextReplacement(value)
    lbTap("lb-create-submit")
    lbApproveSystemIfPresent()
    lbGone("lb-create", 180_000)
    lbScope().secretIds.remove(name)
    return lbSecret(name)
}

internal fun ComposeTestRule.lbWaitPrimary(name: String, expected: String, timeout: Long = 180_000): String {
    var observed = 0
    fun note(bit: Int, phase: String, enabled: String, label: String, failure: Throwable?) {
        if ((observed and bit) != 0) return
        observed = observed or bit
        runCatching {
            val idState = if (bit == 1) "UNKNOWN" else "RESOLVED"
            val failureState = when (failure) {
                null -> "NONE"
                is AssertionError -> "ASSERTION"
                else -> "OTHER"
            }
            android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_PRIMARY_WAIT_OBSERVATION " +
                "VERSION=1 COVERAGE=FIRST_OBSERVED_REFUSAL PHASE=$phase ID=$idState " +
                "ENABLE_ASSERT=$enabled LABEL=$label FAILURE=$failureState")
        }
    }
    val id = try {
        lbSecret(name, timeout)
    } catch (failure: Throwable) {
        note(1, "ID_RESOLUTION", "UNOBSERVED", "UNCONSULTED", failure)
        throw failure
    }
    lbScope().selectedPass = id
    waitUntil(timeout) {
        var phase = "ENABLE_ASSERT"
        val result = runCatching {
            onNodeWithTag("lb-primary-$id", useUnmergedTree = true).assertIsEnabled()
            phase = "LABEL_READ"
            val matches = lbActionText(id) == expected
            if (!matches) note(8, "LABEL_MISMATCH", "RETURNED", "FALSE", null)
            matches
        }
        if (result.isFailure) {
            if (phase == "ENABLE_ASSERT") {
                note(2, phase, "THREW", "UNCONSULTED", result.exceptionOrNull())
            } else {
                note(4, phase, "RETURNED", "UNKNOWN", result.exceptionOrNull())
            }
        }
        result.getOrDefault(false)
    }
    return id
}

internal fun ComposeTestRule.lbShow(name: String, expectedValue: String? = null): String {
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_SECRET_ENTER") }
    val id = lbSecret(name)
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_SECRET_RETURNED") }
    lbScope().selectedPass = id
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_VALUE_PRESENCE_ENTER") }
    val valueAbsent = lbAbsent("lb-value-$id")
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_VALUE_PRESENCE_RETURNED") }
    if (valueAbsent) {
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_PRIMARY_READY_ENTER") }
        lbWaitPrimary(name, lbLabel(AppString.lbShow))
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_PRIMARY_READY_RETURNED") }
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_TAP_ENTER") }
        lbTap("lb-primary-$id", lbLabel(AppString.lbShow))
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_TAP_RETURNED") }
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_SYSTEM_APPROVAL_ENTER") }
        lbApproveSystemIfPresent()
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_SYSTEM_APPROVAL_RETURNED") }
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_AFTER_INPUT_VALUE_WAIT_ENTER") }
        lbWait("lb-value-$id")
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_AFTER_INPUT_VALUE_WAIT_RETURNED") }
    }
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_FINAL_VALUE_WAIT_ENTER") }
    lbWait("lb-value-$id")
    runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_FINAL_VALUE_WAIT_RETURNED") }
    if (expectedValue != null) {
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_VALUE_ASSERT_ENTER") }
        lbAssertValue(id, expectedValue)
        runCatching { android.util.Log.i("MetaSecretE2E", "E2E: ANDROID_SHOW_PHASE_VALUE_ASSERT_RETURNED") }
    }
    return id
}

internal fun ComposeTestRule.lbAssertValue(id: String, expected: String) {
    lbWait("lb-value-$id")
    val texts = lbNodeTexts(onNodeWithTag("lb-value-$id", useUnmergedTree = true).fetchSemanticsNode())
    val plainMatches = texts == listOf(expected)
    val numbered = texts.chunked(2)
    val seedMatches = numbered.isNotEmpty() && numbered.all { it.size == 2 } &&
        numbered.mapIndexed { index, pair -> pair[0] == (index + 1).toString().padStart(2, '0') }.all { it } &&
        numbered.map { it[1] } == expected.trim().split(Regex("\\s+"))
    check(plainMatches || seedMatches) { "Authorized exact value/order mismatch (redacted)" }
}

internal fun ComposeTestRule.lbHide(id: String, timeoutMillis: Long = 30_000) {
    check(lbExactlyOne("lb-value-$id")) { "Hide requires the original disclosure, never a replacement Show" }
    lbTap("lb-primary-$id", lbLabel(AppString.lbHide))
    waitUntil(timeoutMillis) { lbConcealed(id) }
}

/** Navigation after a pending request never invokes Recover again or cancels its Core request. */
internal fun ComposeTestRule.lbCloseCurrentRevealOrPending() {
    val visible = lbTagged("lb-value-")
    check(visible.size <= 1) { "Legacy single-disclosure actor has multiple current values" }
    val id = visible.singleOrNull()?.let { lbNodeTag(it).removePrefix("lb-value-") }
    if (id != null) lbHide(id)
    else if (lbHas("lb-secrets-back")) lbHome()
}

internal fun ComposeTestRule.lbRequestRecovery(name: String): String {
    val id = lbWaitPrimary(name, lbLabel(AppString.recover))
    lbValueAbsent(id)
    lbTap("lb-primary-$id", lbLabel(AppString.recover))
    waitUntil(180_000) {
        runCatching {
            onNodeWithTag("lb-primary-$id", useUnmergedTree = true).assertIsNotEnabled()
            onNodeWithTag("lb-delete-$id", useUnmergedTree = true).assertIsNotEnabled()
        }.isSuccess
    }
    return id
}

internal fun ComposeTestRule.lbRevealAccepted(name: String, expectedValue: String? = null): String = lbShow(name, expectedValue)

internal fun ComposeTestRule.lbSecretAbsent(id: String, name: String) {
    lbSecrets()
    check(lbConcealed(id)) { "Deleted Secret retained private content" }
    onNodeWithTag("lb-search", useUnmergedTree = true).performTextReplacement(name)
    waitUntil(180_000) { lbAbsent("lb-secret-$id") }
    check(lbTagged("lb-secret-name-").none { lbNodeTexts(it) == listOf(name) }) { "Deleted Secret remains in the filtered list" }
}
