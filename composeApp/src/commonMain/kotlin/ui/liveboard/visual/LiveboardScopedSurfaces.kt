package ui.liveboard.visual

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.design.generated.LiveboardGradientStop
import ui.design.generated.LiveboardMaterial
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardOffset
import ui.design.generated.LiveboardShadow
import ui.design.generated.LiveboardTokens as T

/** Two fixed native modal surfaces bind their existing scoped CSS evidence; this is not a CSS engine. */
fun liveboardSheetStyle(viewport: LiveboardViewport): LiveboardSurfaceStyle {
    val properties = LiveboardStyleBindings.properties(".secrets-sheet", viewport)
    val shadows = Regex("#[0-9a-fA-F]{3,8}\\b").findAll(properties["box-shadow"].orEmpty()).map { liveboardColor(it
        .value) }.toList()
    val border = LiveboardStyleBindings.color(".secrets-sheet", "border", viewport, T.liveboardColorVaultBorder)
    val background = LiveboardStyleBindings.color(".secrets-sheet", "background", viewport, T.liveboardColorBackground)
    return LiveboardSurfaceStyle(LiveboardMaterial(null, listOf(LiveboardGradientStop(0f, background),
        LiveboardGradientStop(1f, background)), listOf(
        LiveboardShadow(true, LiveboardOffset(0f, 1f), 0f, 0f, shadows.getOrElse(0) { Color.Transparent }),
        LiveboardShadow(false, LiveboardOffset(0f, -VAULT_HALO_LIFT), VAULT_HALO_BLUR, 0f, shadows.getOrElse(1) {
            Color.Transparent }),
    )), 27.dp, border, false, LiveboardSurfaceEdges(border, Color.Transparent, drawBottom = false, topOnly = true))
}

fun liveboardProfileDialogStyle(viewport: LiveboardViewport): LiveboardSurfaceStyle {
    val properties = LiveboardStyleBindings.properties(".profile-reset-dialog", viewport)
    val shadows = Regex("#[0-9a-fA-F]{3,8}\\b").findAll(properties["box-shadow"].orEmpty()).map { liveboardColor(it
        .value) }.toList()
    val background = LiveboardStyleBindings.color(".profile-reset-dialog", "background", viewport, T
        .liveboardColorBackground)
    val border = LiveboardStyleBindings.color(".profile-reset-dialog", "border", viewport, T.liveboardColorVaultBorder)
    return LiveboardSurfaceStyle(LiveboardMaterial(null, listOf(LiveboardGradientStop(0f, background),
        LiveboardGradientStop(1f, background)), listOf(
        LiveboardShadow(true, LiveboardOffset(0f, 1f), 0f, 0f, shadows.getOrElse(0) { Color.Transparent }),
        LiveboardShadow(false, LiveboardOffset(0f, INBOX_HALO_OFFSET), INBOX_HALO_BLUR, 0f, shadows.getOrElse(1) {
            Color.Transparent }),
    )), 22.dp, border, false, LiveboardSurfaceEdges(border, border))
}

fun liveboardPlusPressed(viewport: LiveboardViewport): LiveboardMaterial {
    val properties = LiveboardStyleBindings.properties(".add-device-trigger:active", viewport)
    val ink = Regex("#[0-9a-fA-F]{3,8}\\b").findAll(properties["box-shadow"].orEmpty()).map { liveboardColor(it
        .value) }.toList()
    return LiveboardMaterials.addDevice.copy(shadows = listOf(
        LiveboardShadow(true, LiveboardOffset(0f, 1f), 1f, 0f, ink.getOrElse(0) { Color.Transparent }),
        LiveboardShadow(false, LiveboardOffset(0f, 1f), 0f, 0f, ink.getOrElse(1) { Color.Transparent }),
        LiveboardShadow(false, LiveboardOffset(0f, CREATE_HALO_OFFSET), CREATE_HALO_BLUR, 0f, ink.getOrElse(2) {
            Color.Transparent }),
    ))
}

private const val VAULT_HALO_LIFT: Float = 12f
private const val VAULT_HALO_BLUR: Float = 45f
private const val INBOX_HALO_OFFSET: Float = 24f
private const val INBOX_HALO_BLUR: Float = 65f
private const val CREATE_HALO_OFFSET: Float = 5f
private const val CREATE_HALO_BLUR: Float = 8f
