package ui.liveboard

import androidx.activity.ComponentActivity
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cafe.adriel.voyager.navigator.Navigator
import core.metaSecretCore.liveboard.LiveboardActionPhase
import core.metaSecretCore.liveboard.LiveboardActionTarget
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import metasecret.project.com.MainActivity
import ui.scenes.mainscreen.MainScreen
import core.AppStateCacheProviderInterface
import metasecret.project.com.lbBindCurrentIncoming
import metasecret.project.com.lbDismissIncomingObserver
import metasecret.project.com.lbVisibleIncoming
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/** Client integration: real Main/services/proof/Android Back and clipboard; controlled Core/auth. */
class LiveboardRootIntegrationTest {
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


    @Test fun navigationActualRenderedCallbackWritesSecretsAndReportsIt() {
        val records = mutableListOf<LiveboardNavigationObservationRecord>()
        val f = navigationFixture(true, records)
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val healthy = compose.onNodeWithTag("lb-open-secrets").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        val before = f.core.calls.size
        compose.runOnUiThread { assertTrue(healthy()) }
        waitTag("lb-secrets")
        compose.onNodeWithTag("lb-secrets").assertExists()
        assertEquals(before, f.core.calls.size)
        val result = records.filter { it.stage == NavigationObservationStage.RESULT }
        assertEquals("Actual successful callback must emit one passive result", 1, result.size)
        assertEquals(NavigationObservationFlag.TRUE, result.single().flags.rendered)
    }

    @Test fun navigationDefaultDisabledKeepsActualHealthyRouteAndEmitsNothing() {
        val records = mutableListOf<LiveboardNavigationObservationRecord>()
        val f = navigationFixture(false, records)
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val before = f.core.calls.size
        compose.onNodeWithTag("lb-open-secrets").performClick()
        waitTag("lb-secrets")
        assertEquals(before, f.core.calls.size)
        assertTrue(records.isEmpty())
    }

