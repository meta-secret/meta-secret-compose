package ui.scenes.registration

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.test.platform.app.InstrumentationRegistry
import core.AppString
import core.OrbitBackdropBlurAndroid
import core.StringProviderAndroid
import core.StringProviderInterface
import core.metaSecretCore.VaultAvailability
import java.io.File
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** Sample states only: real rendering/blur, explicitly synthetic camera, no Core/auth bypass. */
class OrbitRegistrationVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun cleanup() { stopKoin() }

    @Test
    fun availableJoinIsDisabledAndStaticWhileExistingJoinRemainsInteractive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        startKoin { modules(module { single<StringProviderInterface> { StringProviderAndroid(context) } }) }
        var state by mutableStateOf(RegistrationUiState(RegistrationScene.Name,
            RegistrationIdentity("family@example.org", availability = VaultAvailability.AVAILABLE)))
        val intents = mutableListOf<RegistrationIntent>()
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitLayout(state, { intents += it }, {}) }
        compose.waitForIdle()
        compose.onNodeWithTag("orbit-create").assertIsEnabled()
        val join = compose.onNodeWithTag("orbit-join").assertIsNotEnabled()
        join.performTouchInput { click() }
        compose.runOnIdle { assertEquals(emptyList<RegistrationIntent>(), intents) }
        val disabled = join.captureToImage().asAndroidBitmap()
        repeat(6) {
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            assertTrue("Disabled Join must keep a fixed rendered pose", disabled.sameAs(join.captureToImage().asAndroidBitmap()))
        }

        compose.runOnIdle { state = state.copy(identity = state.identity.copy(availability = VaultAvailability.EXISTS)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("orbit-create").assertIsNotEnabled()
        join.assertIsEnabled()
        val enabled = join.captureToImage().asAndroidBitmap()
        var moved = false
        repeat(6) {
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            moved = moved || !enabled.sameAs(join.captureToImage().asAndroidBitmap())
        }
        assertTrue("Enabled Join is the positive control for observable icon motion", moved)
        join.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(RegistrationIntent.Join), intents) }

        compose.runOnIdle { state = state.copy(identity = state.identity.copy(availability = null)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        join.assertIsNotEnabled()
        for (phase in listOf(RegistrationPhase.Provider, RegistrationPhase.Requesting)) {
            compose.runOnIdle { state = state.copy(phase = phase,
                identity = state.identity.copy(availability = VaultAvailability.EXISTS)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            join.assertIsNotEnabled()
            join.performTouchInput { click() }
        }
        for (phase in listOf(RegistrationPhase.Checking, RegistrationPhase.Error, RegistrationPhase.Creating)) {
            compose.runOnIdle { state = state.copy(phase = phase) }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            compose.onNodeWithTag("orbit-join").assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(listOf(RegistrationIntent.Join), intents) }
    }

    @Test
    fun captureApprovedRegistrationStatesAndGlass() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        startKoin { modules(module { single<StringProviderInterface> { StringProviderAndroid(context) } }) }
        var state by mutableStateOf(RegistrationUiState())
        var backdrop by mutableStateOf<ImageBitmap?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val layer = rememberGraphicsLayer()
            val scope = rememberCoroutineScope()
            val radius = with(LocalDensity.current) { 7.dp.toPx() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.clearAndSetSemantics {} else Modifier).drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) {
                    OrbitLayout(state, {}, {
                        scope.launch { backdrop = OrbitBackdropBlurAndroid().blur(layer.toImageBitmap(), radius) }
                    })
                }
                backdrop?.let { image -> OrbitQrSheet(image, { backdrop = null }, {}, cameraPreview = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF171A1B)), contentAlignment = Alignment.Center) {
                        Text("Camera preview sample", color = Color(0xFF929B96))
                    }
                }) }
            }
        }
        compose.waitForIdle()
        capture("01-providers")
        compose.runOnIdle { state = RegistrationUiState(RegistrationScene.Name,
            RegistrationIdentity("family@example.org", availability = VaultAvailability.AVAILABLE)) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        capture("02-clockwise-midpoint")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        capture("02-available")
        compose.runOnIdle { state = state.copy(scene = RegistrationScene.Manual) }
        compose.mainClock.advanceTimeBy(1100)
        compose.waitForIdle()
        capture("03-manual")
        compose.runOnIdle { state = state.copy(scene = RegistrationScene.Name, direction = -1) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        capture("03-back-counterclockwise")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnIdle { state = state.copy(scene = RegistrationScene.Join, direction = 1) }
        compose.mainClock.advanceTimeBy(1100)
        compose.waitForIdle()
        capture("04-join")
        compose.onNodeWithTag("orbit-qr").performClick()
        compose.waitUntil(10000) { backdrop != null }
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        capture("04-qr-morph")
        compose.mainClock.advanceTimeBy(380)
        compose.waitForIdle()
        capture("04-qr-glass")
        compose.onNode(hasTestTag("orbit-back") and hasAnyAncestor(hasTestTag("orbit-qr-sheet"))).performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        capture("04-qr-collapse")
        compose.mainClock.advanceTimeBy(380)
        compose.waitForIdle()
        compose.runOnIdle { state = state.copy(phase = RegistrationPhase.Pending) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        capture("04-request-fusion")
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        capture("04-pending")
        compose.runOnIdle { state = state.copy(phase = RegistrationPhase.Declined, error = AppString.orbitDeclined) }
        compose.mainClock.advanceTimeBy(20)
        compose.waitForIdle()
        capture("04-declined")
        compose.runOnIdle { state = state.copy(scene = RegistrationScene.Name, phase = RegistrationPhase.Idle,
            error = null, identity = state.identity.copy(availability = VaultAvailability.EXISTS)) }
        compose.mainClock.advanceTimeBy(1100)
        compose.waitForIdle()
        capture("02-taken")
        compose.runOnIdle { state = state.copy(phase = RegistrationPhase.CreateError, error = AppString.orbitNetworkError) }
        compose.mainClock.advanceTimeBy(20)
        compose.waitForIdle()
        capture("02-create-error")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "orbit-visual").apply { mkdirs() }
        val orientation = if (context.resources.configuration.orientation == 2) "landscape" else "portrait"
        File(directory, "$orientation-$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
