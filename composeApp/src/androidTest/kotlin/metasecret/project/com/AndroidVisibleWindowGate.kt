package metasecret.project.com

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Native foreground proof reused from Test50's proven accessibility readiness pattern. */
internal class AndroidVisibleWindowGate(private val diagnostic: (String) -> Unit) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetPackage = instrumentation.targetContext.packageName
    private val automation: UiAutomation = instrumentation.uiAutomation.also { service ->
        val info = service.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        service.serviceInfo = info
    }

    fun requireVisibleAnchor(
        anchorText: String,
        anchorTag: String,
        waitUntil: (Long, () -> Boolean) -> Unit,
        unauthenticatedF3: (() -> Boolean)? = null,
    ) {
        check(targetPackage == "metasecret.project.com") { "Test51 foreground gate requires the exact application package" }
        check(anchorText.isNotBlank()) { "Test51 foreground gate requires a meaningful UI anchor" }
        var compatibilityDismissed = false
        var lastOwner = "missing"
        var lastOwnWindow = false
        var lastAnchorCount = 0
        var lastRootBounds = Rect()
        var lastScreenBounds = Rect()
        try {
            val ready: () -> Boolean = ready@{
                val root = automation.rootInActiveWindow
                val nodes = nodes(root)
                val windows = automation.windows
                lastOwner = owner(root?.packageName?.toString())
                val focusedSystem = windows.any { it.id == root?.windowId &&
                    it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isActive && it.isFocused }
                if (unauthenticatedF3 != null && !compatibilityDismissed && lastOwner == "android" && focusedSystem) {
                    val body = nodes.any { it.isVisibleToUser && it.text?.toString()
                        ?.startsWith("This app isn’t 16 KB compatible. ELF alignment check failed.") == true }
                    val title = matches(nodes, "MetaSecret").any { it.isVisibleToUser }
                    if (body && title) {
                        check(unauthenticatedF3()) { "Known compatibility handling is restricted to unauthenticated Test51 F3" }
                        val ok = matches(nodes, "OK").filter { it.isVisibleToUser && it.isEnabled &&
                            (it.isClickable || it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_CLICK }) }
                        check(ok.size == 1) { "Known compatibility warning must expose one actionable OK" }
                        check(ok.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Known compatibility OK action failed" }
                        compatibilityDismissed = true
                        diagnostic("ANDROID_TEST51_COMPATIBILITY_DISMISSED unauthenticatedF3=true")
                        return@ready false
                    }
                }
                val rootBounds = Rect().also { root?.getBoundsInScreen(it) }
                lastRootBounds = rootBounds
                lastOwnWindow = root != null && root.packageName?.toString() == targetPackage && windows.any {
                    it.id == root.windowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused
                }
                val screen = physicalScreenBounds()
                lastScreenBounds = screen
                val visibleAnchors = matches(nodes, anchorText).filter { node ->
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    node.packageName?.toString() == targetPackage && node.isVisibleToUser &&
                        !bounds.isEmpty && rootBounds.contains(bounds) && screen.contains(bounds)
                }
                lastAnchorCount = visibleAnchors.size
                lastOwnWindow && visibleAnchors.isNotEmpty()
            }
            waitUntil(15_000, ready)
            if (unauthenticatedF3 != null) {
                // OS focus can return before the dismissed warning's final composited frame.
                // Require native accessibility-event quiescence, then re-read the actual window.
                automation.waitForIdle(500, 5_000)
                check(ready()) { "Test51 F3 foreground changed during native event quiescence" }
                diagnostic("ANDROID_TEST51_F3_UI_QUIESCENT ownWindowAndAnchorRevalidated=true")
            }
        } catch (failure: Throwable) {
            diagnostic("ANDROID_TEST51_VISIBLE_WINDOW_FAILED owner=$lastOwner ownActiveFocusedApplication=$lastOwnWindow " +
                "anchorTag=$anchorTag visibleAnchorCount=$lastAnchorCount rootBounds=$lastRootBounds physicalScreen=$lastScreenBounds")
            if (anchorTag in mainAnchorTags) {
                recordMainAnchorMetadata(mapOf(anchorTag to anchorText))
                saveMainInspectionScreenshot()
            }
            throw failure
        }
        diagnostic("ANDROID_TEST51_VISIBLE_WINDOW_READY owner=own ownActiveFocusedApplication=true " +
            "anchorTag=$anchorTag visibleAnchorCount=$lastAnchorCount rootBounds=$lastRootBounds physicalScreen=$lastScreenBounds compatibilityDismissed=$compatibilityDismissed")
    }

    /** Only static Main navigation labels/tags are inspected; never dump the whole UI tree. */
    fun recordMainAnchorMetadata(anchors: Map<String, String>) {
        check(anchors.isNotEmpty() && anchors.keys.all { it in mainAnchorTags })
        val root = automation.rootInActiveWindow
        val all = nodes(root)
        val rootBounds = Rect().also { root?.getBoundsInScreen(it) }
        val display = instrumentation.targetContext.resources.displayMetrics
        val contextScreen = Rect(0, 0, display.widthPixels, display.heightPixels)
        val screen = physicalScreenBounds()
        val ownWindow = root != null && root.packageName?.toString() == targetPackage && automation.windows.any {
            it.id == root.windowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive && it.isFocused
        }
        diagnostic("ANDROID_TEST51_MAIN_INSPECT owner=${owner(root?.packageName?.toString())} " +
            "ownActiveFocusedApplication=$ownWindow rootBounds=$rootBounds metricsScreen=$contextScreen physicalScreen=$screen nodes=${all.size}")
        for ((tag, text) in anchors) {
            val textMatches = matches(all, text)
            val candidates = all.filter { node ->
                node.viewIdResourceName == tag || node.viewIdResourceName?.endsWith(":id/$tag") == true ||
                    node.text?.toString()?.contains(text) == true || node.contentDescription?.toString()?.contains(text) == true
            }
            diagnostic("ANDROID_TEST51_MAIN_ANCHOR tag=$tag exactTextMatches=${textMatches.size} candidates=${candidates.size}")
            check(candidates.size <= 16) { "Test51 public Main candidate metadata exceeded its bound" }
            candidates.forEachIndexed { index, node ->
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val nativeText = node.text?.toString()
                val description = node.contentDescription?.toString()
                // Repeated static labels are safe; any unrelated text is deliberately redacted.
                fun publicValue(value: String?) = when {
                    value == null -> "<none>"
                    value.replace(text, "").all { it.isWhitespace() || it in ",.;:()" } -> value
                    else -> "<other-redacted>"
                }
                diagnostic("ANDROID_TEST51_MAIN_CANDIDATE tag=$tag index=$index " +
                    "text=${publicValue(nativeText)} description=${publicValue(description)} " +
                    "textEqual=${nativeText == text} descriptionEqual=${description == text} " +
                    "resourceTagEqual=${node.viewIdResourceName == tag} ownPackage=${node.packageName?.toString() == targetPackage} " +
                    "visible=${node.isVisibleToUser} bounds=$bounds rootContains=${rootBounds.contains(bounds)} screenContains=${screen.contains(bounds)}")
            }
        }
    }

    fun saveUnauthenticatedScreenshot(): java.io.File = saveScreenshot("test51-f3-window.png")

    fun saveMainInspectionScreenshot(): java.io.File = saveScreenshot("test51-main-window-inspection.png")

    private fun saveScreenshot(name: String): java.io.File {
        val image = checkNotNull(automation.takeScreenshot()) { "Test51 F3 real display capture unavailable" }
        val file = java.io.File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        try {
            diagnostic("ANDROID_TEST51_DISPLAY_CAPTURE width=${image.width} height=${image.height}")
            java.io.FileOutputStream(file).use { check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
        return file
    }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf(root)
        var index = 0
        while (index < result.size) {
            val node = result[index++]
            for (child in 0 until node.childCount) node.getChild(child)?.let {
                check(result.size < 512) { "Test51 native foreground traversal exceeded its bound" }
                result.add(it)
            }
        }
        return result
    }

    private fun matches(nodes: List<AccessibilityNodeInfo>, value: String) = nodes.filter {
        it.text?.toString() == value || it.contentDescription?.toString() == value || it.viewIdResourceName == value
    }

    @Suppress("DEPRECATION")
    private fun physicalScreenBounds(): Rect {
        // Native bounds and UiAutomation screenshots include the system bars.
        // Real metrics use the current display rotation and are available on API25.
        val metrics = DisplayMetrics()
        instrumentation.targetContext.getSystemService(WindowManager::class.java)
            .defaultDisplay.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun owner(packageName: String?) = when (packageName) {
        targetPackage -> "own"
        "android" -> "android"
        "com.android.systemui" -> "systemui"
        "com.android.launcher3", "com.google.android.apps.nexuslauncher" -> "launcher"
        "com.android.permissioncontroller", "com.google.android.permissioncontroller" -> "permissioncontroller"
        null -> "missing"
        else -> "other"
    }

    private val mainAnchorTags = setOf("tab-secrets", "tab-devices", "tab-profile")
}
