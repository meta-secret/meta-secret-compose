package metasecret.project.com

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertSame
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Actual Compose semantics contracts; not physical touch or Core submission proof. */
class LiveboardRecoverSemanticsActionTest {
    @get:Rule val compose = createComposeRule()
    private val tag = "contract-recover-primary"

    @Test fun currentRecoverInvokesActualActionExactlyOnceOnMain() {
        var calls = 0
        var onMain = false
        compose.setContent {
            Text("Recover", Modifier.testTag(tag).semantics {
                onClick {
                    onMain = Looper.myLooper() === Looper.getMainLooper()
                    calls++
                    true
                }
            })
        }
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsEnabled().assertTextEquals("Recover")
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        compose.runOnUiThread { lbDispatchRecoverSemantics(root, tag, "Recover") { 1000L } }
        assertEquals(1, calls)
        assertTrue(onMain)
    }

    @Test fun currentMutationAfterPriorReadinessRefusesBeforeActualAction() {
        val changed = mutableStateOf(false)
        var calls = 0
        compose.setContent {
            Text(if (changed.value) "SENT" else "Recover", Modifier.testTag(tag).semantics {
                if (changed.value) disabled()
                // Genuine semantics action remains present even when disabled: helper must guard it.
                onClick { calls++; true }
            })
        }
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsEnabled().assertTextEquals("Recover")
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        compose.runOnIdle { changed.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals("SENT")
        var refused = false
        compose.runOnUiThread {
            refused = runCatching { lbDispatchRecoverSemantics(root, tag, "Recover") { 1000L } }.isFailure
        }
        // Causal baseline assertion: missing final guard invokes the actual action once.
        assertEquals(0, calls)
        assertTrue("Current changed control must be refused", refused)
    }

    private fun render(
        label: State<String> = mutableStateOf("Recover"),
        blocked: State<Boolean> = mutableStateOf(false),
        action: (() -> Boolean)? = null,
    ): SemanticsNode {
        compose.setContent {
            Text(label.value, Modifier.testTag(tag).semantics {
                if (blocked.value) disabled()
                if (action != null) onClick { action() }
            })
        }
        return compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    }

    private fun dispatch(root: SemanticsNode, budget: () -> Long = { 1000L }): Throwable? {
        var failure: Throwable? = null
        compose.runOnUiThread {
            failure = runCatching { lbDispatchRecoverSemantics(root, tag, "Recover", budget) }.exceptionOrNull()
        }
        return failure
    }

    @Test fun changedLabelAloneRefusesCurrentAction() {
        var calls = 0
        val label = mutableStateOf("Recover")
        val root = render(label = label, action = { calls++; true })
        compose.runOnIdle { label.value = "SENT" }
        compose.waitForIdle()
        assertTrue(dispatch(root) != null)
        assertEquals(0, calls)
    }

    @Test fun disabledAloneRefusesCurrentAction() {
        var calls = 0
        val blocked = mutableStateOf(false)
        val root = render(blocked = blocked, action = { calls++; true })
        compose.runOnIdle { blocked.value = true }
        compose.waitForIdle()
        assertTrue(dispatch(root) != null)
        assertEquals(0, calls)
    }

    @Test fun duplicateLaterInTraversalNeverInvokesFirstMatch() {
        var calls = 0
        compose.setContent {
            Column {
                Text("Recover", Modifier.testTag(tag).semantics { onClick { calls++; true } })
                Text("Recover", Modifier.testTag(tag).semantics { onClick { calls++; true } })
            }
        }
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(dispatch(root) != null)
        assertEquals(0, calls)
    }

    @Test fun missingCurrentActionRefuses() {
        val root = render()
        assertTrue(dispatch(root) != null)
    }

    @Test fun falseCurrentActionHasOneAttemptAndNeverRetries() {
        var calls = 0
        val root = render(action = { calls++; false })
        assertTrue(dispatch(root) != null)
        assertEquals(1, calls)
    }

    @Test fun throwingCurrentActionPreservesSameThrowableAndOneAttempt() {
        var calls = 0
        val original = IllegalStateException("controlled-action-fault")
        val root = render(action = { calls++; throw original })
        assertSame(original, dispatch(root))
        assertEquals(1, calls)
    }

    @Test fun expiredBudgetNeverInvokesCurrentAction() {
        var calls = 0
        val root = render(action = { calls++; true })
        assertTrue(dispatch(root) { 0L } != null)
        assertEquals(0, calls)
    }

    @Test fun expiryAfterHandledActionRefusesWithoutSecondAttempt() {
        var calls = 0
        var remaining = 1000L
        val root = render(action = { calls++; remaining = 0L; true })
        assertTrue(dispatch(root) { remaining } != null)
        assertEquals(1, calls)
    }

    @Test fun emptyClippedControlNeverInvokes() {
        var calls = 0
        compose.setContent {
            Box(Modifier.size(1.dp).clipToBounds()) {
                Text("Recover", Modifier.offset(100.dp, 100.dp).testTag(tag).semantics {
                    onClick { calls++; true }
                })
            }
        }
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(dispatch(root) != null)
        assertEquals(0, calls)
    }

    @Test fun removedDialogAnchorCannotSelectReplacementWindow() {
        var calls = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            Text("Host")
            if (visible.value) Dialog(onDismissRequest = {}) {
                Text("Recover", Modifier.testTag(tag).semantics { onClick { calls++; true } })
            }
        }
        fun containsTarget(node: SemanticsNode): Boolean =
            node.config.getOrNull(SemanticsProperties.TestTag) == tag || node.children.any(::containsTarget)
        val old = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .single(::containsTarget)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue(dispatch(old) != null)
        assertEquals(0, calls)
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        assertTrue(dispatch(old) != null)
        assertEquals(0, calls)
    }

