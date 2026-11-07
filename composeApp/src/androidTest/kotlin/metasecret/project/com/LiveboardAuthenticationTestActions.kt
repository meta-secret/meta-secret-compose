package metasecret.project.com

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Must be the OUTER RuleChain resource, before ActivityScenario launches MainActivity. */
internal class LiveboardRealAuthenticationRule(private val launchIntent: Intent) : ExternalResource() {
    private var previous: String? = null
    private var changed = false
    override fun before() {
        val arguments = InstrumentationRegistry.getArguments()
        val required = arguments.getString("requiredE2eServerUrl").orEmpty()
        val profile = if (arguments.containsKey("authProfile")) checkNotNull(arguments.getString("authProfile")) {
            "Supplied Test50 authentication profile is not a string"
        } else "real-os"
        check(profile in setOf("real-os", "controlled-business")) { "Invalid Test50 authentication profile" }
        if (profile == "controlled-business") {
            val fingerprint = Build.FINGERPRINT.lowercase()
            val product = Build.PRODUCT.lowercase()
            check(BuildConfig.DEBUG && required.isNotBlank() &&
                (listOf("emulator", "generic", "emu", "sdk_gphone").any { fingerprint.contains(it) } ||
                    listOf("emulator", "generic").any { product.contains(it) } || System.getProperty("ro.kernel.qemu") == "1")) {
                "Controlled business requires the admitted debug emulator and real Core route"
            }
            check(System.getProperty("metasecret.test.force_system_auth") == null &&
                !launchIntent.hasExtra("metasecret.force_system_auth") &&
                !arguments.containsKey("metasecret.force_system_auth") && !arguments.containsKey("devicePasscode")) {
                "Controlled business has a native force-auth or credential conflict"
            }
            return
        }
        if (required.isBlank()) return
        previous = System.getProperty("metasecret.test.force_system_auth")
        System.setProperty("metasecret.test.force_system_auth", "true")
        changed = true
    }
    override fun after() {
        if (!changed) return
        if (previous == null) System.clearProperty("metasecret.test.force_system_auth")
        else System.setProperty("metasecret.test.force_system_auth", checkNotNull(previous))
    }
}

internal fun lbAccessibilityNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
    val result = mutableListOf<AccessibilityNodeInfo>()
    fun collect(node: AccessibilityNodeInfo, depth: Int) {
        check(depth < 64 && result.size < 4096) { "Accessibility tree exceeds the bounded probe" }
        result.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, depth + 1) }
    }
    collect(root, 0)
    return result
}

private fun systemPrompt(): AccessibilityNodeInfo? {
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return null
    val own = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    if (root.packageName?.toString() == own) return null
    val nodes = lbAccessibilityNodes(root)
    val credential = nodes.any { it.isPassword || it.viewIdResourceName.orEmpty().let { id ->
        id.contains("biometric") || id.contains("credential") || id.contains("pinEntry")
    } }
    return root.takeIf { credential && nodes.any { it.isVisibleToUser } }
}

private fun clickSystemLabel(root: AccessibilityNodeInfo, labels: Set<String>): Boolean {
    val matches = lbAccessibilityNodes(root).filter {
        it.isVisibleToUser && it.isEnabled && (it.text?.toString() in labels || it.contentDescription?.toString() in labels)
    }
    if (matches.size != 1) return false
    var target: AccessibilityNodeInfo? = matches.single()
    repeat(5) {
        if (target?.isClickable == true) return target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        target = target?.parent
    }
    return false
}

internal fun ComposeTestRule.lbCancelSystemPrompt() {
    var root: AccessibilityNodeInfo? = null
    waitUntil(15_000) { root = systemPrompt(); root != null }
    checkNotNull(root) { "The actual OS prompt is required; app Cancel is not evidence" }
    if (!clickSystemLabel(root!!, setOf("Cancel", "Отмена", "Отменить"))) {
        // OS Back is sent only after proving the credential/biometric system window owns interaction.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    waitUntil(15_000) { systemPrompt() == null }
}

internal fun ComposeTestRule.lbApproveSystemIfPresent() {
    var observed = systemPrompt()
    if (observed == null && System.getProperty("metasecret.test.force_system_auth") == "true") {
        waitUntil(15_000) { observed = systemPrompt(); observed != null }
    }
    var root = observed ?: return
    clickSystemLabel(root, setOf("Use PIN", "Enter PIN", "Use password", "Использовать PIN-код", "Пароль"))
    root = systemPrompt() ?: return
    val passcode = requireNotNull(InstrumentationRegistry.getArguments().getString("devicePasscode")) {
        "Actual OS PIN controls require a declared devicePasscode; no default is guessed"
    }
    require(passcode.matches(Regex("[0-9]{4,16}"))) { "Declared devicePasscode must be4..16 digits (value omitted)" }
    val password = lbAccessibilityNodes(root).filter { it.isVisibleToUser && it.isPassword && it.isEditable }
    if (password.size == 1) {
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passcode) }
        check(password.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
    } else {
        for (digit in passcode) {
            check(clickSystemLabel(checkNotNull(systemPrompt()), setOf(digit.toString()))) { "Actual OS approval controls unavailable" }
        }
        systemPrompt()?.let { clickSystemLabel(it, setOf("OK", "Enter", "Done", "ОК", "Готово")) }
    }
    val deadline = SystemClock.elapsedRealtime() + 15_000
    while (systemPrompt() != null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
    check(systemPrompt() == null) { "Actual OS authentication did not complete" }
}

/** Startup authentication is completed only when its real OS window is actually observed. */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.lbCompleteEntryAuthenticationIfPresent() {
    if (System.getProperty("metasecret.test.force_system_auth") == "true" && systemPrompt() != null) {
        lbApproveSystemIfPresent()
    }
}
