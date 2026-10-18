package metasecret.project.com

import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import core.AlertCoordinatorInterface
import core.JoinRequestAlertState
import core.AppStateCacheProviderInterface
import core.KeyChainInterface
import kotlinx.coroutines.runBlocking
import models.apiModels.UserDataOutsiderStatus
import models.apiModels.UserStatus
import models.apiModels.VaultFullInfo
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.net.HttpURLConnection
import java.net.URL

/** Real registration UI actors for Test51; contains no domain mutation or provider substitution. */
@RunWith(AndroidJUnit4::class)
class Case51AndroidRegistrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private val visibleWindow by lazy { AndroidVisibleWindowGate { Log.i("MetaSecretE2E", "E2E: $it") } }

    /** Focused F3 probe: actual OS window and screenshot, with no registration/Core mutation. */
    @Test
    fun verifyTest51NativeWindowReady() {
        assertInitialNativeWindow()
        check(isUnauthenticatedLanding()) { "Test51 F3 screenshot is restricted to unauthenticated landing" }
        visibleWindow.saveUnauthenticatedScreenshot()
        Log.i("MetaSecretE2E", "E2E: ANDROID_TEST51_F3_VISIBLE_WINDOW_AND_SCREENSHOT_READY")
    }

    /** Read-only inspection of an already registered fixture; no data reset or registration action. */
    @Test
    fun inspectTest51MainNativeWindow() {
        composeRule.waitUntil(30_000) { hasTag("tab-devices") && hasTag("tab-secrets") }
        val anchors = listOf("tab-secrets", "tab-devices", "tab-profile")
            .associateWith(::textFromTag)
        check(anchors.values.all { it.isNotBlank() }) { "Main inspection requires actual public navigation labels" }
        visibleWindow.recordMainAnchorMetadata(anchors)
        visibleWindow.saveMainInspectionScreenshot()
        Log.i("MetaSecretE2E", "E2E: ANDROID_TEST51_MAIN_INSPECTION_CAPTURED readinessNotCertified=true")
    }

    @Test
    fun handleRegistrationForTest51() {
        val raw = requireNotNull(InstrumentationRegistry.getArguments().getString("registrationCase")) {
            "Test51 requires explicit registrationCase"
        }
        val config = JSONObject(raw)
        val prefix = config.getString("markerPrefix")
        require(prefix.startsWith("TEST51_") && prefix.matches(Regex("[A-Za-z0-9_.-]+")))
        require(config.getString("platform") == "android")
        require(config.getString("provider") in setOf("manual", "google")) {
            "Android Apple is unsupported; provider cases require real preconfigured account authorization"
        }
        when (config.getString("actor")) {
            "create" -> {
                readyInitial(prefix)
                enterName(config.getString("vaultName"), available = true, provider = config.getString("provider"))
                marker(prefix, "AVAILABLE")
                composeRule.orbitCreateVault()
                assertMember(config.getString("vaultName"))
                marker(prefix, "CREATED")
                assertGeneratedQr(config.getString("vaultName"), prefix)
            }
            "requester" -> requester(config, prefix)
            "responder" -> responder(config, prefix)
            else -> error("Unsupported Test51 actor")
        }
    }

    private fun readyInitial(prefix: String) {
        composeRule.waitUntil(30_000) { hasTag("onboarding-skip") || hasTag("orbit-enter") }
        assertInitialNativeWindow()
        if (hasTag("onboarding-skip")) composeRule.orbitTap("onboarding-skip")
        composeRule.orbitWaitForTag("orbit-transition-ready")
        composeRule.onNodeWithTag("orbit-heading", useUnmergedTree = true).assertTextEquals("MetaSecret")
        composeRule.orbitWaitForTag("orbit-apple")
        composeRule.orbitWaitForTag("orbit-google")
        check(!hasTag("orbit-email-input") && !hasTag("orbit-create")) { "Expected provider screen01" }
        marker(prefix, "READY")
    }

    private fun enterName(name: String, available: Boolean, provider: String = "manual") {
        if (provider == "manual") composeRule.orbitEnterVaultName(name) else {
            // The real OS account consent is completed manually/preseeded. No fake callback or credentials.
            composeRule.orbitTap("orbit-$provider")
            composeRule.waitUntil(120_000) {
                hasTag("orbit-phase-idle") && hasTag("orbit-create") && runCatching {
                    composeRule.onNodeWithTag("orbit-heading", useUnmergedTree = true).assertTextEquals(name)
                }.isSuccess
            }
        }
        composeRule.orbitWaitForTag("orbit-transition-ready")
        composeRule.onNodeWithTag("orbit-heading", useUnmergedTree = true).assertTextEquals(name)
        val create = composeRule.onNodeWithTag("orbit-create", useUnmergedTree = true)
        create.assertIsDisplayed()
        if (available) create.assertIsEnabled() else create.assertIsNotEnabled()
        val join = composeRule.onNodeWithTag("orbit-join", useUnmergedTree = true)
        join.assertIsDisplayed()
        if (available) join.assertIsNotEnabled() else join.assertIsEnabled()
    }

    private fun requester(config: JSONObject, prefix: String) {
        readyInitial(prefix)
        enterName(config.getString("vaultName"), available = false, provider = config.getString("provider"))
        marker(prefix, "TAKEN")
        composeRule.orbitRequestJoin()
        assertOutsider(UserDataOutsiderStatus.PENDING)
        marker(prefix, "PENDING")
        when (config.getString("outcome")) {
            "accepted" -> {
                composeRule.orbitWaitForTag("tab-secrets", 180_000)
                assertMember(config.getString("vaultName"))
                marker(prefix, "MAIN")
            }
            "declined-retry", "back-retry" -> {
                if (config.getString("outcome") == "declined-retry") {
                    composeRule.orbitWaitForTag("orbit-phase-declined", 180_000)
                    assertOutsider(UserDataOutsiderStatus.DECLINED)
                    marker(prefix, "DECLINED")
                }
                val previousDevice = GlobalContext.get().get<AppStateCacheProviderInterface>()
                    .appState.value?.getCurrentDeviceId()
                check(!previousDevice.isNullOrBlank()) { "Back requires the canonical requesting device identity" }
                val keys = GlobalContext.get().get<KeyChainInterface>()
                check(runBlocking { keys.containsKey("master_key") }) {
                    "Incomplete request must have its stored key before Back"
                }
                composeRule.orbitTap("orbit-back")
                composeRule.waitUntil(30_000) {
                    hasTag("orbit-transition-ready") && hasTag("orbit-apple") && hasTag("orbit-google")
                }
                composeRule.onNodeWithTag("orbit-heading", useUnmergedTree = true).assertTextEquals("MetaSecret")
                check(runBlocking {
                    listOf("master_key", "pending_vault_email", "pending_email_provider").none { keys.containsKey(it) }
                }) { "Incomplete Back reached01 but retained local attempt credentials" }
                marker(prefix, "RESET01")
                val retryName = config.getString("retryVaultName")
                require(retryName != config.getString("vaultName")) { "Retry requires a different existing Vault" }
                enterName(retryName, available = false)
                composeRule.orbitRequestJoin()
                assertOutsider(UserDataOutsiderStatus.PENDING)
                marker(prefix, "RETRY_PENDING")
                composeRule.orbitWaitForTag("tab-secrets", 180_000)
                assertMember(retryName)
                marker(prefix, "MAIN")
            }
            else -> error("Unsupported Test51 requester outcome")
        }
    }

    private fun responder(config: JSONObject, prefix: String) {
        composeRule.orbitWaitForTag("tab-devices", 60_000)
        assertCurrentNativeWindow()
        composeRule.onNodeWithTag("tab-devices", useUnmergedTree = true).performClick()
        check(!hasTag("pending-device-row") && !hasTag("alert-join-request")) { "Responder has stale pending UI" }
        marker(prefix, "READY")
        waitForGate(config)
        composeRule.orbitWaitForTag("pending-device-row", 180_000)
        check(composeRule.onAllNodes(hasTestTag("pending-device-row"), useUnmergedTree = true).fetchSemanticsNodes().size == 1) {
            "Isolated Test51 must expose exactly one pending candidate"
        }
        val candidateName = composeRule.onNodeWithTag("pending-device-name", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        check(candidateName.isNotBlank()) { "Core candidate name was empty" }
        composeRule.onNodeWithTag("pending-device-row", useUnmergedTree = true).performClick()
        composeRule.orbitWaitForTag("alert-join-request")
        val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
        val candidateId = (GlobalContext.get().get<AlertCoordinatorInterface>().joinRequestAlert.value as? JoinRequestAlertState.Visible)?.deviceId
        check(!candidateId.isNullOrBlank()) { "Visible Core-backed dialog must identify the exact candidate" }
        val before = cache.appState.value
        val currentId = before?.getCurrentDeviceId()
        val canonicalPendingIds = before?.getVaultSummary()?.users?.filterValues { it.status == UserStatus.PENDING }?.keys
        check(before?.success == true && before.getCurrentVaultName() == config.getString("vaultName") &&
            !currentId.isNullOrBlank() && currentId != candidateId && canonicalPendingIds?.singleOrNull() == candidateId &&
            before.getUserDataByDeviceId(candidateId)?.device?.deviceName == candidateName &&
            (before.getVaultFullInfo() as? VaultFullInfo.Member)?.member?.member?.vault?.users?.get(candidateId)
                ?.outsider?.status == UserDataOutsiderStatus.PENDING) {
            "Pending UI/dialog must correlate the same canonical Core candidate in this Vault"
        }
        val accepted = config.getString("decision") == "accepted"
        composeRule.onNodeWithTag(if (accepted) "alert-join-request-accept" else "alert-join-request-decline",
            useUnmergedTree = true).performClick()
        composeRule.waitUntil(120_000) { !hasTag("alert-join-request") && !hasTag("pending-device-row") }
        if (accepted) {
            composeRule.waitUntil(120_000) {
                composeRule.onAllNodes(
                    androidx.compose.ui.test.SemanticsMatcher("canonical Device row") {
                        it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag].startsWith("device-row-")
                    }.and(hasAnyDescendant(hasText(candidateName, substring = false)))
                        .and(hasAnyDescendant(hasText("Member", substring = false))), useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 1
            }
        } else {
            // Core client_view intentionally excludes declined outsiders from the receiving device list.
            composeRule.waitUntil(120_000) {
                val state = cache.appState.value
                val currentVisible = runCatching {
                    composeRule.onNodeWithTag("device-row-$currentId", useUnmergedTree = true).assertIsDisplayed()
                }.isSuccess
                state?.success == true && state.getVaultFullInfo() is VaultFullInfo.Member &&
                    state.getCurrentDeviceId() == currentId && state.getCurrentVaultName() == config.getString("vaultName") &&
                    state.getUserDataByDeviceId(candidateId) == null && currentVisible && hasTag("tab-devices") &&
                    composeRule.onAllNodes(hasTestTag("device-row-$candidateId"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodes(hasTestTag("pending-device-row"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
        marker(prefix, "DECIDED")
    }

    private fun waitForGate(config: JSONObject) {
        val base = InstrumentationRegistry.getArguments().getString("approvalCoordinatorUrl") ?: "http://10.0.2.2:5180"
        val key = "registration-${config.getString("id")}-${config.optString("stage", "primary")}"
        // Condition polling is only for the existing harness action barrier, never Core/business state.
        composeRule.waitUntil(120_000) {
            val connection = URL("$base/approval?platform=$key&cycle=0").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.inputStream.bufferedReader().use { it.readText() } == "allowed"
            } finally { connection.disconnect() }
        }
    }

    private fun assertGeneratedQr(name: String, prefix: String) {
        composeRule.onNodeWithTag("tab-devices", useUnmergedTree = true).performClick()
        composeRule.orbitWaitForTag("add-device-fab")
        composeRule.onNodeWithTag("add-device-fab", useUnmergedTree = true).assertIsEnabled().performClick()
        composeRule.orbitWaitForTag("add-device-dialog")
        composeRule.onNodeWithTag("vault-qr-name", useUnmergedTree = true).performScrollTo().assertIsDisplayed().assertTextEquals(name)
        composeRule.onNodeWithTag("vault-name-qr", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val bitmap = composeRule.onNodeWithTag("add-device-dialog", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // QRKit already supplies ZXing 3.5.2 at runtime. Reflection avoids a new
        // production or test dependency solely to decode the actually rendered pixels.
        val sourceType = Class.forName("com.google.zxing.LuminanceSource")
        val source = Class.forName("com.google.zxing.RGBLuminanceSource")
            .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java)
            .newInstance(bitmap.width, bitmap.height, pixels)
        val binarizerType = Class.forName("com.google.zxing.Binarizer")
        val binarizer = Class.forName("com.google.zxing.common.HybridBinarizer")
            .getConstructor(sourceType).newInstance(source)
        val binaryType = Class.forName("com.google.zxing.BinaryBitmap")
        val binary = binaryType.getConstructor(binarizerType).newInstance(binarizer)
        val readerType = Class.forName("com.google.zxing.qrcode.QRCodeReader")
        val result = readerType.getMethod("decode", binaryType).invoke(readerType.getConstructor().newInstance(), binary)
        check(result.javaClass.getMethod("getText").invoke(result) == name) {
            "Real generated QR must decode to exactly the canonical Vault name"
        }
        marker(prefix, "QR_GENERATED")
    }

    private fun assertMember(name: String) {
        val state = GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value
        check(state?.success == true && state.getCurrentVaultName() == name && state.getVaultFullInfo() is VaultFullInfo.Member) {
            "Main UI contradicts canonical Core membership"
        }
    }

    private fun assertOutsider(status: UserDataOutsiderStatus) {
        check(GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value?.getOutsiderStatus() == status) {
            "Orbit state contradicts canonical Core outsider status"
        }
    }

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1

    private fun marker(prefix: String, action: String) {
        assertCurrentNativeWindow()
        Log.i("MetaSecretE2E", "E2E: ${prefix}_$action")
    }

    private fun assertInitialNativeWindow() {
        composeRule.waitUntil(30_000) { hasTag("onboarding-skip") || hasTag("orbit-heading") }
        val tag = if (hasTag("onboarding-skip")) "onboarding-skip" else "orbit-heading"
        visibleWindow.requireVisibleAnchor(textFromTag(tag), tag,
            waitUntil = { timeout, condition -> composeRule.waitUntil(timeout, condition) },
            unauthenticatedF3 = ::isUnauthenticatedLanding)
    }

    private fun assertCurrentNativeWindow() {
        val tag = listOf("vault-qr-name", "pending-device-name", "tab-devices", "tab-secrets", "orbit-heading")
            .firstOrNull { hasTag(it) && textFromTag(it).isNotBlank() }
            ?: error("Test51 completed action has no meaningful public native-window anchor")
        visibleWindow.requireVisibleAnchor(textFromTag(tag), tag,
            waitUntil = { timeout, condition -> composeRule.waitUntil(timeout, condition) })
    }

    private fun isUnauthenticatedLanding(): Boolean =
        !hasTag("tab-secrets") &&
            GlobalContext.get().get<AppStateCacheProviderInterface>().appState.value?.getVaultFullInfo() !is VaultFullInfo.Member &&
            (hasTag("onboarding-skip") || (hasTag("orbit-apple") && hasTag("orbit-google") &&
                hasTag("orbit-heading") && textFromTag("orbit-heading") == "MetaSecret"))

    private fun textFromTag(tag: String): String {
        val root = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val nodes = mutableListOf(root)
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index++]
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let { return it }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            check(nodes.size + node.children.size <= 64) { "Test51 public anchor subtree exceeded its bound" }
            nodes.addAll(node.children)
        }
        return ""
    }
}
