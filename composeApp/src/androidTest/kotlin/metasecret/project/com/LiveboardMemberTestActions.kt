package metasecret.project.com

import androidx.compose.ui.test.junit4.ComposeTestRule

import androidx.compose.ui.test.*
import core.AppString
import core.AppStateCacheProviderInterface
import core.metaSecretCore.liveboard.LiveboardKind
import models.apiModels.liveboard.LiveboardStateDecoder
import models.apiModels.liveboard.LiveboardSnapshot
import org.koin.core.context.GlobalContext

internal data class LiveboardTestMember(val deviceId: String, val name: String, val current: Boolean)

internal fun ComposeTestRule.lbMembers(): List<LiveboardTestMember> {
    val placeholders = setOf("lb-device-0", "lb-device-1", "lb-device-2")
    return lbTagged("lb-device-").filter { lbNodeTag(it) !in placeholders }.map { node ->
        val texts = lbNodeTexts(node)
        check(texts.size >= 3) { "Connected Device slot is missing visible identity/status" }
        LiveboardTestMember(lbNodeTag(node).removePrefix("lb-device-"), texts[texts.size - 2], texts.first() == lbLabel(AppString.lbThisDevice))
    }.also { list ->
        check(list.map { it.deviceId }.distinct().size == list.size)
        if (list.isNotEmpty()) check(list.count { it.current } == 1)
    }
}

internal fun ComposeTestRule.lbMember(name: String, current: Boolean? = null): LiveboardTestMember {
    lbHome()
    var found: LiveboardTestMember? = null
    waitUntil(180_000) { found = lbMembers().singleOrNull { it.name == name && (current == null || it.current == current) }; found != null }
    return checkNotNull(found)
}

internal fun ComposeTestRule.lbInspectRemove(member: LiveboardTestMember, awaitEnabledRemove: Boolean = false): Boolean {
    lbTap("lb-device-${member.deviceId}")
    if (member.current) {
        check(lbAbsent("lb-remove-${member.deviceId}")) { "Current Device exposes forbidden self-removal" }
        return false
    }
    val tag = "lb-remove-${member.deviceId}"
    val control = onNodeWithTag(tag, useUnmergedTree = true)
    if (awaitEnabledRemove) {
        // Membership can settle before the inbox finishes refreshing.
        waitUntil(180_000) { lbExactlyOne(tag) && runCatching { control.assertIsEnabled() }.isSuccess }
    } else {
        lbWait(tag)
    }
    val enabled = runCatching { control.assertIsEnabled() }.isSuccess
    if (lbHas("lb-current")) lbTap("lb-current")
    return enabled
}

internal fun ComposeTestRule.lbAssertMembers(expected: List<Pair<String, Boolean>>): Map<String, String> {
    lbHome()
    waitUntil(180_000) {
        val actual = lbMembers()
        actual.size == expected.size && expected.all { (name, current) -> actual.count { it.name == name && it.current == current } == 1 }
    }
    return expected.associate { (name, current) ->
        val member = lbMembers().single { it.name == name && it.current == current }
        if (current) check(!lbInspectRemove(member))
        else check(lbInspectRemove(member, awaitEnabledRemove = true)) { "Current ready noncurrent Device has no enabled Remove" }
        name to member.deviceId
    }
}

internal fun ComposeTestRule.lbPreviewRemoval(deviceId: String) {
    lbHome()
    check(lbMembers().single { it.deviceId == deviceId }.current.not())
    lbTap("lb-device-$deviceId")
    lbWait("lb-remove-$deviceId")
    lbScope().previewDevice = deviceId
}

/** A cached canonical projection is observation only: never refreshes or invokes Core. */
internal fun lbSnapshot(): LiveboardSnapshot? {
    val cache = GlobalContext.get().get<AppStateCacheProviderInterface>()
    val frame = cache.canonicalFrames.value ?: return null
    if (frame.outcome != core.metaSecretCore.liveboard.NativeReadOutcome.SUCCESS || frame.scope == null ||
        frame.state !== cache.appState.value) return null
    return frame.state?.let(LiveboardStateDecoder::decode)
}

internal fun ComposeTestRule.lbSubmitRemoval() {
    val id = checkNotNull(lbScope().previewDevice)
    val before = checkNotNull(lbSnapshot())
    val oldRequests = before.operations.map { it.identity.requestId }.toSet()
    lbTap("lb-remove-$id")
    lbApproveSystemIfPresent()
    waitUntil(180_000) {
        val current = lbSnapshot() ?: return@waitUntil false
        current.devices.none { it.deviceId == id } || current.operations.any {
            it.identity.kind == LiveboardKind.RemoveDevice && it.identity.targetDeviceId == id &&
                it.identity.requesterId == before.identity.currentDeviceId && it.identity.requestId !in oldRequests
        }
    }
}

internal fun ComposeTestRule.lbResetProfile() {
    lbHome(); lbTap("lb-open-profile"); lbWait("lb-profile")
    lbTap("lb-profile-reset"); lbWait("lb-reset-confirmation"); lbTap("lb-reset-submit")
    lbApproveSystemIfPresent()
    lbWait("orbit-enter")
    check(lbAbsent("lb-home") && lbAbsent("lb-secrets") && lbTagged("lb-device-").isEmpty())
}
