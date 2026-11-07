package ui.liveboard

import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.activity.ComponentActivity
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cafe.adriel.voyager.navigator.Navigator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import ui.scenes.mainscreen.MainScreen
import core.metaSecretCore.liveboard.LiveboardCreatePhase
import core.metaSecretCore.liveboard.LiveboardDraft
import ui.design.generated.LiveboardDuration
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardTokens

/** Actual Main/Session rendering with controlled Core/auth; captures are not real Core/OS E2E. */
class LiveboardRootVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var fixture: LiveboardAndroidFixture? = null
    private val imeProbe by lazy { LiveboardImeDiagnostics { compose.activity } }
    private val orientationActions = PhoneOrientationTestActions { compose.activity }

    @After fun cleanup() {
        val disposalFailure = runCatching {
            compose.runOnUiThread {
                val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
                (content?.getChildAt(0) as? ComposeView)?.let { ownedView ->
                    ownedView.setContent {}
                    ownedView.disposeComposition()
                }
            }
        }.exceptionOrNull()
        val fixtureFailure = runCatching { fixture?.close() }.exceptionOrNull()
        if (disposalFailure != null && fixtureFailure != null && fixtureFailure !== disposalFailure)
            disposalFailure.addSuppressed(fixtureFailure)
        orientationActions.restore(disposalFailure ?: fixtureFailure)
    }

    private fun settleCreateEditor() {
        frame()
        val duration = maxOf(LiveboardTokens.liveboardDurationCreateResize,
            (LiveboardTimelines.createFields.timing.duration as LiveboardDuration.Fixed).ms)
        compose.mainClock.advanceTimeBy(duration.toLong())
        frame()
    }

    private fun revealCreateField(tag: String): SemanticsNodeInteraction {
        val deadline = System.nanoTime() + 15_000_000_000L
        val target = compose.onNodeWithTag(tag)
        val fields = compose.onNodeWithTag("lb-create-fields")
        fun requireTime() = check(System.nanoTime() < deadline) { "Create scroll deadline: tag=$tag" }
        for (gesture in 0..24) {
            requireTime()
            val viewport = fields.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            check(viewport.width > 0f && viewport.height > 0f) { "Create scroll empty viewport: tag=$tag viewport=$viewport" }
            val node = target.fetchSemanticsNode()
            val center = Offset(node.positionInRoot.x + node.size.width / 2f,
                node.positionInRoot.y + node.size.height / 2f)
            target.assertIsEnabled()
            if (target.isDisplayed() && viewport.contains(center)) {
                target.assertIsDisplayed().assertIsEnabled()
                requireTime()
                return target
            }
            check(gesture < 24) { "Create scroll gesture limit: tag=$tag viewport=$viewport center=$center" }
            check(center.x >= viewport.left && center.x < viewport.right) {
                "Create scroll horizontal target unreachable: tag=$tag viewport=$viewport center=$center"
            }
            val upward = center.y >= viewport.bottom
            check(upward || center.y < viewport.top) {
                "Create scroll target not displayed inside viewport: tag=$tag viewport=$viewport center=$center"
            }
            requireTime()
            println("LIVEBOARD_CREATE_SCROLL tag=$tag phase=before gesture=${gesture + 1} viewport=$viewport center=$center")
            fields.performTouchInput {
                val width = visibleSize.width.toFloat()
                val height = visibleSize.height.toFloat()
                check(width > 0f && height > 0f) { "Create scroll empty touch area: tag=$tag" }
                swipeWithVelocity(Offset(width / 2f, height * (if (upward) .8f else .2f)),
                    Offset(width / 2f, height * (if (upward) .2f else .8f)), endVelocity = 0f)
            }
            repeat(8) { requireTime(); frame() }
            requireTime()
            val afterViewport = fields.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val after = target.fetchSemanticsNode()
            val afterCenter = Offset(after.positionInRoot.x + after.size.width / 2f,
                after.positionInRoot.y + after.size.height / 2f)
            val reachable = target.isDisplayed() && afterViewport.contains(afterCenter)
            println("LIVEBOARD_CREATE_SCROLL tag=$tag phase=after gesture=${gesture + 1} viewport=$afterViewport center=$afterCenter reachable=$reachable")
            if (reachable) {
                target.assertIsDisplayed().assertIsEnabled()
                requireTime()
                return target
            }
            check(kotlin.math.abs((afterCenter.y - afterViewport.top) - (center.y - viewport.top)) > .5f) {
                "Create scroll made no progress: tag=$tag gesture=${gesture + 1} viewport=$afterViewport before=$center after=$afterCenter"
            }
        }
        error("Create scroll target unreachable: tag=$tag")
    }

    @Test fun captureActualMainHomeSecretsFullFormAndInvite() {
        orientationActions.applyPolicy()
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it; it.install() }
        compose.mainClock.autoAdvance = false
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        assertTrue("Physical native read must precede rendered Member", f.core.reads.get() > 0)
        capture("01-home")
        compose.onNodeWithTag("lb-open-secrets").performClick()
        compose.mainClock.advanceTimeBy(500)
        waitTag("lb-secrets"); capture("02-secrets-intermediate")
        compose.mainClock.advanceTimeBy(1_000)
        capture("03-secrets-settled")
        compose.onNodeWithTag("lb-primary-pass-a").performScrollTo().performClick()
        waitTag("lb-value-pass-a")
        compose.onNodeWithTag("lb-value-pass-a").assertTextContains("local renderer value")
        compose.onNodeWithTag("lb-copy-pass-a").assertExists()
        frame()
        val revealDuration = maxOf((LiveboardTimelines.valueReveal.timing.duration as LiveboardDuration.Fixed).ms,
            LiveboardTimelines.copyReveal.timing.delay.at(0) + (LiveboardTimelines.copyReveal.timing.duration as
                LiveboardDuration.Fixed).ms)
        compose.mainClock.advanceTimeBy(revealDuration.toLong())
        frame()
        capture("04-authorized-local")
        compose.onNodeWithTag("lb-secrets-back").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("lb-open-create").performClick()
        waitTag("lb-create-name")
        compose.onNodeWithTag("lb-create-name").performTextInput("Visual form"); frame()
        revealCreateField("lb-create-value").performTouchInput { click() }
        waitForActualIme(true)
        compose.onNodeWithTag("lb-create-value").performTextInput("Synthetic visual input")
        compose.mainClock.advanceTimeBy(1_000); capture("05-plain-form-ime")
        requireNativeCreateViewport("lb-create-value", Configuration.ORIENTATION_PORTRAIT)
        captureDisplay("09-portrait-create-ime-system")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForActualIme(false)
        compose.onNodeWithTag("lb-create").assertExists()
        compose.onNodeWithTag("lb-create-seed").performClick(); waitTag("lb-create-count-24")
        revealCreateField("lb-create-count-24").performClick(); waitTag("lb-create-word-24")
        settleCreateEditor()
        revealCreateField("lb-create-word-24").performTouchInput { click() }
        waitForActualIme(true)
        revealCreateField("lb-create-word-24").performTextInput("twentyfour")
        compose.mainClock.advanceTimeBy(1_000); capture("06-seed24-scroll")
        val formHost = compose.activity
        val rotatedOrientation = orientationActions.rotate(landscape = true)
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == rotatedOrientation }
        settleCreateEditor()
        imeProbe.systemTap(revealCreateField("lb-create-word-24"), compose.onNodeWithTag("lb-create-fields"), formHost)
        imeProbe.capture(2)
        requireNativeCreateViewport("lb-create-word-24", rotatedOrientation)
        assertSame("Open-form rotation must retain the controlled host", formHost, compose.activity)
        assertTrue("Authored Word24 must survive open-form rotation",
            compose.onNodeWithTag("lb-create-word-24").fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text == "twentyfour")
        captureDisplay(if (rotatedOrientation == Configuration.ORIENTATION_PORTRAIT) "10-phone-portrait-policy-ime-system" else "10-landscape-create-ime-system")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForActualIme(false)
        compose.onNodeWithTag("lb-create").assertExists()
        orientationActions.rotate(landscape = false)
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithTag("lb-create-close").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("lb-invite").performClick()
        waitTag("lb-invite-qr"); compose.mainClock.advanceTimeBy(1_000)
        capture("07-invite-qr-glass")
        compose.onNodeWithTag("lb-invite-close").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        val hostBeforeRotation = compose.activity
        orientationActions.rotate(landscape = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == rotatedOrientation }
        assertSame("Controlled render rotation must retain the configured host", hostBeforeRotation, compose.activity)
        assertEquals("Capture requires the supported orientation policy", rotatedOrientation, compose.activity.resources.configuration.orientation)
        waitTag("lb-home"); capture(if (rotatedOrientation == Configuration.ORIENTATION_PORTRAIT) "08-phone-portrait-policy-home" else "08-landscape-home")
        runBlocking(Dispatchers.IO) { f.session() }
    }

    @Test fun canonicalReceiptClearsFieldsAndOwnsOnlyIts520msRenderedSuccess() {
        val f = LiveboardAndroidFixture(compose.activity).also { fixture = it; it.install() }
        var mounted by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { if (mounted) Navigator(MainScreen()) }
        waitTag("lb-home"); compose.waitUntil(15_000) { f.idle() }
        val session = runBlocking(Dispatchers.IO) { f.session() }
        for (seed in listOf(false, true)) {
            val name = if (seed) "Canonical seed form" else "Canonical plain form"
            compose.onNodeWithTag("lb-open-create").performClick(); waitTag("lb-create-name")
            compose.onNodeWithTag("lb-create-name").performTextInput(name); frame()
            if (seed) {
                compose.onNodeWithTag("lb-create-seed").performClick(); frame()
                revealCreateField("lb-create-count-24").performClick(); frame()
                settleCreateEditor()
                repeat(24) { index ->
                    revealCreateField("lb-create-word-${index + 1}").performTextInput("word${index + 1}")
                    frame()
                }
            } else {
                compose.onNodeWithTag("lb-create-value").performTextInput("Synthetic canonical value"); frame()
            }
            val before = f.core.calls.count { it.first == "create" }
            compose.onNodeWithTag("lb-create-submit").assertIsEnabled().performClick(); frame()
            compose.waitUntil(15_000) { f.core.calls.count { it.first == "create" } == before + 1 }
            runBlocking(Dispatchers.IO) { session.awaitSettled(); f.proof() }
            frame()
            assertEquals(LiveboardCreatePhase.Submitted, session.actions.createPresentation().phase)
            assertNotNull(session.sensitive.state.value.draft)
            compose.onNodeWithTag("lb-create-success").assertDoesNotExist()
            // The fixture changes only the Core response; a real covered physical read supplies authority.
            f.core.publishCreated(name, if (seed) 24 else null)
            runBlocking(Dispatchers.IO) { f.proof() }
            waitTag("lb-create-success")
            assertNull(session.sensitive.state.value.draft)
            assertNotNull(session.sensitive.state.value.createCompletion)
            val saved = compose.onNodeWithTag("lb-create-saved")
            val expected = if (compose.activity.resources.configuration.locales[0].language == "ru") "Секрет создан" else "Secret created"
            saved.assertTextEquals(expected)
            listOf("lb-create-name", "lb-create-value", "lb-create-word-1", "lb-create-word-24", "lb-create-submit").forEach {
                compose.onNodeWithTag(it).assertDoesNotExist()
            }
            frame()
            val started = compose.mainClock.currentTime
            capture(if (seed) "13-seed-success-start" else "10-plain-success-start")
            compose.mainClock.advanceTimeBy(260, ignoreFrameDuration = true)
            saved.assertExists()
            capture(if (seed) "14-seed-success-260ms" else "11-plain-success-260ms")
            if (!seed) {
                compose.mainClock.advanceTimeBy(259, ignoreFrameDuration = true)
                assertEquals(started + 519, compose.mainClock.currentTime)
                saved.assertExists()
                capture("12-plain-success-519ms")
                val beforeMetadata = requireNotNull(f.reads.frames.value).attempt
                runBlocking(Dispatchers.IO) { f.proof() }
                assertTrue(requireNotNull(f.reads.frames.value).attempt > beforeMetadata)
                compose.runOnUiThread {} // Deliver the genuine frame/Root metadata revision without a render tick.
                assertEquals(started + 519, compose.mainClock.currentTime)
                saved.assertExists() // The SAME receipt must survive the real read before any clock advance.
                compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
                frame() // Deliver the 520ms frame-clock completion on the next render frame.
                frame() // Recompose the completed Root publication before reading semantics.
                compose.onNodeWithTag("lb-create-success").assertDoesNotExist()
                compose.onNodeWithTag("lb-create").assertDoesNotExist()
                waitTag("lb-open-create") // The retained exit must release Home before the next form.
            } else {
                // A genuine new authored version invalidates the old token before its timer returns.
                compose.runOnUiThread {
                    assertTrue(session.sensitive.openCreate())
                    session.sensitive.editForm(LiveboardDraft("New current editor", "New current input"))
                }
                frame(); waitTag("lb-create-value")
                compose.mainClock.advanceTimeBy(520, ignoreFrameDuration = true); frame()
                compose.onNodeWithTag("lb-create-value").assertTextContains("New current input")
                compose.onNodeWithTag("lb-create-success").assertDoesNotExist()
                compose.runOnUiThread { f.socket.onAppBackground() }; frame()
                assertNull(session.sensitive.state.value.draft)
                compose.onNodeWithTag("lb-create").assertDoesNotExist()
                compose.runOnUiThread { f.socket.onAppForeground() }
                runBlocking(Dispatchers.IO) { f.proof() }
                compose.waitUntil(15_000) { f.idle() }; frame()
            }
            assertEquals("Presentation never replays native Create", before + 1, f.core.calls.count { it.first == "create" })
        }
        val issued = f.core.calls.count { it.first == "create" }
        compose.runOnIdle { mounted = false }; frame()
        compose.runOnIdle { mounted = true }; frame(); waitTag("lb-home")
        compose.onNodeWithTag("lb-create-success").assertDoesNotExist()
        compose.onNodeWithTag("lb-create").assertDoesNotExist()
        assertEquals(issued, f.core.calls.count { it.first == "create" })
    }

    private data class NativeImeWindow(val visible: RectF, val origin: Offset, val orientationReady: Boolean, val ime: Boolean?)

    private fun nativeImeWindow(orientation: Int): NativeImeWindow {
        var observed: NativeImeWindow? = null
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val visible = Rect().also { decor.getWindowVisibleDisplayFrame(it) }
            val screen = IntArray(2).also { decor.getLocationOnScreen(it) }
            val window = IntArray(2).also { decor.getLocationInWindow(it) }
            observed = NativeImeWindow(RectF(visible), Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()),
                compose.activity.resources.configuration.orientation == orientation &&
                    (if (orientation == Configuration.ORIENTATION_LANDSCAPE) decor.width > decor.height else decor.height > decor.width),
                ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()))
        }
        return checkNotNull(observed) { "Native window observation unavailable" }
    }

    private fun waitForActualIme(visible: Boolean) {
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            nativeImeWindow(compose.activity.resources.configuration.orientation).ime == visible
        }
    }

    private fun requireNativeCreateViewport(fieldTag: String, orientation: Int) {
        var previous: List<RectF>? = null
        val observations = ArrayDeque<List<Any?>>()
        fun rectangle(value: RectF) = listOf(value.left, value.top, value.right, value.bottom).map { it.takeIf { number -> number.isFinite() } }
        fun record(value: List<Any?>) { if (observations.size == 2) observations.removeFirst(); observations.addLast(value) }
        val started = android.os.SystemClock.elapsedRealtime(); var samples = 0; var goodSamples = 0
        try { compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            val native = nativeImeWindow(orientation)
            val tags = listOf("lb-create", "lb-create-fields", "lb-create-cancel", "lb-create-submit", fieldTag)
            val matches = tags.map { compose.onAllNodesWithTag(it).fetchSemanticsNodes() }
            val head = listOf(++samples, android.os.SystemClock.elapsedRealtime() - started, compose.mainClock.currentTime,
                native.orientationReady, native.ime, listOf(native.origin.x, native.origin.y), rectangle(native.visible), matches.map { it.size })
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) imeProbe.capture(3, samples)
            if (matches.any { it.size != 1 }) { record(head + listOf(null, null, false, false, false)); previous = null; return@waitUntil false }
            val nodes = matches.map { it.single() }
            val bounds = nodes.map { node ->
                val point = node.positionInWindow + native.origin
                RectF(point.x, point.y, point.x + node.size.width, point.y + node.size.height)
            }
            val finite = (bounds + native.visible).all { rectangle -> rectangle.width() > 0f && rectangle.height() > 0f &&
                listOf(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom).all { it.isFinite() } }
            val good = finite && native.orientationReady && native.ime == true && native.visible.contains(bounds[0]) &&
                bounds[0].contains(bounds[1]) && bounds[0].contains(bounds[2]) && bounds[0].contains(bounds[3]) &&
                bounds[1].contains(bounds[4]) && !nodes[4].config.contains(SemanticsProperties.Disabled)
            val sample = bounds + native.visible
            val stable = good && previous == sample
            val checks = listOf(finite, native.orientationReady, native.ime == true, native.visible.contains(bounds[0]),
                bounds[0].contains(bounds[1]), bounds[0].contains(bounds[2]), bounds[0].contains(bounds[3]),
                bounds[1].contains(bounds[4]), !nodes[4].config.contains(SemanticsProperties.Disabled))
            record(head + listOf(bounds.map(::rectangle), checks, good, previous == sample, stable)); if (good) goodSamples++
            previous = if (good) sample else null
            stable
        } } catch (primary: ComposeTimeoutException) {
            try {
                val data = org.json.JSONObject().put("schema", 1).put("orientation", orientation).put("samples", samples)
                    .put("goodSamples", goodSamples).put("lastTwo", org.json.JSONArray(observations.toList())).put("nativeIme", imeProbe.json())
                val bytes = data.toString().toByteArray(Charsets.UTF_8); check(bytes.size <= 8192) { "Geometry diagnostic exceeds finite size" }
                val directory = File(compose.activity.getExternalFilesDir(null), "liveboard-visual").apply { mkdirs() }
                File(directory, "11-native-create-viewport-failure.json").writeBytes(bytes)
            } catch (diagnostic: Throwable) { primary.addSuppressed(diagnostic) }
            try { captureDisplay("11-native-create-viewport-failure-system") } catch (diagnostic: Throwable) { primary.addSuppressed(diagnostic) }
            throw primary
        }
    }

    private fun captureDisplay(name: String) {
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) {
            "Actual system display screenshot unavailable"
        }
        try {
            val directory = File(compose.activity.getExternalFilesDir(null), "liveboard-visual").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(15_000) {
            if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "liveboard-visual").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("A capture must contain rendered pixels", image.width > 0 && image.height > 0)
    }
}
