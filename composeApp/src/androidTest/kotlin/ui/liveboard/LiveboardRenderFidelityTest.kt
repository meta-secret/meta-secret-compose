package ui.liveboard

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import core.AppString
import core.StringProviderAndroid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import qrgenerator.qrkitpainter.QrKitCodeShape
import qrgenerator.qrkitpainter.QrKitErrorCorrection
import qrgenerator.qrkitpainter.QrKitOptions
import qrgenerator.qrkitpainter.QrKitShapes
import qrgenerator.qrkitpainter.QrPainter
import qrgenerator.qrkitpainter.QrMatrix
import ui.design.generated.LiveboardDuration
import ui.design.generated.LiveboardInviteFit
import ui.design.generated.LiveboardMotionRect
import ui.design.generated.LiveboardTimelines
import ui.liveboard.invite.InvitePopover
import ui.liveboard.invite.InvitePresentation
import ui.liveboard.visual.LiveboardArt
import ui.scenes.mainscreen.MainScreen

/** Actual controlled pixels against the approved prototype; not real Core or OS E2E. */
class LiveboardRenderFidelityTest {
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
            fixture?.close()
        }
    }

    @Test fun testQrKeepsFourActualModulesClearOnEveryEdge() {
        val image = invitePixels()
        val modules = referenceMatrix().dimension
        val unit = image.width.toFloat() / (modules + 8)
        val inset = ceil(unit).toInt()
        val ink = pixelBounds(image, Rect(inset, inset, image.width - inset, image.height - inset), ::dark)
        val gaps = listOf(ink.left, ink.top, image.width - ink.right, image.height - ink.bottom)
        assertTrue("Real QR must contain a substantial dark matrix", ink.width() > image.width / 2)
        assertTrue("Four-module quiet zone: modulePx=$unit observedEdgePx=$gaps",
            gaps.all { abs(it - 4f * unit) <= 2f })
    }

    @Test fun testQrGrayBrandIsFivePointFourModulesInsideSevenModuleBacking() {
        val image = invitePixels()
        val matrix = referenceMatrix()
        val unit = image.width.toFloat() / (matrix.dimension + 8)
        val center = image.width / 2f
        val radius = ceil(3.5f * unit).toInt()
        val gray = pixelBounds(image, Rect(center.toInt() - radius, center.toInt() - radius,
            center.toInt() + radius, center.toInt() + radius), ::brandGray)
        // Fixed SVG path bounds in its 132x152 viewport; the prototype embeds this in a 5.4-module square.
        assertEquals("Gray brand height must follow module geometry, not two-dp padding",
            5.4f * unit * (150.74f / 152f), gray.height().toFloat(), 2f)
        assertEquals("Gray brand must retain its original aspect", 5.4f * unit * (131.18f / 152f),
            gray.width().toFloat(), 2f)
        assertEquals("Gray brand horizontal center", center, gray.exactCenterX(), 2f)
        assertEquals("Gray brand vertical center", center, gray.exactCenterY(), 2f)
        val middle = matrix.dimension / 2
        var overwrittenDarkModules = 0
        var preservedDarkModules = 0
        for (row in middle - 4..middle + 4) for (column in middle - 4..middle + 4) {
            val distance = maxOf(abs(row - middle), abs(column - middle))
            val pixel = image.getPixel(((column + 4.5f) * unit).roundToInt(),
                ((row + 4.5f) * unit).roundToInt())
            if (distance == 3) {
                if (matrix[column, row] == QrMatrix.PixelType.Dark) overwrittenDarkModules++
                assertTrue("Seven-module backing must clear its outer ring", near(pixel, 0xFFEDEEEB.toInt(), 3))
            } else if (distance == 4 && matrix[column, row] == QrMatrix.PixelType.Dark) {
                preservedDarkModules++
                assertTrue("Backing must not erase the next QR module ring", dark(pixel))
            }
        }
        assertTrue("Backing checks must include real encoded dark modules", overwrittenDarkModules > 0)
        assertTrue("Outer-ring control must contain real encoded dark modules", preservedDarkModules > 0)
    }

    @Test fun testCompactInactiveArtKeepsTopBottomAndBothSideBorders() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(65.dp, 82.dp).background(Color(0xFF2C2E2F)).testTag("fidelity-ghost")) {
                Box(Modifier.matchParentSize().alpha(.65f)) {
                    LiveboardArt("placeholder", Modifier.fillMaxWidth().height(82.dp), Color(0xFF68736B))
                }
            }
        }
        val image = compose.onNodeWithTag("fidelity-ghost").captureToImage().asAndroidBitmap()
        // Four independent bands from the complete, centered prototype outline in the compact 65x82 box.
        val counts = listOf(
            count(image, fractionRect(image, .20f, .05f, .80f, .12f), ::inactiveDot),
            count(image, fractionRect(image, .20f, .88f, .80f, .96f), ::inactiveDot),
            count(image, fractionRect(image, .12f, .25f, .22f, .75f), ::inactiveDot),
            count(image, fractionRect(image, .78f, .25f, .88f, .75f), ::inactiveDot),
        )
        assertTrue("Inactive alpha layer must retain top/bottom/left/right dots: $counts", counts.all { it >= 10 })
    }

    @Test fun testSecretsSearchShowsHintOnlyWhenEmptyAndClearRestoresIt() {
        openSecrets()
        val hint = if (compose.activity.resources.configuration.locales[0].language == "ru")
            "Поиск секретов" else "Search secrets"
        compose.onNodeWithText(hint, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lb-search-clear").assertDoesNotExist()
        compose.onNodeWithTag("lb-search").performTextReplacement("Local")
        frame()
        compose.onNodeWithText(hint, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("lb-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Local")))
        compose.onNodeWithTag("lb-secret-pass-a").assertExists()
        compose.onNodeWithTag("lb-secret-pass-b").assertDoesNotExist()
        compose.onNodeWithTag("lb-search-clear").assertIsDisplayed().performClick()
        frame()
        compose.onNodeWithTag("lb-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(hint, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lb-search-clear").assertDoesNotExist()
        compose.onNodeWithTag("lb-secret-pass-a").assertExists()
        compose.onNodeWithTag("lb-secret-pass-b").assertExists()
    }

    @Test fun testSecretsHeaderShowsOriginalGrayBrandAboveSearch() {
        openSecrets()
        val label = StringProviderAndroid(compose.activity).getString(AppString.orbitBrand)
        val brand = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("lb-secrets")),
            useUnmergedTree = true).assertIsDisplayed()
        val bounds = brand.fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals("Phone prototype brand width", 26f * density, bounds.width, 1f)
        assertEquals("Phone prototype brand height", 30f * density, bounds.height, 1f)
        val back = compose.onNodeWithTag("lb-secrets-back").fetchSemanticsNode().boundsInRoot
        assertEquals("Header controls share their vertical center", back.center.y, bounds.center.y, 1f)
        assertTrue("Brand belongs above the search field", bounds.bottom < compose.onNodeWithTag("lb-search")
            .fetchSemanticsNode().boundsInRoot.top)
        val image = brand.captureToImage().asAndroidBitmap()
        val area = Rect(0, 0, image.width, image.height)
        // Allow prototype opacity .8 over the header; each of the three original gray layers must remain visible.
        listOf(0xFF2C2E2F.toInt() to 12, 0xFF555B5D.toInt() to 14, 0xFF858E91.toInt() to 24).forEach { (ink, tolerance) ->
            assertTrue("Header must display every original gray layer", count(image, area) { near(it, ink, tolerance) } > 3)
        }
    }

    private fun referenceMatrix(): QrMatrix {
        var observed: QrMatrix? = null
        val defaultShape = QrKitCodeShape.Default
        val observer = object : QrKitCodeShape by defaultShape {
            override fun QrMatrix.transform(): QrMatrix =
                with(defaultShape) { this@transform.transform() }.also { observed = it }
        }
        QrPainter(VAULT, QrKitOptions(shapes = QrKitShapes(codeShape = observer),
            errorCorrection = QrKitErrorCorrection.High))
        return requireNotNull(observed) { "Public QR shape callback did not supply its returned matrix" }
    }

    private fun install() {
        fixture = LiveboardAndroidFixture(compose.activity).also { it.install() }
        compose.mainClock.autoAdvance = false
    }

    private fun invitePixels(): Bitmap {
        install()
        compose.setContent {
            InvitePopover(VAULT, InvitePresentation(LiveboardInviteFit(LiveboardMotionRect(0f, 0f, 304f, 344f),
                208f), 1f, null, Offset.Zero), {}, Modifier.size(304.dp, 344.dp))
        }
        return compose.onNodeWithTag("lb-invite-qr").assertIsDisplayed().captureToImage().asAndroidBitmap()
    }

    private fun openSecrets() {
        install()
        compose.setContent { Navigator(MainScreen()) }
        waitTag("lb-home")
        compose.waitUntil(15_000) { requireNotNull(fixture).idle() }
        compose.onNodeWithTag("lb-open-secrets").performClick()
        waitTag("lb-secrets")
        compose.mainClock.advanceTimeBy((LiveboardTimelines.sheetOpen.timing.duration as LiveboardDuration.Fixed).ms.toLong())
        frame()
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun pixelBounds(image: Bitmap, area: Rect, predicate: (Int) -> Boolean): Rect {
        var left = image.width; var top = image.height; var right = -1; var bottom = -1
        for (y in area.top until area.bottom) for (x in area.left until area.right) if (predicate(image.getPixel(x, y))) {
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        assertTrue("Expected real rendered ink within the sampled region", right >= left && bottom >= top)
        return Rect(left, top, right + 1, bottom + 1)
    }

    private fun fractionRect(image: Bitmap, left: Float, top: Float, right: Float, bottom: Float) =
        Rect((image.width * left).roundToInt(), (image.height * top).roundToInt(),
            (image.width * right).roundToInt(), (image.height * bottom).roundToInt())

    private fun count(image: Bitmap, area: Rect, predicate: (Int) -> Boolean): Int {
        var count = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) if (predicate(image.getPixel(x, y))) count++
        return count
    }

    private fun near(pixel: Int, color: Int, tolerance: Int) =
        abs(AndroidColor.red(pixel) - AndroidColor.red(color)) <= tolerance &&
            abs(AndroidColor.green(pixel) - AndroidColor.green(color)) <= tolerance &&
            abs(AndroidColor.blue(pixel) - AndroidColor.blue(color)) <= tolerance

    private fun dark(pixel: Int) = AndroidColor.red(pixel) < 80 && AndroidColor.green(pixel) < 80 && AndroidColor.blue(pixel) < 80
    private fun inactiveDot(pixel: Int) = AndroidColor.red(pixel) > 65 && AndroidColor.green(pixel) > 70 && AndroidColor.blue(pixel) > 65
    private fun brandGray(pixel: Int) = listOf(0xFF2C2E2F, 0xFF555B5D, 0xFF858E91).any { near(pixel, it.toInt(), 3) }

    private companion object { const val VAULT = "Renderer vault" }
}
