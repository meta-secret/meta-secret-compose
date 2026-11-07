package ui.liveboard

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cafe.adriel.voyager.navigator.Navigator
import core.metaSecretCore.liveboard.LiveboardActionPhase
import core.metaSecretCore.liveboard.LiveboardActionTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ui.scenes.mainscreen.MainScreen

/** Actual shared Card/Root/Session behavior with controlled Core/auth; not real Core/OS E2E. */
class LiveboardRecoveredHideIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var fixture: LiveboardAndroidFixture? = null

    @After fun cleanup() {
        try {
            compose.runOnUiThread {
                val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
                (content?.getChildAt(0) as? ComposeView)?.let { ownedView ->
                    ownedView.setContent {}
                    ownedView.disposeComposition()
                }
            }
        } finally {
            fixture?.close() ?: org.koin.core.context.stopKoin()
        }
    }

    @Test fun testDeliveredRecoveredHideRemainsActionableAfterDone() {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it }
        f.core.recoveryPhase = "unknown"
        f.core.recoveredPrimary("recover")
        f.core.recoveredDisclosure = AndroidRecoveredHideFixture.response
        f.install()
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val session = runBlocking(Dispatchers.IO) { f.session() }
        compose.onNodeWithTag("lb-open-secrets").performClick()
        waitTag("lb-secrets")
        val primary = compose.onNodeWithTag("lb-primary-pass-b").performScrollTo()
        primary.performClick()
        compose.waitUntil(15_000) { f.core.calls.count { it.first == "recover" } == 1 }
        runBlocking(Dispatchers.IO) { session.awaitSettled() }
        assertTrue(session.actions.presentation().any {
            it.target == LiveboardActionTarget.Recover("pass-b") && it.phase == LiveboardActionPhase.Unknown
        })
        // No disclosure: the unchanged pending/Unknown Recover guard still blocks real input.
        primary.assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        assertEquals(listOf("recover" to listOf("pass-b")), f.core.calls.toList())

        f.core.publishRecoveredProjection(AndroidRecoveredHideFixture.member(done = false))
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        primary.assertIsEnabled().performClick()
        compose.waitUntil(15_000) { session.sensitive.state.value.revealed["pass-b"] != null }
        runBlocking(Dispatchers.IO) { session.awaitSettled() }
        assertRecoveredSeed()
        compose.onNodeWithTag("lb-copy-pass-b").assertExists()
        val delivered = session.sensitive.state.value.revealed.getValue("pass-b")
        assertEquals(1, f.auth.prompts.get())
        assertEquals(listOf("recover" to listOf("pass-b"),
            "recovered" to listOf(AndroidRecoveredHideFixture.claim)), f.core.calls.toList())

        f.core.publishRecoveredProjection(AndroidRecoveredHideFixture.member(done = true))
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        assertEquals("done", session.currentMember()?.operations?.single()?.status?.clientStatus)
        assertSame(delivered, session.sensitive.state.value.revealed["pass-b"])
        assertFalse(session.actions.presentation().any {
            it.target == LiveboardActionTarget.Recover("pass-b")
        })
        assertRecoveredSeed()
        compose.onNodeWithTag("lb-copy-pass-b").assertExists()
        val beforeCalls = f.core.calls.toList()
        val beforeReads = f.core.reads.get()
        val beforeAuth = f.auth.prompts.get()
        // R32_RENDERED_HIDE_MUST_REMAIN_ACTIONABLE_AFTER_DONE: actual shared Card semantics/click.
        primary.performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(15_000) { session.sensitive.state.value.revealed["pass-b"] == null }
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        primary.assertIsEnabled()
        assertEquals(beforeCalls, f.core.calls.toList())
        assertEquals(beforeReads, f.core.reads.get())
        assertEquals(beforeAuth, f.auth.prompts.get())
        compose.onNodeWithTag("lb-primary-pass-a").assertIsEnabled()
        f.core.recoveryClaimId = AndroidRecoveredHideFixture.cycleClaims[0]
        primary.performClick()
        compose.waitUntil(15_000) { f.core.calls.count { it.first == "recover" } == 2 }
        runBlocking(Dispatchers.IO) { session.awaitSettled() }
        assertEquals(beforeCalls + ("recover" to listOf("pass-b")), f.core.calls.toList())
        assertEquals(beforeAuth, f.auth.prompts.get())
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
    }

    /** Controlled exact Copy callback; Android OS Clipboard behavior is UNRUN by this method. */
    @Test fun testThreeExplicitRecoverShowDoneHideCyclesPermitNextActualEntry() {
        val copies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val f = LiveboardAndroidFixture(compose.activity, captureCopy = { copies.add(it) }).also { fixture = it }
        val claims = AndroidRecoveredHideFixture.cycleClaims
        f.core.recoveryPhase = "unknown"
        f.core.recoveryClaimId = claims[0]
        f.core.recoveredPrimary("recover")
        f.install()
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val session = runBlocking(Dispatchers.IO) { f.session() }
        compose.onNodeWithTag("lb-open-secrets").performClick(); waitTag("lb-secrets")
        val primary = compose.onNodeWithTag("lb-primary-pass-b").performScrollTo()
        val expectedCalls = mutableListOf<Pair<String, List<String>>>()
        for (cycle in 0 until 3) {
            val claim = claims[cycle]
            f.core.recoveryClaimId = claim
            f.core.recoveredDisclosure = AndroidRecoveredHideFixture.response(claim)
            primary.assertIsEnabled().performClick()
            expectedCalls.add("recover" to listOf("pass-b"))
            compose.waitUntil(15_000) { f.core.calls.count { it.first == "recover" } == cycle + 1 }
            runBlocking(Dispatchers.IO) { session.awaitSettled() }
            assertEquals(expectedCalls, f.core.calls.toList())
            assertEquals(cycle, f.auth.prompts.get())
            assertTrue(session.actions.presentation().any {
                it.target == LiveboardActionTarget.Recover("pass-b") && it.phase == LiveboardActionPhase.Unknown
            })
            // Preserve the no-value Unknown denial before any canonical terminal proof.
            primary.assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
            compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
            assertEquals(expectedCalls, f.core.calls.toList())

            f.core.publishRecoveredProjection(AndroidRecoveredHideFixture.member(false, claim))
            runBlocking(Dispatchers.IO) { f.proof() }
            compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
            primary.assertIsEnabled().performClick()
            compose.waitUntil(15_000) { session.sensitive.state.value.revealed["pass-b"] != null }
            runBlocking(Dispatchers.IO) { session.awaitSettled() }
            expectedCalls.add("recovered" to listOf(claim))
            assertEquals(expectedCalls, f.core.calls.toList())
            assertEquals(cycle + 1, f.auth.prompts.get())
            assertRecoveredSeed(); assertExactCopy(f, session, copies)
            val delivered = session.sensitive.state.value.revealed.getValue("pass-b")

            f.core.publishRecoveredProjection(AndroidRecoveredHideFixture.member(true, claim))
            runBlocking(Dispatchers.IO) { f.proof() }
            compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
            val operation = requireNotNull(session.currentMember()).operations.single()
            assertEquals(claim, operation.identity.requestId)
            assertEquals("done", operation.status.clientStatus)
            assertEquals("waiting", operation.status.readiness)
            assertEquals("accepted", operation.status.outcome)
            assertEquals("verified", operation.status.resolutionSource)
            assertSame(delivered, session.sensitive.state.value.revealed["pass-b"])
            assertRecoveredSeed(); assertExactCopy(f, session, copies)
            val reads = f.core.reads.get(); val auth = f.auth.prompts.get()
            primary.assertIsEnabled().performClick()
            compose.waitUntil(15_000) { session.sensitive.state.value.revealed["pass-b"] == null }
            compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
            compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
            assertNull(session.sensitive.copyValue("pass-b"))
            assertEquals(expectedCalls, f.core.calls.toList())
            assertEquals(reads, f.core.reads.get()); assertEquals(auth, f.auth.prompts.get())
            // R32_NEXT_EXPLICIT_RECOVER_AFTER_EXACT_DONE: first causal baseline failure.
            primary.performScrollTo().assertIsEnabled()
            assertFalse(session.actions.presentation().any { it.target == LiveboardActionTarget.Recover("pass-b") })
        }
        // Actually dispatch the next entry after the third completed cycle as well.
        f.core.recoveryClaimId = claims[3]
        primary.performClick(); expectedCalls.add("recover" to listOf("pass-b"))
        compose.waitUntil(15_000) { f.core.calls.count { it.first == "recover" } == 4 }
        runBlocking(Dispatchers.IO) { session.awaitSettled() }
        assertEquals(expectedCalls, f.core.calls.toList()); assertEquals(3, f.auth.prompts.get())
        assertEquals(List(6) { AndroidRecoveredHideFixture.value }, copies.toList())
        primary.assertIsNotEnabled()
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-primary-pass-a").assertIsEnabled()
    }

    private fun assertExactCopy(f: LiveboardAndroidFixture, session: core.metaSecretCore.liveboard.LiveboardSession,
        copies: List<String>) {
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        compose.runOnIdle { assertEquals(AndroidRecoveredHideFixture.value, session.sensitive.copyValue("pass-b")) }
        val before = copies.size
        compose.onNodeWithTag("lb-copy-pass-b").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("Each actual rendered Copy click dispatches exactly one body", before + 1, copies.size)
            assertEquals(AndroidRecoveredHideFixture.value, copies.last())
        }
    }

    private fun assertRecoveredSeed() {
        val children = compose.onNodeWithTag("lb-value-pass-b").onChildren()
        val expected = listOf("01", "one", "02", "two", "03", "three", "04", "four",
            "05", "five", "06", "six", "07", "seven", "08", "eight", "09", "nine",
            "10", "ten", "11", "eleven", "12", "twelve")
        children.assertCountEquals(24)
        expected.forEachIndexed { index, text -> children[index].assertTextEquals(text) }
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