    @Test fun actionRecompositionStillInvokesExactlyOnce() {
        var calls = 0
        val label = mutableStateOf("Recover")
        val root = render(label = label, action = { calls++; label.value = "SENT"; true })
        assertEquals(null, dispatch(root))
        compose.waitForIdle()
        assertEquals(1, calls)
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals("SENT")
    }

    @Test fun highReadyControlInvokesExactlyOnce() {
        var calls = 0
        val root = render(action = { calls++; true })
        compose.lbAwaitRecoverDispatch(root, tag, "Recover") { 1000L }
        assertEquals(1, calls)
    }

    @Test fun highInitiallyNotReadyWaitsForActualRecompositionBeforeOneAction() {
        var calls = 0
        var scheduled = false
        val label = mutableStateOf("SENT")
        val blocked = mutableStateOf(true)
        val root = render(label, blocked) { calls++; true }
        val refusal = runCatching {
            compose.lbAwaitRecoverDispatch(root, tag, "Recover") {
                if (!scheduled && Looper.myLooper() === Looper.getMainLooper()) {
                    scheduled = true
                    label.value = "Recover"
                    blocked.value = false
                }
                1000L
            }
        }.exceptionOrNull()
        assertTrue("Real readiness composition must be scheduled", scheduled)
        // Baseline must expose actual typed NotReady; setup failure is not RED.
        assertEquals("Pre-action wait must finish", null, refusal)
        assertEquals(1, calls)
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsEnabled().assertTextEquals("Recover")
    }

    @Test fun highPermanentDisabledExpiresWithoutAction() {
        var calls = 0
        var reads = 0
        val root = render(blocked = mutableStateOf(true), action = { calls++; true })
        val failure = runCatching {
            compose.lbAwaitRecoverDispatch(root, tag, "Recover") { if (++reads > 4) 0L else 100L }
        }.exceptionOrNull()
        assertTrue(failure != null)
        assertEquals(0, calls)
    }

    @Test fun highTypedActionThrowableAfterInvocationIsNeverReadiness() {
        var calls = 0
        val original = RecoverBeforeDispatchNotReady()
        val root = render(action = { calls++; throw original })
        val failure = runCatching { compose.lbAwaitRecoverDispatch(root, tag, "Recover") { 1000L } }.exceptionOrNull()
        assertSame(original, failure)
        assertEquals(1, calls)
    }

    @Test fun highFalseActionNeverRetries() {
        var calls = 0
        val root = render(action = { calls++; false })
        assertTrue(runCatching { compose.lbAwaitRecoverDispatch(root, tag, "Recover") { 1000L } }.isFailure)
        assertEquals(1, calls)
    }

    @Test fun highLateHandledActionNeverRetries() {
        var calls = 0
        var remaining = 1000L
        val root = render(action = { calls++; remaining = 0L; true })
        assertTrue(runCatching { compose.lbAwaitRecoverDispatch(root, tag, "Recover") { remaining } }.isFailure)
        assertEquals(1, calls)
    }

    @Test fun throwingInvocationCallbackCannotInvokeOrRetryAction() {
        var callbacks = 0
        var calls = 0
        val original = IllegalStateException("controlled-latch-fault")
        val root = render(action = { calls++; true })
        var failure: Throwable? = null
        compose.runOnUiThread {
            failure = runCatching {
                lbDispatchRecoverSemantics(root, tag, "Recover", { 1000L }) { callbacks++; throw original }
            }.exceptionOrNull()
        }
        assertSame(original, failure)
        assertEquals(1, callbacks)
        assertEquals(0, calls)
    }

    @Test fun highMissingActionIsFatalWithoutReadinessRetry() {
        val root = render()
        val failure = runCatching { compose.lbAwaitRecoverDispatch(root, tag, "Recover") { 1000L } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure !is RecoverBeforeDispatchNotReady)
    }

    @Test fun highDuplicateCurrentTargetsAreFatalBeforeAnyAction() {
        var calls = 0
        compose.setContent {
            Column {
                Text("Recover", Modifier.testTag(tag).semantics { onClick { calls++; true } })
                Text("Recover", Modifier.testTag(tag).semantics { onClick { calls++; true } })
            }
        }
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val failure = runCatching { compose.lbAwaitRecoverDispatch(root, tag, "Recover") { 1000L } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure !is RecoverBeforeDispatchNotReady)
        assertEquals(0, calls)
    }
}
