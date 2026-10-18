package metasecret.project.com

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

/** Shared UI-only setup. Business markers remain in their owning scenarios. */
internal fun AndroidComposeTestRule<*, *>.orbitEnterVaultName(name: String) {
    waitUntil(30_000) { orbitHasTag("onboarding-skip") || orbitHasTag("orbit-enter") }
    if (orbitHasTag("onboarding-skip")) orbitTap("onboarding-skip")
    orbitTap("orbit-enter")
    orbitWaitForTag("orbit-email-input")
    onNodeWithTag("orbit-email-input", useUnmergedTree = true).performTextReplacement(name)
    orbitTap("orbit-ok")
    orbitWaitForTag("orbit-phase-idle", 60_000)
}

internal fun AndroidComposeTestRule<*, *>.orbitCreateVault() {
    orbitWaitForTag("orbit-transition-ready")
    onNodeWithTag("orbit-create", useUnmergedTree = true).assertIsEnabled()
    orbitTap("orbit-create")
    orbitWaitForTag("tab-secrets", 180_000)
}

internal fun AndroidComposeTestRule<*, *>.orbitRequestJoin(awaitPending: Boolean = true) {
    orbitTap("orbit-join")
    orbitTap("orbit-request")
    if (awaitPending) orbitWaitForTag("orbit-phase-pending", 180_000)
}

internal fun AndroidComposeTestRule<*, *>.orbitTap(tag: String) {
    waitUntil(60_000) {
        (tag == "onboarding-skip" || orbitHasTag("orbit-transition-ready")) && orbitHasTag(tag) && runCatching {
            onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
        }.isSuccess
    }
    onNodeWithTag(tag, useUnmergedTree = true).performClick()
}

internal fun AndroidComposeTestRule<*, *>.orbitWaitForTag(tag: String, timeoutMillis: Long = 30_000) {
    waitUntil(timeoutMillis) { orbitHasTag(tag) }
}

private fun AndroidComposeTestRule<*, *>.orbitHasTag(tag: String): Boolean =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1
