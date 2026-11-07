package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import java.util.WeakHashMap

internal class LiveboardTestScope {
    val secretIds = mutableMapOf<String, String>()
    var selectedPass: String? = null
    var incoming: LiveboardTestIncoming? = null
    var previewDevice: String? = null
    val completedRequests = mutableSetOf<String>()
}

private val liveboardScopes = WeakHashMap<ComposeTestRule, LiveboardTestScope>()
internal fun ComposeTestRule.lbScope(): LiveboardTestScope = synchronized(liveboardScopes) {
    liveboardScopes.getOrPut(this) { LiveboardTestScope() }
}
internal fun lbNodeTag(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
internal fun lbNodeTexts(node: SemanticsNode): List<String> =
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::lbNodeTexts)
internal fun ComposeTestRule.lbTagged(prefix: String): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher("Liveboard tag prefix") { lbNodeTag(it).startsWith(prefix) }, useUnmergedTree = true).fetchSemanticsNodes()
internal fun ComposeTestRule.lbCount(tag: String): Int =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
/** Presence is nonzero; negating this can never mistake duplicate nodes for absence. */
internal fun ComposeTestRule.lbHas(tag: String): Boolean = lbCount(tag) > 0
internal fun ComposeTestRule.lbAbsent(tag: String): Boolean = lbCount(tag) == 0
internal fun ComposeTestRule.lbExactlyOne(tag: String): Boolean = lbCount(tag).let { count ->
    check(count <= 1) { "Ambiguous current UI identity; matching nodes=$count" }
    count == 1
}
internal fun ComposeTestRule.lbWait(tag: String, timeout: Long = 180_000) {
    waitUntil(timeout) { lbExactlyOne(tag) }
}
internal fun ComposeTestRule.lbWaitMain(timeout: Long = 180_000) {
    waitUntil(timeout) {
        lbCompleteEntryAuthenticationIfPresent()
        lbExactlyOne("lb-home") || lbExactlyOne("lb-secrets") || lbExactlyOne("lb-profile")
    }
}
internal fun ComposeTestRule.lbGone(tag: String, timeout: Long = 180_000) {
    waitUntil(timeout) { lbAbsent(tag) }
}
internal fun ComposeTestRule.lbTap(tag: String, expectedLabel: String? = null) {
    var diagnosticFailed = false
    fun diagnosticMarker(message: String) {
        if (tag == "lb-inbox") runCatching { println(message) }
    }
    fun <T> phase(name: String, operation: () -> T): T {
        if (tag != "lb-inbox") return operation()
        diagnosticMarker("E2E: ANDROID_INBOX_TAP_PHASE_${name}_ENTER")
        try {
            val result = operation()
            if (name != "SCROLL") diagnosticMarker("E2E: ANDROID_INBOX_TAP_PHASE_${name}_EXIT")
            return result
        } catch (failure: Throwable) {
            if (!diagnosticFailed) {
                diagnosticFailed = true
                diagnosticMarker("E2E: ANDROID_INBOX_TAP_PHASE_${name}_FAIL")
            }
            throw failure
        }
    }
    val started = System.nanoTime()
    fun remaining(): Long = (180_000 - (System.nanoTime() - started) / 1_000_000).also {
        check(it > 0) { "Current control readiness deadline expired" }
    }
    require(!tag.startsWith("lb-primary-") || expectedLabel != null) { "A primary action requires its expected label" }
    val originalHead = if (tag.startsWith("lb-approve-") || tag.startsWith("lb-decline-")) {
        checkNotNull(lbVisibleIncoming()) { "A decision requires its actual current head" }.also { head ->
            check(tag == "lb-approve-${head.requestId}" || tag == "lb-decline-${head.requestId}")
        }
    } else null
    phase("PRESENCE") { lbWait(tag, remaining()) }
    runCatching { phase("SCROLL") { onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() } }
    diagnosticMarker("E2E: ANDROID_INBOX_TAP_PHASE_SCROLL_ATTEMPT_FINISHED")
    phase("READINESS") {
        waitUntil(remaining()) {
            if (!lbExactlyOne(tag)) false else runCatching {
                onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
                expectedLabel == null || lbActionText(tag.removePrefix("lb-primary-")) == expectedLabel
            }.getOrDefault(false)
        }
    }
    if (tag.startsWith("lb-primary-")) {
        var readyControl: SemanticsNodeInteraction? = null
        waitUntil(remaining()) {
            check(lbExactlyOne(tag)) { "Current control disappeared after readiness" }
            val control = onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            val node = control.fetchSemanticsNode()
            check(lbNodeTag(node) == tag) { "Current primary identity changed after readiness" }
            check(lbNodeTexts(node).joinToString(" ") == expectedLabel) {
                "Current primary action changed after readiness"
            }
            val enabled = node.config.getOrNull(SemanticsProperties.Disabled) == null
            if (enabled) readyControl = control
            enabled
        }
        remaining()
        checkNotNull(readyControl).performClick()
        return
    }
    if (originalHead != null) {
        check(lbVisibleIncoming() == originalHead) { "Current request changed during readiness" }
        lbBindCurrentIncoming(originalHead)
    }
    val control = phase("FINAL_GUARDS") {
        check(lbExactlyOne(tag)) { "Current control disappeared after readiness" }
        val control = onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
        check(expectedLabel == null || lbActionText(tag.removePrefix("lb-primary-")) == expectedLabel) {
            "Current primary action changed after readiness"
        }
        if (originalHead != null) check(lbVisibleIncoming() == originalHead) { "Current request changed before action" }
        remaining()
        control
    }
    phase("CLICK") { control.performClick() }
}
internal fun ComposeTestRule.lbHome() {
    lbWaitMain()
    if (lbHas("lb-secrets-back")) lbTap("lb-secrets-back")
    if (lbHas("lb-profile-back")) lbTap("lb-profile-back")
    lbWait("lb-home")
    lbScope().previewDevice = null
}
internal fun ComposeTestRule.lbSecrets() {
    if (lbAbsent("lb-secrets")) {
        lbHome()
        lbTap("lb-open-secrets")
    }
    lbWait("lb-secrets")
}
internal fun ComposeTestRule.lbFindSecret(name: String): String {
    val matches = lbTagged("lb-secret-name-").filter { lbNodeTexts(it) == listOf(name) }
    check(matches.size == 1) { "Expected one exact rendered Secret name; count=${matches.size}" }
    return lbNodeTag(matches.single()).removePrefix("lb-secret-name-").also {
        check(it.isNotBlank()) { "Secret lacks its opaque identity" }
        lbScope().secretIds[name] = it
    }
}
internal fun ComposeTestRule.lbSecret(name: String, timeout: Long = 180_000): String {
    lbSecrets()
    val prior = lbScope().secretIds[name]
    if (prior != null && lbHas("lb-secret-name-$prior")) {
        check(lbNodeTexts(onNodeWithTag("lb-secret-name-$prior", useUnmergedTree = true).fetchSemanticsNode()) == listOf(name))
        return prior
    }
    // Search is actual UI. Never change it while an existing disclosure is being verified.
    if (lbTagged("lb-value-").isEmpty()) {
        onNodeWithTag("lb-search", useUnmergedTree = true).performTextReplacement(name)
    }
    waitUntil(timeout) { lbTagged("lb-secret-name-").any { lbNodeTexts(it) == listOf(name) } }
    return lbFindSecret(name)
}
internal fun ComposeTestRule.lbActionText(id: String): String =
    lbNodeTexts(onNodeWithTag("lb-primary-$id", useUnmergedTree = true).fetchSemanticsNode()).joinToString(" ")
internal fun ComposeTestRule.lbConcealed(id: String): Boolean = lbAbsent("lb-value-$id") && lbAbsent("lb-copy-$id")
internal fun ComposeTestRule.lbValueAbsent(id: String) {
    check(lbConcealed(id)) { "Concealed Secret exposes private content or Copy" }
}