    private fun navigationFixture(enabled: Boolean,
        records: MutableList<LiveboardNavigationObservationRecord>): LiveboardAndroidFixture {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it }
        f.install()
        val original = f.dependencies
        val observed = object : LiveboardPlatformPort by original.platform {
            override val navigationObservationEnabled = enabled
            override fun emitNavigationObservation(record: LiveboardNavigationObservationRecord) { records.add(record) }
        }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module {
            single<LiveboardRootDependencies> { LiveboardRootDependencies(original.sessions, original.reads,
                original.environment, original.services, observed) }
        })
        return f
    }

    @Test fun realMainPreservesFullFormAndCoreReadyRecoverEnablesAuthorizedShow() {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it }
        f.core.recoveredPrimary("recover")
        f.install()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { assertTrue(f.platform.dispatchBack()) }
            })
        }
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val session = runBlocking(Dispatchers.IO) { f.session() }
        compose.onNodeWithTag("lb-request-sender", useUnmergedTree = true).assertTextEquals("New laptop")
        assertTrue("Join sender is an outsider, not a member device", session.currentMember()?.devices?.none { it.deviceId == "outsider-d" } == true)
        compose.onNodeWithTag("lb-open-create").performClick()
        compose.onNodeWithTag("lb-create-name").performTextInput("Full form")
        compose.onNodeWithTag("lb-create-value").performTextInput("Inactive plain value")
        compose.onNodeWithTag("lb-create-seed").performClick()
        compose.onNodeWithTag("lb-create-count-24").performScrollTo().performClick()
        compose.onNodeWithTag("lb-create-word-24").performScrollTo().performTextInput("lastword")
        compose.onNodeWithTag("lb-create-count-12").performScrollTo().performClick()
        compose.runOnUiThread { f.notifications.showError("Synthetic render error") }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("lb-create").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("lb-inbox").assertExists()
        compose.runOnUiThread { session.inbox.dismissError() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        compose.onNodeWithTag("lb-open-create").performClick()
        compose.onNodeWithTag("lb-create-name").assertTextContains("Full form")
        compose.onNodeWithTag("lb-create-count-24").performScrollTo().performClick()
        compose.onNodeWithTag("lb-create-word-24").performScrollTo().assertTextContains("lastword")
        compose.onNodeWithTag("lb-create-plain").performClick()
        compose.onNodeWithTag("lb-create-value").assertTextContains("Inactive plain value")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("lb-create").assertDoesNotExist()
        compose.runOnIdle { assertNull(session.sensitive.state.value.draft) }

        compose.onNodeWithTag("lb-open-secrets").performClick()
        waitTag("lb-secrets")
        compose.onNodeWithTag("lb-primary-pass-b").performScrollTo().performClick()
        compose.waitUntil(15_000) { f.core.calls.count { it.first == "recover" } == 1 }
        runBlocking(Dispatchers.IO) { session.awaitSettled() }
        assertTrue(session.actions.presentation().any {
            it.target == LiveboardActionTarget.Recover("pass-b") && it.phase == LiveboardActionPhase.Submitted
        })
        // Actual physical Core projection changes while the private submission remains unresolved.
        f.core.recoveredPrimary("showRecovered")
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        compose.onNodeWithTag("lb-primary-pass-b").assertIsEnabled()
        f.auth.hold = true
        val before = f.core.calls.size
        compose.onNodeWithTag("lb-primary-pass-b").performClick()
        compose.waitUntil(15_000) { f.auth.prompts.get() > 0 }
        compose.runOnUiThread { f.auth.complete(false) }
        compose.runOnIdle {
            assertNull("Show Cancel is silent", session.inbox.state.value.error)
            assertEquals(before, f.core.calls.size)
        }
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        f.auth.hold = false
        compose.onNodeWithTag("lb-primary-pass-b").performClick()
        waitTag("lb-value-pass-b")
        compose.onNode(hasText("one") and hasAnyAncestor(hasTestTag("lb-value-pass-b")), useUnmergedTree = true).assertExists()
        assertEquals("renderer-claim", f.core.calls.last { it.first == "recovered" }.second.single())
        compose.onNodeWithTag("lb-copy-pass-b").performClick()
        val clipboard = AtomicReference<String?>()
        compose.runOnUiThread { clipboard.set(runBlocking { f.platform.readClipboard() }) }
        assertEquals("one two three four five six seven eight nine ten eleven twelve", clipboard.get())
        compose.onNodeWithTag("lb-primary-pass-b").performClick()
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-secrets-back").performClick()
        waitTag("lb-home")
        compose.onNodeWithTag("lb-open-profile").performClick()
        waitTag("lb-profile")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitTag("lb-home")
        assertEquals(1, f.core.calls.count { it.first == "recover" })
        compose.onNodeWithTag("lb-device-device-b").performClick()
        compose.onNodeWithTag("lb-remove-device-b").performClick()
        compose.waitUntil(15_000) { f.core.calls.count { it.first == "remove" } == 1 }
        runBlocking(Dispatchers.IO) { session.awaitSettled(); f.proof() }
        compose.waitUntil(15_000) { f.idle() }
        // No canonical request yet is not settlement of the private submitted Remove.
        compose.onNodeWithTag("lb-current").assertIsNotEnabled()
        compose.onNodeWithTag("lb-remove-device-b").assertIsNotEnabled()
        f.core.declineRemoval()
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() }
        compose.onNodeWithTag("lb-remove-device-b").assertIsEnabled()
        f.core.removeOtherDevice()
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() }
        compose.onNodeWithTag("lb-device-device-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-current").assertDoesNotExist()
        compose.onNodeWithTag("lb-remove-device-b").assertDoesNotExist()
    }

    @Test fun canonicalReadyShowOverridesOnlyPresentationBlockingOfPrivateRecoverUnknown() {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it }
        f.core.recoveryPhase = "unknown"
        f.core.recoveredPrimary("recover")
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
        primary.assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, f.core.calls.count { it.first == "recover" })
        f.core.recoveredPrimary("waiting", ready = false)
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        primary.assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        assertEquals(1, f.core.calls.count { it.first == "recover" })
        // Only genuine current Core/read proof enables Show; the private producer stays Unknown.
        f.core.recoveredPrimary("showRecovered")
        runBlocking(Dispatchers.IO) { f.proof() }
        compose.waitUntil(15_000) { f.idle() && session.inbox.state.value.ready }
        assertTrue(session.actions.presentation().any {
            it.target == LiveboardActionTarget.Recover("pass-b") && it.phase == LiveboardActionPhase.Unknown
        })
        primary.assertIsEnabled().performClick()
        waitTag("lb-value-pass-b")
        assertEquals(listOf("renderer-claim"), f.core.calls.last { it.first == "recovered" }.second)
        compose.onNodeWithTag("lb-copy-pass-b").performClick()
        val copied = AtomicReference<String?>()
        compose.runOnUiThread { copied.set(runBlocking { f.platform.readClipboard() }) }
        assertEquals("one two three four five six seven eight nine ten eleven twelve", copied.get())
        primary.performClick()
        compose.onNodeWithTag("lb-value-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-copy-pass-b").assertDoesNotExist()
        assertEquals("Show/Hide/Copy never replay Recover", 1, f.core.calls.count { it.first == "recover" })
        compose.runOnIdle { assertNull(session.inbox.state.value.error) }
    }

    @Test fun testObserverDismissalKeepsPendingJoinCollapsed() {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it }
        f.install()
        loadKoinModules(module { single<AppStateCacheProviderInterface> { f.cache } })
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home")
        compose.waitUntil(15_000) { f.idle() && compose.lbVisibleIncoming() != null }
        val head = requireNotNull(compose.lbVisibleIncoming())
        assertEquals("Join", head.kind)
        assertEquals(1, head.count)
        lbBindCurrentIncoming(head)
        val approve = compose.onNodeWithTag("lb-approve-${head.requestId}", useUnmergedTree = true)
        val decline = compose.onNodeWithTag("lb-decline-${head.requestId}", useUnmergedTree = true)
        val coreCalls = f.core.calls.toList()
        compose.onNodeWithTag("lb-inbox", useUnmergedTree = true).performScrollTo().performClick()
        approve.performScrollTo().assertIsDisplayed().assertIsEnabled()
        decline.assertIsDisplayed().assertIsEnabled()
        assertEquals(head, compose.lbVisibleIncoming())

        // Refused inputs must leave the original expanded renderer and Core state intact.
        fun assertRefused(expected: metasecret.project.com.LiveboardTestIncoming, deadline: Long) {
            val result = runCatching { compose.lbDismissIncomingObserver(expected, deadline) }
            assertTrue("Invalid observer preflight must be refused", result.exceptionOrNull() is IllegalStateException)
            compose.onNodeWithTag("lb-profile", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(head, compose.lbVisibleIncoming())
            approve.assertIsDisplayed()
            decline.assertIsDisplayed()
            assertEquals(coreCalls, f.core.calls.toList())
        }
        assertRefused(head, System.nanoTime() - 1L)
        assertRefused(head.copy(count = head.count + 1), System.nanoTime() + 60_000_000_000L)

        compose.lbDismissIncomingObserver(head, System.nanoTime() + 60_000_000_000L)

        assertEquals("Observer must retain the exact pending head and count", head, compose.lbVisibleIncoming())
        lbBindCurrentIncoming(head)
        assertEquals("Observer must submit no Core decision", coreCalls, f.core.calls.toList())
        approve.assertDoesNotExist()
        decline.assertDoesNotExist()
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Dedicated enrolled/initialized-device gate; lifecycle cover evidence, not a claim of OS Cancel. */
    @Test fun actualActivityInactiveCoverSuppressesUnderlyingTreeWithoutHardClearingForm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Requires dedicated real Core identity and an enrolled device; skipped is not proof",
            InstrumentationRegistry.getArguments().getString("liveboardRealRenderer") == "true")
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Any startup real prompt is handled by the visible platform supervisor; no callback bypass.
            waitTag("lb-home")
            compose.onNodeWithTag("lb-open-create").performClick()
            compose.onNodeWithTag("lb-create-name").performTextInput("Inactive form")
            compose.onNodeWithTag("lb-create-value").performTextInput("Synthetic inactive input")
            compose.onNodeWithTag("lb-create-value").assertTextContains("Synthetic inactive input")
            scenario.moveToState(Lifecycle.State.STARTED) // onPause only: no onStop/background hard clear.
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, content.importantForAccessibility)
                val decor = activity.window.decorView
                val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                val cover = android.graphics.Color.rgb(44, 46, 47)
                assertEquals("Inactive cover must occupy the rendered content center", cover, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                val directory = java.io.File(activity.getExternalFilesDir(null), "liveboard-visual").apply { mkdirs() }
                java.io.File(directory, "09-actual-activity-inactive-cover.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("lb-create-name").assertTextContains("Inactive form")
            compose.onNodeWithTag("lb-create-value").assertTextContains("Synthetic inactive input")
            scenario.onActivity { activity ->
                assertNotEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    activity.findViewById<View>(android.R.id.content).importantForAccessibility)
            }
            scenario.moveToState(Lifecycle.State.CREATED) // actual onStop must hard-conceal.
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitTag("lb-home")
            compose.onNodeWithTag("lb-create").assertDoesNotExist()
            compose.onNodeWithTag("lb-open-create").performClick()
            compose.onNodeWithTag("lb-create-name").assertTextEquals("")
            compose.onNodeWithTag("lb-create-value").assertTextEquals("")
        }
    }
}
